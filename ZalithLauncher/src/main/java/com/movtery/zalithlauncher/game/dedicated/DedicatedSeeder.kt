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
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "DedicatedSeeder"

object DedicatedSeeder {

    /**
     * Spec §7.1: garante a entrada `DEDICATED_SERVER_NAME` / `DEDICATED_SERVER_IP`
     * em `servers.dat` (NBT cru do 1.7.10). Nunca remove entradas existentes e,
     * quando nada muda, não reescreve o arquivo (atualizações ficam intactas).
     * @return true somente quando o arquivo foi alterado.
     */
    suspend fun ensureServer(versionDir: File): Boolean = withContext(Dispatchers.IO) {
        val dataFile = File(versionDir, "servers.dat")
        val servers = AllServers()
        if (dataFile.exists()) servers.loadServers(dataFile)

        val name = BuildKeys.DEDICATED_SERVER_NAME
        val ip = BuildKeys.DEDICATED_SERVER_IP
        val existing = servers.serverList.firstOrNull { it.originIp == ip }
        val changed = when {
            existing == null -> {
                servers.addServer(ServerData(name = name, originIp = ip))
                true
            }
            existing.name != name -> {
                existing.name = name
                true
            }
            else -> false
        }
        if (changed) servers.save(versionDir)
        changed
    }

    /**
     * Spec §7.3: na primeira instalação liga isolamento de versão, RAM herdada
     * (-1), o layout de controle e `skipGameIntegrityCheck=false` (nunca pular).
     * Em atualização (`applyDefaults=false`) o `version.config` não é tocado.
     */
    suspend fun ensureVersionConfig(
        versionDir: File,
        controlFileName: String,
        applyDefaults: Boolean
    ) = withContext(Dispatchers.IO) {
        if (!applyDefaults) {
            Logger.info(TAG, "version.config preserved (pack update)")
            return@withContext
        }
        val config = VersionConfig.parseConfig(versionDir)
        config.isolationType = SettingState.ENABLE
        config.skipGameIntegrityCheck = SettingState.DISABLE
        config.ramAllocation = -1
        config.control = controlFileName
        config.save()
    }
}
