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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Os mods do pack dedicado vêm de um zip no Dropbox; o pack do Technic segue
 * como base do jogo.
 *
 * O zip é soberano sobre `mods/`: o que ele não trouxer sai. Isso não é
 * detalhe, é o que evita mods duplicados — quatro mods mudaram de nome de
 * arquivo entre o pack e o zip (UniMixins 0.1.17→0.3.2, CodeChickenCore,
 * CustomNPC+, dbcsuperitem) e duas copias lado a lado derrubam o FML 1.7.10.
 */
class DedicatedModsTest {

    private val packMods = listOf(
        "mods/+unimixins-all-1.7.10-0.1.17 (1).jar",
        "mods/1.7.10/CodeChickenLib-1.7.10-1.1.3.138-universal.jar",
        "mods/1.7.10bspkrsCore-universal-6.12.jar",
        "mods/OptiFine_1.7.10_HD_U_E7.jar",
        "mods/Damage-Indicators-Mod-1.7.10.jar",
        "mods/super-boss-bar.jar",
        "mods/npcdbc-1.0.6.jar",
        "mods/DragonBlockC-v1.4.85.jar",
        "mods/JRMCore-v1.3.51.jar"
    )

    private val zipMods = listOf(
        "mods/",
        "mods/+unimixins-all-1.7.10-0.3.2.jar",
        "mods/1.7.10/",
        "mods/1.7.10/CodeChickenLib-1.7.10-1.1.3.138-universal.jar",
        "mods/1.7.10bspkrsCore-universal-6.12.jar",
        "mods/angelica-NO-GIT-TAG-SET.jar",
        "mods/DragonBlockC-v1.4.85.jar",
        "mods/JRMCore-v1.3.51.jar"
    )

    @Test
    fun `mods entries keep the mods prefix so the guard can see them`() {
        val entries = modsEntriesIn(zipMods)

        assertEquals(
            "so os arquivos de mods/, sem as pastas e com o prefixo intacto",
            listOf(
                "mods/+unimixins-all-1.7.10-0.3.2.jar",
                "mods/1.7.10/CodeChickenLib-1.7.10-1.1.3.138-universal.jar",
                "mods/1.7.10bspkrsCore-universal-6.12.jar",
                "mods/angelica-NO-GIT-TAG-SET.jar",
                "mods/DragonBlockC-v1.4.85.jar",
                "mods/JRMCore-v1.3.51.jar"
            ),
            entries
        )
    }

    @Test
    fun `entries outside the mods folder are ignored`() {
        val entries = modsEntriesIn(
            listOf("mods/A.jar", "config/dbc.toml", "options.txt", "saves/1/level.dat", "core.jar")
        )

        assertEquals(listOf("mods/A.jar"), entries)
    }

    @Test
    fun `an archive without a single mod is rejected`() {
        // Sem esta trava um zip vazio (ouso errado da URL) apagaria todos os mods
        // do pack e o jogo ficaria sem nada para carregar.
        try {
            requireModsEntries(listOf("config/x.toml", "options.txt"))
            fail("um arquivo sem mods tem que ser rejeitado antes de tocar em mods/")
        } catch (expected: ModsArchiveInvalidException) {
            assertTrue(
                "a mensagem precisa explicar o que fazer",
                expected.message!!.contains("mods", ignoreCase = true)
            )
        }
    }

    @Test
    fun `stale mods are the ones the archive does not bring`() {
        val stale = staleModFiles(
            existing = modsEntriesIn(packMods),
            incoming = modsEntriesIn(zipMods)
        )

        assertEquals(
            "UniMixins antigo, OptiFine e os outros que o zip nao traz saem; " +
                "CodeChickenLib, DragonBlockC, JRMCore e bspkrsCore ficam",
            listOf(
                "mods/+unimixins-all-1.7.10-0.1.17 (1).jar",
                "mods/Damage-Indicators-Mod-1.7.10.jar",
                "mods/OptiFine_1.7.10_HD_U_E7.jar",
                "mods/npcdbc-1.0.6.jar",
                "mods/super-boss-bar.jar"
            ),
            stale
        )
    }

    @Test
    fun `nothing outside the mods folder can ever be deleted`() {
        // A regra e "o zip manda em mods/", nao "o zip manda no jogo": saves/,
        // options.txt e servers.dat estao fora e nunca podem ser alcancados.
        for (path in listOf("saves/1/level.dat", "options.txt", "servers.dat", "config/x.toml")) {
            assertFalse("$path nao esta em mods/", isModPath(path))
        }
        assertTrue(isModPath("mods/OptiFine_1.7.10_HD_U_E7.jar"))
        assertTrue(isModPath("mods/1.7.10/CodeChickenLib.jar"))
    }

    @Test
    fun `stale mods never escape the mods folder`() {
        val stale = staleModFiles(
            existing = listOf("saves/1/level.dat", "options.txt", "mods/A.jar"),
            incoming = listOf("mods/B.jar")
        )

        assertEquals("so entra na lista o que esta de fato em mods/", listOf("mods/A.jar"), stale)
    }

    @Test
    fun `a rar archive is refused before anything is written`() {
        // O primeiro link apontava para um RAR 5 com nome de .zip; o extrator do
        // launcher so entende zip e falharia no meio da aplicacao.
        assertTrue(isZipArchive(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertFalse("RAR 5 com nome de zip", isZipArchive(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07)))
        assertFalse("pagina HTML do Dropbox (dl=0)", isZipArchive("<!DOCTYPE html>".toByteArray()))
        assertFalse("arquivo vazio", isZipArchive(ByteArray(0)))
    }

    @Test
    fun `the shipped mods url is a direct download link`() {
        val url = BuildKeys.DEDICATED_MODS_URL

        assertTrue(
            "com dl=0 o Dropbox devolve text/html e o launcher baixa uma pagina; " +
                "a URL precisa terminar em dl=1",
            url.contains("dl=1")
        )
    }

    @Test
    fun `an empty url disables the mods download`() {
        // Desligar o Dropbox depois e so limpar DEDICATED_MODS_URL: sem URL nao ha
        // o que baixar e o passo inteiro e pulado.
        assertTrue(
            "URL em branco precisa desativar o passo",
            isModsDownloadDisabled("")
        )
        assertFalse(isModsDownloadDisabled("https://example.com/mods.zip?dl=1"))
    }
}