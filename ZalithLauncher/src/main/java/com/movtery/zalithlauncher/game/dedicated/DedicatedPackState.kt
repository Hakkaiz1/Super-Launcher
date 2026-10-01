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

import com.movtery.zalithlauncher.game.download.modpack.technic.InsufficientSpaceException
import com.movtery.zalithlauncher.game.download.modpack.technic.TechnicApi
import com.movtery.zalithlauncher.ui.AndroidStringText
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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
}
