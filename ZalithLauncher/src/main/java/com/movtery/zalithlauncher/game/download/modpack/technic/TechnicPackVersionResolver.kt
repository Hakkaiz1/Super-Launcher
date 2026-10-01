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

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.ZipFile

/** Versão resolvida do staging (spec §5.2 etapa 4): base + build do Forge. */
data class PackVersionInfo(
    val minecraft: String,
    val forgeBuild: String?
)

class PackVersionUnresolvableException(message: String) : RuntimeException(message)

object TechnicPackVersionResolver {

    /**
     * Prioridade (spec §5.2 etapa 4): bin/version.json →
     * bin/modpack.jar!/version.json → campo `minecraft` da API.
     */
    fun resolve(stagingDir: File, apiMinecraft: String?): PackVersionInfo {
        File(stagingDir, "bin/version.json").takeIf { it.isFile }?.let { file ->
            parseVersionJson(file.readText())?.let { return it }
        }
        File(stagingDir, "bin/modpack.jar").takeIf { it.isFile }?.let { jar ->
            runCatching {
                ZipFile(jar).use { zip ->
                    val entry = zip.getEntry("version.json") ?: return@runCatching null
                    zip.getInputStream(entry).use { stream ->
                        parseVersionJson(stream.reader().readText())
                    }
                }
            }.getOrNull()?.let { return it }
        }
        apiMinecraft?.takeIf { it.isNotBlank() }?.let { return PackVersionInfo(it, null) }
        throw PackVersionUnresolvableException("No version source for the pack in $stagingDir")
    }

    /** `inheritsFrom` + build do Forge; null quando o JSON não serve como fonte. */
    internal fun parseVersionJson(json: String): PackVersionInfo? = runCatching {
        val root = Json.parseToJsonElement(json).jsonObject
        val inheritsFrom = root["inheritsFrom"]?.jsonPrimitive?.contentOrNull ?: return null
        val forgeBuild = root["libraries"]
            ?.let { it as? JsonArray }
            ?.firstNotNullOfOrNull { element -> forgeBuildFromLibrary(element) }
            ?: forgeBuildFromId(root["id"]?.jsonPrimitive?.contentOrNull)
            ?: return null
        PackVersionInfo(inheritsFrom, forgeBuild)
    }.getOrNull()

    /** `net.minecraftforge:forge:1.7.10-10.13.4.1558-1.7.10` → `10.13.4.1558`. */
    private fun forgeBuildFromLibrary(element: kotlinx.serialization.json.JsonElement): String? {
        val name = when (element) {
            is JsonObject -> element["name"]?.jsonPrimitive?.contentOrNull
            is JsonPrimitive -> element.content
            else -> null
        } ?: return null
        if (!name.startsWith("net.minecraftforge:forge:")) return null
        val coordinate = name.removePrefix("net.minecraftforge:forge:")
        val parts = coordinate.split('-')
        return parts.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    /** `1.7.10-Forge10.13.4.1558-1.7.10` → `10.13.4.1558` (quando as libraries não trazem o forge). */
    private fun forgeBuildFromId(id: String?): String? {
        id ?: return null
        return Regex("Forge(\\d+(?:\\.\\d+)+)").find(id)?.groupValues?.get(1)
    }
}
