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

import com.movtery.zalithlauncher.game.download.engine.DownloadEngine
import com.movtery.zalithlauncher.game.download.modpack.technic.buildPackRequest
import com.movtery.zalithlauncher.game.download.modpack.technic.headContentLength
import com.movtery.zalithlauncher.game.download.modpack.technic.progressOf
import com.movtery.zalithlauncher.utils.file.extractFromZip
import com.movtery.zalithlauncher.utils.network.withSpeedReport
import java.io.File
import java.util.zip.ZipFile

/** Zip de mods no cache; nome estavel para o download ser reaproveitado no retry. */
const val DEDICATED_MODS_ARCHIVE = "mods.zip"

private const val STAGING_DIR = "mods_staging"
private const val MODS_FOLDER = "mods"
private const val ZIP_SIGNATURE_LENGTH = 4

/**
 * Baixa o zip de mods e troca `mods/` da versao instalada pelo conteudo dele.
 *
 * Roda depois do overlay do pack e antes do seed: o pack ainda traz a pasta
 * `mods/` dele, e e esse passo que a torna soberana. Nada fora de `mods/` e
 * tocado — `saves/`, `options.txt` e `servers.dat` ficam intactos.
 *
 * A ordem interno e deliberada: baixa, valida, extrai para staging e so entao
 * apaga. Se qualquer coisa falhar no meio, a versao fica com os mods anteriores
 * em vez de metade trocada.
 */
suspend fun installDedicatedMods(
    url: String,
    versionDir: File,
    cacheDir: File,
    onProgress: (Float) -> Unit,
    onSpeedReport: (Long) -> Unit,
    onSpeedClear: () -> Unit
) {
    // URL vazia = passo desligado (BuildKeys.DEDICATED_MODS_URL).
    if (isModsDownloadDisabled(url)) return

    cacheDir.mkdirs()
    val archive = File(cacheDir, DEDICATED_MODS_ARCHIVE)
    downloadArchive(url, archive, onProgress, onSpeedReport, onSpeedClear)

    applyModsArchive(
        archive = archive,
        versionDir = versionDir,
        stagingDir = File(cacheDir, STAGING_DIR),
        onProgress = onProgress
    )
}

/**
 * Extrai o zip e troca `mods/` da versao pelo conteudo dele.
 *
 * Seam sem rede e sem Android: o resto do caminho (validar, extrair, apagar,
 * copiar) e o que destrói arquivos, e precisa de teste sem passar pela rede.
 */
internal suspend fun applyModsArchive(
    archive: File,
    versionDir: File,
    stagingDir: File,
    onProgress: (Float) -> Unit = {}
) {
    // Antes de qualquer escrita: confirma que e zip e que tem mod algum.
    val incoming = requireModsEntries(readArchiveEntryNames(archive))

    stagingDir.deleteRecursively()
    ZipFile(archive).use { it.extractFromZip("$MODS_FOLDER/", stagingDir) }

    applyStagedMods(stagingDir, versionDir, incoming, onProgress)
}

private suspend fun downloadArchive(
    url: String,
    archive: File,
    onProgress: (Float) -> Unit,
    onSpeedReport: (Long) -> Unit,
    onSpeedClear: () -> Unit
) {
    val totalBytes = headContentLength(url)
    var downloaded = 0L
    onProgress(progressOf(0L, totalBytes))
    withSpeedReport(onSpeedReport, onSpeedClear) { report ->
        DownloadEngine.download(buildPackRequest(url, archive)) { delta ->
            downloaded += delta
            onProgress(progressOf(downloaded, totalBytes))
            report(delta)
        }
    }
    onProgress(1f)
}

/**
 * Nomes das entradas do zip, recusando anything que nao seja zip.
 *
 * A assinatura e conferida antes do `ZipFile`: o primeiro link apontava para um
 * RAR 5 com nome de `.zip`, e o `ZipFile` rebentaria so depois da validacao de
 * mod — com o jogo ja em estado inconsistente.
 */
private fun readArchiveEntryNames(archive: File): List<String> {
    val head = ByteArray(ZIP_SIGNATURE_LENGTH)
    val read = archive.inputStream().use { stream ->
        var filled = 0
        while (filled < head.size) {
            val count = stream.read(head, filled, head.size - filled)
            if (count <= 0) break
            filled += count
        }
        filled
    }
    if (!isZipArchive(head.copyOf(read))) {
        throw ModsArchiveInvalidException(
            "Downloaded mods file is not a zip (${archive.length()} bytes): " +
                "check the URL (needs dl=1) and that the file is really a zip, not a renamed rar."
        )
    }
    return ZipFile(archive).use { zip -> zip.entries().toList().map { it.name } }
}

/**
 * Aplica o staging: apaga o que o zip nao traz e copia o que ele traz.
 *
 * A ordem (apagar antes de copiar) e o que garante que um mod trocado de nome
 * de arquivo nao deixe duas copias: o FML 1.7.10 aborta carregando mod
 * duplicado, e quatro mods mudaram de nome entre o pack e o zip.
 */
private fun applyStagedMods(
    staging: File,
    versionDir: File,
    incoming: List<String>,
    onProgress: (Float) -> Unit
) {
    // Varragem ampla de proposito: quem decide o que e apagavel e
    // [staleModFiles], que so aceita `mods/`. Se o filtro sumisse daqui, nada
    // impediria `saves/` de entrar na lista de remocao — e o teste existe para
    // barrar exatamente isso.
    val existing = versionDir.walkTopDown()
        .filter { it.isFile }
        .map { it.relativeTo(versionDir).invariantSeparatorsPath }
        .toList()

    staleModFiles(existing, incoming).forEach { relative ->
        File(versionDir, relative).delete()
    }

    val staged = staging.walkTopDown()
        .filter { it.isFile }
        .sortedBy { it.relativeTo(staging).invariantSeparatorsPath }
        .toList()

    staged.forEachIndexed { index, source ->
        val relative = "$MODS_FOLDER/${source.relativeTo(staging).invariantSeparatorsPath}"
        val target = File(versionDir, relative)
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)
        onProgress((index + 1).toFloat() / staged.size)
    }
    if (staged.isEmpty()) onProgress(1f)

    staging.deleteRecursively()
}