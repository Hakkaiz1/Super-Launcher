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

package com.movtery.zalithlauncher.game.download.modpack.technic

import android.content.Context
import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.coroutine.TaskFlowExecutor
import com.movtery.zalithlauncher.coroutine.TaskLogOutput
import com.movtery.zalithlauncher.coroutine.addTask
import com.movtery.zalithlauncher.coroutine.buildPhase
import com.movtery.zalithlauncher.game.addons.modloader.forgelike.forge.ForgeVersions
import com.movtery.zalithlauncher.game.control.ControlManager
import com.movtery.zalithlauncher.game.download.engine.DownloadEngine
import com.movtery.zalithlauncher.game.download.engine.DownloadRequest
import com.movtery.zalithlauncher.game.download.game.GameDownloadInfo
import com.movtery.zalithlauncher.game.download.game.GameInstaller
import com.movtery.zalithlauncher.game.dedicated.DedicatedSeeder
import com.movtery.zalithlauncher.game.dedicated.PackManifest
import com.movtery.zalithlauncher.game.path.getVersionsHome
import com.movtery.zalithlauncher.game.version.installed.VersionsManager
import com.movtery.zalithlauncher.path.DOWNLOAD_OKHTTP_CLIENT
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.path.createRequestBuilder
import com.movtery.zalithlauncher.ui.androidText
import com.movtery.zalithlauncher.utils.file.extractFromZip
import com.movtery.zalithlauncher.utils.network.withSpeedReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.zip.ZipFile

private const val TAG = "TechnicPackInstaller"

/** Zip em `cache/temp_dedicated_pack/pack.zip` (spec §5.2 etapa 2). */
private fun packZipFile(): File = File(PathManager.DIR_CACHE_DEDICATED_PACK, "pack.zip")

/** Staging extraído (spec §5.2 etapa 3). */
private fun stagingDirectory(): File = File(PathManager.DIR_CACHE_DEDICATED_PACK, "staging")

/**
 * Request do pack: `DownloadEngine` direto — sem o timeout global de 5 min do
 * `downloadFileFromSources` (o zip tem 90 MB), `sha1=null` porque a Platform não
 * publica hash, e User-Agent de navegador para o Dropbox (spec §5.5).
 */
internal fun buildPackRequest(url: String, zipFile: File): DownloadRequest =
    DownloadRequest(
        urls = listOf(url),
        targetFile = zipFile,
        sha1 = null,
        userAgent = TechnicApi.BROWSER_USER_AGENT
    )

/** -1f = total desconhecido ⇒ progresso indeterminado; nunca NaN, nunca acima de 1. */
internal fun progressOf(downloaded: Long, total: Long): Float =
    if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else -1f

/** Tamanho total via HEAD — o engine só reporta bytes incrementais. */
internal fun headContentLength(url: String): Long = runCatching {
    DOWNLOAD_OKHTTP_CLIENT.newCall(createRequestBuilder(url).header("User-Agent", TechnicApi.BROWSER_USER_AGENT).head().build()).execute().use { resp ->
        if (resp.isSuccessful) resp.body.contentLength() else -1L
    }
}.getOrDefault(-1L)

class InsufficientSpaceException(message: String) : RuntimeException(message)
class PackForgeNotFoundException(message: String) : RuntimeException(message)

/** Espaço livre antes de extrair (spec §10): zip + margem para o conteúdo extraído. */
internal fun ensureFreeSpace(availableBytes: Long, requiredBytes: Long) {
    if (availableBytes < requiredBytes) {
        throw InsufficientSpaceException(
            "Need ${requiredBytes / (1024 * 1024)} MB free ($requiredBytes bytes), have ${availableBytes / (1024 * 1024)} MB"
        )
    }
}

/** Etapa 3 (spec §5.2): extrai o zip inteiro para o staging, validando espaço antes. */
internal suspend fun extractPack(zipFile: File, stagingDir: File) {
    stagingDir.deleteRecursively()
    stagingDir.mkdirs()
    ensureFreeSpace(stagingDir.usableSpace, zipFile.length() * 2)
    ZipFile(zipFile).use { it.extractFromZip("", stagingDir) }
}

/**
 * Etapa 6 (spec §5.2) + regras de update (§5.3): copia o staging para
 * `versions/<slug>/` sem `bin/`; no update apaga os órfãos do manifesto antigo e
 * **nunca** sobrescreve `saves/`, `options.txt` nem `servers.dat`; na primeira
 * instalação os protegidos do pack são copiados normalmente.
 */
internal suspend fun overlayPack(
    stagingDir: File,
    versionDir: File,
    oldManifest: PackManifest?,
    onProgress: (Float) -> Unit
) {
    val copyable = stagingDir.walkTopDown()
        .filter { it.isFile }
        .map { it.relativeTo(stagingDir).invariantSeparatorsPath }
        .filter { path ->
            !path.startsWith("bin/") &&
                (oldManifest == null || !PackManifest.isProtected(path))
        }
        .sorted()
        .toList()

    if (oldManifest != null) {
        PackManifest.orphans(oldManifest, PackManifest.packFiles(stagingDir)).forEach { rel ->
            if (!PackManifest.isProtected(rel)) {
                File(versionDir, rel).takeIf { it.exists() }?.delete()
            }
        }
    }

    versionDir.mkdirs()
    copyable.forEachIndexed { index, rel ->
        val source = File(stagingDir, rel)
        val target = File(versionDir, rel)
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)
        onProgress((index + 1).toFloat() / copyable.size)
    }
    if (copyable.isEmpty()) onProgress(1f)
}

/**
 * Instala/atualiza o pack dedicado (8 etapas da spec §5.2).
 * O `Context` entra por `install()` — a construção precisa ficar testável em JVM.
 */
class TechnicPackInstaller(private val scope: CoroutineScope) {
    private val taskExecutor = TaskFlowExecutor(scope)
    val tasksFlow = taskExecutor.tasksFlow

    private val _logOutput = MutableStateFlow<TaskLogOutput?>(null)
    val logOutput: StateFlow<TaskLogOutput?> = _logOutput.asStateFlow()

    private var pack: TechnicPack? = null
    private var apiIdentity: String = ""

    /** Etapas 1-2: limpa o temp, resolve o pack na API e baixa o zip com % e velocidade. */
    internal fun downloadPhase(): TaskFlowExecutor.TaskPhase = buildPhase {
        addTask(id = "Dedicated.ClearTemp", title = androidText(R.string.download_install_clear_temp)) {
            PathManager.DIR_CACHE_DEDICATED_PACK.deleteRecursively()
            PathManager.DIR_CACHE_DEDICATED_PACK.mkdirs()
        }
        addTask(id = "Dedicated.ResolvePack", title = androidText(R.string.dedicated_task_resolve_pack)) { task ->
            val fetched = TechnicApi.getPack(BuildKeys.DEDICATED_PACK_SLUG)
            pack = fetched
            apiIdentity = PackManifest.identityOf(fetched.version, fetched.url) ?: ""
            task.updateProgress(1f)
        }
        addTask(id = "Dedicated.Download", title = androidText(R.string.dedicated_task_download)) { task ->
            val packUrl = requireNotNull(requireNotNull(pack).url) { "Pack has no download url" }
            val zipFile = packZipFile()
            val totalBytes = headContentLength(packUrl)
            var downloaded = 0L
            task.updateProgress(progressOf(0L, totalBytes))
            withSpeedReport(
                onSpeedReport = { task.updateSpeed(it) },
                onClear = { task.clearSpeed() }
            ) { report ->
                DownloadEngine.download(buildPackRequest(packUrl, zipFile)) { delta ->
                    downloaded += delta
                    task.updateProgress(progressOf(downloaded, totalBytes))
                    report(delta)
                }
            }
            task.updateProgress(1f)
        }
    }

    private var versionInfo: PackVersionInfo? = null
    private var gameInstaller: GameInstaller? = null
    private var appContext: Context? = null

    /** Etapas 3-5: extrai, resolve a versão do pack e instala a base via caminho legado do Forge. */
    internal fun extractBasePhase(): TaskFlowExecutor.TaskPhase = buildPhase {
        addTask(id = "Dedicated.Extract", title = androidText(R.string.dedicated_task_extract)) { task ->
            extractPack(packZipFile(), stagingDirectory())
            task.updateProgress(1f)
        }
        addTask(id = "Dedicated.ResolveVersion", title = androidText(R.string.dedicated_task_resolve)) { task ->
            val pack = requireNotNull(pack)
            versionInfo = TechnicPackVersionResolver.resolve(stagingDirectory(), pack.minecraft)
            task.updateProgress(1f)
        }
        addTask(id = "Dedicated.InstallBase", title = androidText(R.string.dedicated_task_base)) { task ->
            val info = requireNotNull(versionInfo)
            val forgeVersion = ForgeVersions.fetchForgeList(info.minecraft)
                ?.firstOrNull { it.forgeBuildVersion.toString() == info.forgeBuild }
                ?: throw PackForgeNotFoundException(
                    "Forge ${info.forgeBuild} not found for Minecraft ${info.minecraft}"
                )
            val slug = BuildKeys.DEDICATED_PACK_SLUG
            val gameInfo = GameDownloadInfo(
                gameVersion = info.minecraft,
                customVersionName = slug,
                // sobrescreve quando o json já existe: evita GameAlreadyInstalledException no update
                overwrite = VersionsManager.isVersionExists(slug, true),
                forge = forgeVersion
            )
            val installer = GameInstaller(
                context = requireNotNull(appContext) { "install() was not called yet" },
                info = gameInfo,
                scope = scope,
                logOutputHolder = _logOutput
            )
            gameInstaller = installer
            // Fases do GameInstaller + as nossas 6-8, anexadas em tempo de execução
            // (mesmo padrão do ModPackInstaller.kt:241-280).
            val oldManifest = PackManifest.read(versionDir())
            taskExecutor.addPhases(
                installer.getTaskPhase(createIsolation = true) + listOf(
                    overlayPhase(stagingDirectory(), versionDir(), oldManifest),
                    seedPhase(
                        context = requireNotNull(appContext) { "install() was not called yet" },
                        stagingDir = stagingDirectory(),
                        versionDir = versionDir(),
                        // primeira instalação aplica os defaults; update preserva o version.config
                        applyDefaults = oldManifest == null
                    ),
                    selectPhase()
                )
            )
            task.updateProgress(1f)
        }
    }

    /** Etapa 6 como fase do fluxo. */
    internal fun overlayPhase(
        stagingDir: File,
        versionDir: File,
        oldManifest: PackManifest?
    ): TaskFlowExecutor.TaskPhase = buildPhase {
        addTask(id = "Dedicated.Overlay", title = androidText(R.string.dedicated_task_overlay)) { task ->
            overlayPack(stagingDir, versionDir, oldManifest) { pct -> task.updateProgress(pct) }
        }
    }

    /** `versions/<slug>` — alvo do overlay e do seed (isolation ativa). */
    private fun versionDir(): File = File(getVersionsHome(), BuildKeys.DEDICATED_PACK_SLUG)

    /** Etapas 7 (§5.2 + §7): layout, version.config, servidor — e o manifesto POR ÚLTIMO. */
    internal fun seedPhase(
        context: Context,
        stagingDir: File,
        versionDir: File,
        applyDefaults: Boolean
    ): TaskFlowExecutor.TaskPhase = buildPhase {
        addTask(id = "Dedicated.Seed", title = androidText(R.string.dedicated_task_seed)) { task ->
            val controlFile = ControlManager.ensureDefaultLayout(context)
            DedicatedSeeder.ensureVersionConfig(versionDir, controlFile, applyDefaults)
            DedicatedSeeder.ensureServer(versionDir)
            PackManifest.write(
                versionDir,
                PackManifest(
                    slug = BuildKeys.DEDICATED_PACK_SLUG,
                    packVersion = apiIdentity,
                    files = PackManifest.packFiles(stagingDir)
                )
            )
            task.updateProgress(1f)
        }
    }

    /** Etapa 8 (§5.2): seleciona a versão instalada como atual. */
    internal fun selectPhase(): TaskFlowExecutor.TaskPhase = buildPhase {
        addTask(id = "Dedicated.Select", title = androidText(R.string.dedicated_task_select)) { task ->
            VersionsManager.saveCurrentVersion(BuildKeys.DEDICATED_PACK_SLUG)
            task.updateProgress(1f)
        }
    }

    /** Etapas 1-5 estáticas; 6-8 entram dinamicamente depois da base (InstallBase). */
    internal fun buildPhases(): List<TaskFlowExecutor.TaskPhase> = listOf(
        downloadPhase(),
        extractBasePhase()
    )

    /** Pipeline completo (spec §5.2). Erros caem em onError; cancelamento em onCancel. */
    fun install(
        context: Context,
        onInstalled: () -> Unit,
        onCancel: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (taskExecutor.isRunning()) return
        appContext = context.applicationContext
        taskExecutor.executePhasesAsync(
            onStart = { taskExecutor.addPhases(buildPhases()) },
            onComplete = { onInstalled() },
            onCancel = { onCancel() },
            onError = { e -> onError(e) }
        )
    }

    fun cancelInstall() {
        taskExecutor.cancel()
        gameInstaller?.cancelInstall(clearTarget = false)
    }
}
