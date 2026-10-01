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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TechnicPackVersionResolverTest {

    /** Fixture da spec §4: bin/version.json do pack real. */
    private val versionJson = """
        { "id": "1.7.10-Forge10.13.4.1558-1.7.10",
          "inheritsFrom": "1.7.10",
          "libraries": ["net.minecraftforge:forge:1.7.10-10.13.4.1558-1.7.10", "org.lwjgl.lwjgl:lwjgl:2.9.4"] }
    """.trimIndent()

    private fun staging(): File = Files.createTempDirectory("resolver-staging").toFile()

    @Test
    fun `reads bin version json`() {
        val dir = staging()
        try {
            File(dir, "bin").mkdirs()
            File(dir, "bin/version.json").writeText(versionJson)

            val info = TechnicPackVersionResolver.resolve(dir, apiMinecraft = null)

            assertEquals(PackVersionInfo("1.7.10", "10.13.4.1558"), info)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `falls back to version json inside modpack jar`() {
        val dir = staging()
        try {
            File(dir, "bin").mkdirs()
            ZipOutputStream(FileOutputStream(File(dir, "bin/modpack.jar"))).use { zos ->
                zos.putNextEntry(ZipEntry("version.json"))
                zos.write(versionJson.toByteArray())
                zos.closeEntry()
            }

            val info = TechnicPackVersionResolver.resolve(dir, apiMinecraft = null)

            assertEquals(PackVersionInfo("1.7.10", "10.13.4.1558"), info)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `falls back to the api minecraft field`() {
        val dir = staging()
        try {
            val info = TechnicPackVersionResolver.resolve(dir, apiMinecraft = "1.7.10")

            assertEquals(PackVersionInfo("1.7.10", null), info)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `libraries declared as objects still resolve the forge build`() {
        val json = """
            { "id": "1.7.10-Forge10.13.4.1558-1.7.10",
              "inheritsFrom": "1.7.10",
              "libraries": [{"name": "net.minecraftforge:forge:1.7.10-10.13.4.1558-1.7.10"}] }
        """.trimIndent()
        val dir = stagingWith(json)
        try {
            assertEquals(PackVersionInfo("1.7.10", "10.13.4.1558"),
                TechnicPackVersionResolver.resolve(dir, apiMinecraft = null))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `forge build falls back to the version id`() {
        val json = """
            { "id": "1.7.10-Forge10.13.4.1558-1.7.10",
              "inheritsFrom": "1.7.10",
              "libraries": ["org.lwjgl.lwjgl:lwjgl:2.9.4"] }
        """.trimIndent()
        val dir = stagingWith(json)
        try {
            assertEquals(PackVersionInfo("1.7.10", "10.13.4.1558"),
                TechnicPackVersionResolver.resolve(dir, apiMinecraft = null))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test(expected = PackVersionUnresolvableException::class)
    fun `throws when there is no version source at all`() {
        val dir = staging()
        try {
            TechnicPackVersionResolver.resolve(dir, apiMinecraft = null)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `api fallback leaves forge unknown`() {
        val dir = staging()
        try {
            assertNull(TechnicPackVersionResolver.resolve(dir, "1.7.10").forgeBuild)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Monta um staging só com bin/version.json para testar o parser puro. */
    private fun stagingWith(json: String): File {
        val dir = staging()
        File(dir, "bin").mkdirs()
        File(dir, "bin/version.json").writeText(json)
        return dir
    }
}
