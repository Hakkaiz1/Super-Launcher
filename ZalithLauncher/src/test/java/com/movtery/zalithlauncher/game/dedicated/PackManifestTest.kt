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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PackManifestTest {

    private fun staging(): File = Files.createTempDirectory("pack-staging").toFile()
    private fun versionDir(): File = Files.createTempDirectory("pack-version").toFile()

    @Test
    fun `write and read round trip atomically`() {
        val dir = versionDir()
        try {
            val manifest = PackManifest("dbc-super-oficial", "10.8", listOf("mods/a.jar", "config/b.cfg"))
            PackManifest.write(dir, manifest)

            assertEquals(manifest, PackManifest.read(dir))
            assertTrue(PackManifest.manifestFile(dir).isFile)
            val leftovers = PackManifest.manifestFile(dir).parentFile?.listFiles().orEmpty()
                .filter { it.name.endsWith(".tmp") }
            assertTrue("gravação atômica deixou arquivo .tmp", leftovers.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `missing manifest reads as null`() {
        val dir = versionDir()
        try {
            assertNull(PackManifest.read(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `corrupt manifest reads as null instead of throwing`() {
        val dir = versionDir()
        try {
            val file = PackManifest.manifestFile(dir)
            file.parentFile?.mkdirs()
            file.writeText("{broken json")
            assertNull(PackManifest.read(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `pack files exclude bin and player data`() {
        val dir = staging()
        try {
            listOf(
                "bin/minecraft.jar", "bin/natives/awt.dll",
                "mods/x.jar", "config/a.cfg",
                "servers.dat", "options.txt", "saves/world/level.dat"
            ).forEach { path ->
                val file = File(dir, path)
                file.parentFile?.mkdirs()
                file.writeText("x")
            }

            assertEquals(listOf("config/a.cfg", "mods/x.jar"), PackManifest.packFiles(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `orphans list only removed pack files`() {
        val old = PackManifest("dbc-super-oficial", "10.7",
            listOf("mods/keep.jar", "mods/gone.jar", "config/old.cfg"))
        val newFiles = listOf("mods/keep.jar")

        assertEquals(listOf("config/old.cfg", "mods/gone.jar"), PackManifest.orphans(old, newFiles))
    }

    @Test
    fun `protected paths never appear in the orphan list`() {
        val old = PackManifest("dbc-super-oficial", "10.7",
            listOf("servers.dat", "options.txt", "saves/world/level.dat", "bin/version.json", "mods/gone.jar"))

        assertEquals(listOf("mods/gone.jar"), PackManifest.orphans(old, emptyList()))
        assertTrue(PackManifest.isProtected("saves/any/thing"))
        assertTrue(PackManifest.isProtected("options.txt"))
        assertTrue(PackManifest.isProtected("servers.dat"))
        assertFalse(PackManifest.isProtected("mods/x.jar"))
    }

    @Test
    fun `identity uses version then url then nothing`() {
        assertEquals("10.8", PackManifest.identityOf("10.8", "https://dl/x.zip"))
        assertEquals("https://dl/x.zip", PackManifest.identityOf(null, "https://dl/x.zip"))
        assertEquals("https://dl/x.zip", PackManifest.identityOf("  ", "https://dl/x.zip"))
        assertNull(PackManifest.identityOf(null, null))
        assertNull(PackManifest.identityOf("", ""))
    }
}
