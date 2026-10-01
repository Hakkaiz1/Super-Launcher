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

import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.game.version.installed.SettingState
import com.movtery.zalithlauncher.game.version.installed.VersionConfig
import com.movtery.zalithlauncher.game.version.multiplayer.AllServers
import com.movtery.zalithlauncher.game.version.multiplayer.ServerData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DedicatedSeederTest {

    private fun tempDir(): File = Files.createTempDirectory("dedicated-seeder").toFile()

    private suspend fun readServers(dir: File): AllServers =
        AllServers().also { it.loadServers(File(dir, "servers.dat")) }

    @Test
    fun `seeds the dedicated server into an empty directory`() = runBlocking<Unit> {
        val dir = tempDir()
        try {
            assertTrue(DedicatedSeeder.ensureServer(dir))

            val servers = readServers(dir)
            assertEquals(1, servers.serverList.size)
            assertEquals(BuildKeys.DEDICATED_SERVER_NAME, servers.serverList[0].name)
            assertEquals(BuildKeys.DEDICATED_SERVER_IP, servers.serverList[0].originIp)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `never duplicates the entry nor rewrites an unchanged file`() = runBlocking<Unit> {
        val dir = tempDir()
        try {
            DedicatedSeeder.ensureServer(dir)
            val bytes = File(dir, "servers.dat").readBytes()

            assertFalse("segunda chamada não deve mudar nada", DedicatedSeeder.ensureServer(dir))
            assertArrayEquals("arquivo não deveria ser reescrito", bytes, File(dir, "servers.dat").readBytes())
            assertEquals(1, readServers(dir).serverList.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `preserves player entries`() = runBlocking<Unit> {
        val dir = tempDir()
        try {
            val player = AllServers()
            player.addServer(ServerData(name = "Minha Hypixel", originIp = "mc.hypixel.net"))
            player.save(dir)

            DedicatedSeeder.ensureServer(dir)

            val servers = readServers(dir)
            assertEquals(2, servers.serverList.size)
            assertTrue(servers.serverList.any {
                it.name == "Minha Hypixel" && it.originIp == "mc.hypixel.net"
            })
            assertTrue(servers.serverList.any {
                it.name == BuildKeys.DEDICATED_SERVER_NAME && it.originIp == BuildKeys.DEDICATED_SERVER_IP
            })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `renames the dedicated entry when the configured name differs`() = runBlocking<Unit> {
        val dir = tempDir()
        try {
            val pack = AllServers()
            pack.addServer(ServerData(name = "Outro Nome", originIp = BuildKeys.DEDICATED_SERVER_IP))
            pack.save(dir)

            assertTrue(DedicatedSeeder.ensureServer(dir))

            val servers = readServers(dir)
            assertEquals(1, servers.serverList.size)
            assertEquals(BuildKeys.DEDICATED_SERVER_NAME, servers.serverList[0].name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `first install applies the dedicated version config`() = runBlocking<Unit> {
        val dir = tempDir()
        try {
            DedicatedSeeder.ensureVersionConfig(dir, "default_layout.json", applyDefaults = true)

            val config = VersionConfig.parseConfig(dir)
            assertEquals(SettingState.ENABLE, config.isolationType)
            assertEquals(SettingState.DISABLE, config.skipGameIntegrityCheck)
            assertEquals(-1, config.ramAllocation)
            assertEquals("default_layout.json", config.control)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `pack update preserves the player version config`() = runBlocking<Unit> {
        val dir = tempDir()
        try {
            DedicatedSeeder.ensureVersionConfig(dir, "default_layout.json", applyDefaults = true)
            val custom = VersionConfig.parseConfig(dir).apply {
                ramAllocation = 2048
                control = "custom.json"
            }
            custom.save()

            DedicatedSeeder.ensureVersionConfig(dir, "default_layout.json", applyDefaults = false)

            val config = VersionConfig.parseConfig(dir)
            assertEquals(2048, config.ramAllocation)
            assertEquals("custom.json", config.control)
        } finally {
            dir.deleteRecursively()
        }
    }
}
