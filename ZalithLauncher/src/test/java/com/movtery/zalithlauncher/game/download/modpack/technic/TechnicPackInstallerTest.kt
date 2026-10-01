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
}
