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

import com.movtery.zalithlauncher.game.version.installed.getZalithVersionPath
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Conteúdo do pack-manifest.json (spec §5.3): o que o pack possui,
 * para apagar órfãos no update sem tocar nos dados do jogador.
 */
@Serializable
data class PackManifest(
    val slug: String,
    val packVersion: String,
    val files: List<String>
) {
    companion object {
        const val FILE_NAME = "pack-manifest.json"
        private val JSON = Json { prettyPrint = true }

        /** `versions/<slug>/<LAUNCHER_IDENTIFIER>/pack-manifest.json` — ao lado do version.config (§5.3). */
        fun manifestFile(versionDir: File): File = File(getZalithVersionPath(versionDir), FILE_NAME)

        /** Manifesto ausente ou corrompido = ausente; nunca lança (spec §5.5: volta a instalar). */
        fun read(versionDir: File): PackManifest? {
            val file = manifestFile(versionDir)
            if (!file.isFile) return null
            return runCatching { JSON.decodeFromString(PackManifest.serializer(), file.readText()) }
                .getOrNull()
        }

        /** Gravação atômica: tmp no mesmo diretório + move com replace (§5.3 regra 5). */
        fun write(versionDir: File, manifest: PackManifest) {
            val target = manifestFile(versionDir)
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(JSON.encodeToString(PackManifest.serializer(), manifest))
            Files.move(
                tmp.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
            )
        }

        /** Dados do jogador: fora do manifesto e nunca removidos (§5.3 regra 4). */
        fun isProtected(relativePath: String): Boolean {
            val path = relativePath.replace('\\', '/')
            return path == "options.txt" || path == "servers.dat" || path.startsWith("saves/")
        }

        private fun isBinPath(relativePath: String): Boolean {
            val path = relativePath.replace('\\', '/')
            return path == "bin" || path.startsWith("bin/")
        }

        /** O que pertence ao pack: staging inteiro menos `bin/` e dados do jogador (§5.2 etapa 6). */
        fun packFiles(stagingDir: File): List<String> =
            stagingDir.walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(stagingDir).invariantSeparatorsPath }
                .filter { !isBinPath(it) && !isProtected(it) }
                .sorted()
                .toList()

        /** Update: arquivos do manifesto antigo que não existem mais no pack novo (§5.3 regra 2). */
        fun orphans(old: PackManifest, newPackFiles: List<String>): List<String> {
            val kept = newPackFiles.toSet()
            return old.files
                .map { it.replace('\\', '/') }
                .filter { it !in kept && !isProtected(it) && !isBinPath(it) }
                .distinct()
                .sorted()
                .toList()
        }

        /**
         * Identidade do pack (spec §5.1): `version`; se vazio cai para `url`;
         * null quando os dois vazios (aí o estado considera READY, nunca loop).
         */
        fun identityOf(version: String?, url: String?): String? =
            version?.takeIf { it.isNotBlank() }
                ?: url?.takeIf { it.isNotBlank() }
    }
}
