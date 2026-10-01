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

import com.movtery.zalithlauncher.game.dedicated.PackManifest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TechnicPackInstallerTest {

    @Test
    fun `pack request goes straight to the DownloadEngine`() {
        val url = "https://www.dropbox.com/scl/fi/example/ATT58.zip?rlkey=abc&dl=1"
        val request = buildPackRequest(url, File("pack.zip"))

        assertEquals(listOf(url), request.urls)
        assertNull(request.sha1)                 // a Platform não publica hash do zip (spec §4)
        assertEquals(-1L, request.expectedSize)  // sem gate de tamanho; total chega via HEAD
        assertEquals(TechnicApi.BROWSER_USER_AGENT, request.userAgent)  // §5.5: UA de navegador
    }

    @Test
    fun `progress is indeterminate when the total is unknown`() {
        assertEquals(-1f, progressOf(512L, -1L), 0.0001f)
        assertEquals(-1f, progressOf(512L, 0L), 0.0001f)
        assertEquals(0.5f, progressOf(512L, 1024L), 0.0001f)
        assertEquals(1f, progressOf(4096L, 1024L), 0.0001f)
    }

    @Test
    fun `extract unpacks the whole archive into staging`() = runBlocking<Unit> {
        val root = Files.createTempDirectory("dedicated-extract").toFile()
        try {
            val zip = File(root, "pack.zip")
            ZipOutputStream(FileOutputStream(zip)).use { zos ->
                zos.putNextEntry(ZipEntry("bin/version.json"))
                zos.write("""{"id":"x"}""".toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("mods/a.jar"))
                zos.write(byteArrayOf(1, 2, 3))
                zos.closeEntry()
            }
            val staging = File(root, "staging")

            extractPack(zip, staging)

            assertTrue(File(staging, "bin/version.json").isFile)
            assertTrue(File(staging, "mods/a.jar").isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `free space is required before extracting`() {
        ensureFreeSpace(availableBytes = 200L, requiredBytes = 100L)   // não lança
        try {
            ensureFreeSpace(availableBytes = 50L, requiredBytes = 100L)
            fail("deveria lançar InsufficientSpaceException")
        } catch (expected: InsufficientSpaceException) {
            assertTrue(expected.message!!.contains("100"))
        }
    }

    @Test
    fun `pipeline starts with download then extract and base phases`() {
        val installer = TechnicPackInstaller(CoroutineScope(Dispatchers.Unconfined))

        val phases = installer.buildPhases()

        assertEquals(2, phases.size)
        assertEquals(3, phases[0].tasks.size)   // ClearTemp + ResolvePack + Download
        assertEquals(3, phases[1].tasks.size)   // Extract + ResolveVersion + InstallBase
    }

    @Test
    fun `first install copies player data but never bin`() = runBlocking<Unit> {
        val root = Files.createTempDirectory("overlay-first").toFile()
        try {
            val staging = File(root, "staging")
            val versionDir = File(root, "version")
            listOf("bin/minecraft.jar", "mods/pack.jar", "servers.dat", "options.txt", "saves/packworld/level.dat")
                .forEach { path ->
                    File(staging, path).let { it.parentFile?.mkdirs(); it.writeText("pack") }
                }
            val progress = mutableListOf<Float>()

            overlayPack(staging, versionDir, oldManifest = null) { progress.add(it) }

            assertFalse(File(versionDir, "bin/minecraft.jar").exists())
            assertEquals("pack", File(versionDir, "mods/pack.jar").readText())
            assertEquals("pack", File(versionDir, "servers.dat").readText())
            assertEquals("pack", File(versionDir, "options.txt").readText())
            assertEquals("pack", File(versionDir, "saves/packworld/level.dat").readText())
            assertEquals(1f, progress.last(), 0.0001f)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `update never overwrites player data and removes orphan mods`() = runBlocking<Unit> {
        val root = Files.createTempDirectory("overlay-update").toFile()
        try {
            val staging = File(root, "staging")
            val versionDir = File(root, "version")
            // estado do jogador após uma instalação anterior
            File(versionDir, "mods/old.jar").let { it.parentFile?.mkdirs(); it.writeText("old") }
            File(versionDir, "mods/new.jar").let { it.parentFile?.mkdirs(); it.writeText("stale") }
            File(versionDir, "options.txt").let { it.parentFile?.mkdirs(); it.writeText("player-options") }
            File(versionDir, "servers.dat").let { it.parentFile?.mkdirs(); it.writeText("player-servers") }
            File(versionDir, "saves/mysave/level.dat").let { it.parentFile?.mkdirs(); it.writeText("player-save") }
            // zip novo: mods/new.jar atualizado + protegidos diferentes (que NÃO devem entrar)
            File(staging, "mods/new.jar").let { it.parentFile?.mkdirs(); it.writeText("pack-new") }
            File(staging, "options.txt").let { it.parentFile?.mkdirs(); it.writeText("pack-options") }
            File(staging, "servers.dat").let { it.parentFile?.mkdirs(); it.writeText("pack-servers") }

            val oldManifest = PackManifest("dbc-super-oficial", "10.7", listOf("mods/old.jar", "mods/new.jar"))
            overlayPack(staging, versionDir, oldManifest) { }

            assertFalse("órfão do manifesto antigo deveria sumir", File(versionDir, "mods/old.jar").exists())
            assertEquals("pack-new", File(versionDir, "mods/new.jar").readText())
            assertEquals("player-options", File(versionDir, "options.txt").readText())
            assertEquals("player-servers", File(versionDir, "servers.dat").readText())
            assertEquals("player-save", File(versionDir, "saves/mysave/level.dat").readText())
        } finally {
            root.deleteRecursively()
        }
    }
}
