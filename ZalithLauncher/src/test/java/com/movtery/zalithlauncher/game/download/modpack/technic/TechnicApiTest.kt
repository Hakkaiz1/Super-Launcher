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
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicApiTest {

    /** Fixture da spec §4 (dados verificados em 2026-09-29), com campo extra para ignorar desconhecidos. */
    private val zipFixture = """
        {
          "displayName": "DBC Super (Oficial)",
          "minecraft": "1.7.10",
          "version": "10.8",
          "url": "https://www.dropbox.com/scl/fi/example/ATT58.zip?rlkey=abc&dl=1",
          "solder": null,
          "icon": { "url": "https://cdn.technicpack.net/platform2/pack-icons/1132904.png" },
          "unknownFutureField": { "ignored": true }
        }
    """.trimIndent()

    private fun fetch(body: String, code: Int = 200) =
        { _: String -> TechnicApi.RawResponse(code, body) }

    @Test
    fun `zip mode fixture parses all fields and ignores unknown ones`() {
        val pack = TechnicApi.getPack("https://api.technicpack.net/modpack/x", fetch(zipFixture))
        assertEquals("DBC Super (Oficial)", pack.displayName)
        assertEquals("1.7.10", pack.minecraft)
        assertEquals("10.8", pack.version)
        assertEquals("https://www.dropbox.com/scl/fi/example/ATT58.zip?rlkey=abc&dl=1", pack.url)
        assertNull(pack.solder)
        assertEquals("https://cdn.technicpack.net/platform2/pack-icons/1132904.png", pack.icon?.url)
    }

    @Test
    fun `explicit solder null parses as null`() {
        val pack = TechnicApi.getPack("https://api.technicpack.net/modpack/x",
            fetch("""{"minecraft":"1.7.10","solder":null}"""))
        assertNull(pack.solder)
        assertEquals("1.7.10", pack.minecraft)
    }

    @Test(expected = TechnicApi.BuildRejectedException::class)
    fun `http 401 rejects the launcher build`() {
        TechnicApi.getPack("https://api.technicpack.net/modpack/x",
            fetch("""{"code":401}""", code = 401))
    }

    @Test(expected = TechnicApi.PackUnavailableException::class)
    fun `http 503 surfaces as pack unavailable`() {
        TechnicApi.getPack("https://api.technicpack.net/modpack/x",
            fetch("maintenance", code = 503))
    }

    @Test(expected = TechnicApi.MalformedResponseException::class)
    fun `malformed json throws malformed response`() {
        TechnicApi.getPack("https://api.technicpack.net/modpack/x", fetch("not-json{{"))
    }

    @Test
    fun `pack url embeds the current launcher build`() {
        val url = TechnicApi.packUrl("dbc-super-oficial")
        assertTrue(url.startsWith("https://api.technicpack.net/modpack/dbc-super-oficial"))
        assertTrue(url.contains("build=1166"))
    }
}
