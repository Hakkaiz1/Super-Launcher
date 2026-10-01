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

package com.movtery.zalithlauncher.game.control

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files

class ControlManagerSeedTest {

    @Test
    fun `copies the default layout only when missing`() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("control-seed").toFile()
        try {
            var copies = 0

            val first = ControlManager.ensureLayoutFile(dir) { output ->
                copies++
                output.writeText("{}")
            }

            assertEquals("default_layout.json", first.name)
            assertTrue(first.isFile)
            assertEquals(1, copies)

            val second = ControlManager.ensureLayoutFile(dir) { output ->
                copies++
                output.writeText("{}")
            }

            assertEquals("não deve copiar de novo", 1, copies)
            assertEquals(first.absolutePath, second.absolutePath)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `leaves an existing layout untouched`() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("control-seed").toFile()
        try {
            java.io.File(dir, "default_layout.json").writeText("player-layout")

            ControlManager.ensureLayoutFile(dir) { _ ->
                fail("não deveria copiar: o arquivo já existe")
            }

            assertEquals("player-layout", java.io.File(dir, "default_layout.json").readText())
        } finally {
            dir.deleteRecursively()
        }
    }
}
