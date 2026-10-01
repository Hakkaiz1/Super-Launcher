/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.game.dedicated

import android.content.Context
import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.coroutine.TaskLogOutput
import com.movtery.zalithlauncher.coroutine.TitledTask
import com.movtery.zalithlauncher.game.download.modpack.technic.InsufficientSpaceException
import com.movtery.zalithlauncher.game.download.modpack.technic.TechnicApi
import com.movtery.zalithlauncher.game.download.modpack.technic.TechnicPackInstaller
import com.movtery.zalithlauncher.game.path.GamePathManager
import com.movtery.zalithlauncher.game.path.getVersionsHome
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.ui.AndroidStringText
import com.movtery.zalithlauncher.ui.androidText
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.net.ConnectException
import java.net.UnknownHostException

/** Estados da tela dedicada (spec §3/§5.1). */
sealed interface PackState {
    data object Checking : PackState
    data object Offline : PackState
    data object NotInstalled : PackState
    data class NeedsUpdate(val installedVersion: String, val availableVersion: String) : PackState
    data object Ready : PackState
    data object Installing : PackState
    data class Failed(val message: AndroidStringText) : PackState
}

/** Eventos puros da máquina de estados (spec §9 teste 5). */
sealed interface DedicatedEvent {
    data object CheckStarted : DedicatedEvent
    data class CheckCompleted(
        val installed: PackManifest?,
        val versionJsonPresent: Boolean,
        val apiIdentity: String?
    ) : DedicatedEvent
    data object CheckOffline : DedicatedEvent
    data class CheckFailed(val message: AndroidStringText) : DedicatedEvent
    data object InstallRequested : DedicatedEvent
    data object InstallSucceeded : DedicatedEvent
    data class InstallFailed(val message: AndroidStringText) : DedicatedEvent
}

/** Tipo de falha puro; o mapeamento para string fica na wiring (spec §5.5). */
enum class ErrorKind { OFFLINE, API_BUILD_REJECTED, INSUFFICIENT_SPACE, GENERIC }

object DedicatedPackState {
    private const val TAG = "DedicatedPackState"

    private val _state = MutableStateFlow<PackState>(PackState.Checking)
    val state: StateFlow<PackState> = _state.asStateFlow()

    internal fun dispatch(event: DedicatedEvent) {
        _state.update { current -> reduce(current, event) }
    }

    /** Transições puras (spec §9 teste 5): CHECKING→NOT_INSTALLED→INSTALLING→READY e →FAILED→INSTALLING. */
    internal fun reduce(current: PackState, event: DedicatedEvent): PackState = when (event) {
        DedicatedEvent.CheckStarted -> PackState.Checking
        is DedicatedEvent.CheckCompleted ->
            classifyCheck(event.installed, event.versionJsonPresent, event.apiIdentity)
        DedicatedEvent.CheckOffline -> PackState.Offline
        is DedicatedEvent.CheckFailed -> PackState.Failed(event.message)
        // dup-tap: não reinicia uma instalação em andamento
        DedicatedEvent.InstallRequested -> if (current is PackState.Installing) current else PackState.Installing
        DedicatedEvent.InstallSucceeded -> PackState.Ready
        is DedicatedEvent.InstallFailed -> PackState.Failed(event.message)
    }

    /**
     * §5.1: sem manifesto (ou sem o json da versão — base apagada) = instalar;
     * identidade nula = READY com aviso (nunca loop de update); igual = READY;
     * diferente = atualizar.
     */
    internal fun classifyCheck(
        manifest: PackManifest?,
        versionJsonPresent: Boolean,
        apiIdentity: String?
    ): PackState {
        if (manifest == null || !versionJsonPresent) return PackState.NotInstalled
        if (apiIdentity == null) {
            Logger.warning(TAG, "Pack identity empty on both sides; treating as READY")
            return PackState.Ready
        }
        return if (manifest.packVersion == apiIdentity) PackState.Ready
        else PackState.NeedsUpdate(manifest.packVersion, apiIdentity)
    }

    /** Separa queda de rede, rejeição de build (401) e erro comum (spec §5.5). */
    internal fun classifyError(throwable: Throwable): ErrorKind {
        var current: Throwable? = throwable
        while (current != null) {
            when {
                current is TechnicApi.BuildRejectedException -> return ErrorKind.API_BUILD_REJECTED
                current is InsufficientSpaceException -> return ErrorKind.INSUFFICIENT_SPACE
                current is UnknownHostException || current is ConnectException -> return ErrorKind.OFFLINE
            }
            current = current.cause
        }
        return ErrorKind.GENERIC
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var installer: TechnicPackInstaller? = null
    private var appContext: Context? = null
    private var collectorJobs: List<Job> = emptyList()
    private var checkJob: Job? = null

    private val _tasks = MutableStateFlow<List<TitledTask>>(emptyList())
    val tasks: StateFlow<List<TitledTask>> = _tasks.asStateFlow()

    private val _logOutput = MutableStateFlow<TaskLogOutput?>(null)
    val logOutput: StateFlow<TaskLogOutput?> = _logOutput.asStateFlow()

    /**
     * Ordem obrigatória (spec §5.1): refreshPaths → reloadPath/waitForRefresh →
     * VersionsManager.waitForRefresh — só depois disso ler arquivo de jogo.
     * Chamado pela MainActivity na abertura e pelo botão "Tentar de novo".
     */
    fun launchCheck(context: Context) {
        // Toques rápidos não iniciam checks concorrentes (o estado Checking também é o
        // inicial — só o job indica "check em andamento")
        if (checkJob?.isActive == true) return
        appContext = context.applicationContext
        dispatch(DedicatedEvent.CheckStarted)
        checkJob = scope.launch { check(context.applicationContext) }
    }

    internal suspend fun check(context: Context) {
        try {
            PathManager.refreshPaths(context)
            GamePathManager.reloadPath()
            GamePathManager.waitForRefresh()
            VersionsManager.waitForRefresh()

            val slug = BuildKeys.DEDICATED_PACK_SLUG
            val versionDir = File(getVersionsHome(), slug)
            val manifest = PackManifest.read(versionDir)
            val versionJsonPresent = VersionsManager.isVersionExists(slug, true)

            val pack = try {
                TechnicApi.getPack(slug)
            } catch (t: Throwable) {
                dispatch(checkEvent(t))
                return
            }

            val identity = PackManifest.identityOf(pack.version, pack.url)
            dispatch(DedicatedEvent.CheckCompleted(manifest, versionJsonPresent, identity))
        } catch (t: Throwable) {
            dispatch(checkEvent(t))
        }
    }

    private fun checkEvent(t: Throwable): DedicatedEvent = when (classifyError(t)) {
        ErrorKind.OFFLINE -> DedicatedEvent.CheckOffline
        ErrorKind.API_BUILD_REJECTED -> DedicatedEvent.CheckFailed(androidText(R.string.dedicated_error_api_build))
        ErrorKind.INSUFFICIENT_SPACE -> DedicatedEvent.CheckFailed(androidText(R.string.dedicated_error_no_space))
        ErrorKind.GENERIC -> DedicatedEvent.CheckFailed(androidText(R.string.dedicated_error_generic))
    }

    /** Inicia instalação/atualização (etapas 1-8 da spec §5.2). */
    fun install(context: Context) {
        if (_state.value is PackState.Installing) return
        appContext = context.applicationContext
        dispatch(DedicatedEvent.InstallRequested)

        val inst = TechnicPackInstaller(scope)
        installer = inst
        collectorJobs.forEach { it.cancel() }
        collectorJobs = listOf(
            scope.launch { inst.tasksFlow.collect { _tasks.value = it } },
            scope.launch { inst.logOutput.collect { _logOutput.value = it } }
        )
        inst.install(
            context = context.applicationContext,
            onInstalled = {
                _tasks.value = emptyList()
                _logOutput.value = null
                dispatch(DedicatedEvent.InstallSucceeded)
            },
            onCancel = { appContext?.let { c -> launchCheck(c) } },
            onError = { e ->
                _tasks.value = emptyList()
                _logOutput.value = null
                if (classifyError(e) == ErrorKind.OFFLINE) dispatch(DedicatedEvent.CheckOffline)
                else dispatch(DedicatedEvent.InstallFailed(errorMessage(e)))
            }
        )
    }

    private fun errorMessage(t: Throwable): AndroidStringText = when (classifyError(t)) {
        ErrorKind.API_BUILD_REJECTED -> androidText(R.string.dedicated_error_api_build)
        ErrorKind.INSUFFICIENT_SPACE -> androidText(R.string.dedicated_error_no_space)
        else -> androidText(R.string.dedicated_error_generic)
    }

    fun cancelInstall() {
        installer?.cancelInstall()
    }
}
