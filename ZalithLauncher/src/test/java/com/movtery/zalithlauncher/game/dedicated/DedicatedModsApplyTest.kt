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

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * O caminho que apaga arquivos de verdade, com um zip de verdade.
 *
 * Os testes de [DedicatedModsTest] cobrem a regra; este cobre a efeito no disco.
 * O motivo e o custo do erro: um `staleModFiles` que devolva a lista errada
 * apaga os mods do jogador — ou o inverso, deixa versao antiga ao lado da nova
 * e o FML aborta por mod duplicado.
 */
class DedicatedModsApplyTest {

    @get:Rule
    val temp = TemporaryFolder()

    private fun versionDir(): File = temp.newFolder("versions/dbc-super-oficial")

    private fun writeFile(file: File, content: String = "x"): File {
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }

    private fun zipWith(entries: Map<String, String>, target: File) {
        target.parentFile.mkdirs()
        ZipOutputStream(target.outputStream().buffered()).use { out ->
            entries.forEach { (name, content) ->
                out.putNextEntry(ZipEntry(name))
                out.write(content.toByteArray())
                out.closeEntry()
            }
        }
    }

    @Test
    fun `mods from the pack that the archive omits are deleted`() = runBlocking<Unit> {
        val version = versionDir()
        writeFile(File(version, "mods/Unimixins-0.1.17.jar"))
        writeFile(File(version, "mods/OptiFine_1.7.10_HD_U_E7.jar"))
        writeFile(File(version, "mods/DragonBlockC-v1.4.85.jar"))
        val archive = File(temp.newFolder("cache"), "mods.zip")
        zipWith(
            mapOf(
                "mods/" to "",
                "mods/Unimixins-0.3.2.jar" to "novo",
                "mods/DragonBlockC-v1.4.85.jar" to "mesmo"
            ),
            archive
        )

        applyModsArchive(archive, version, File(temp.root, "staging"))

        assertFalse("UniMixins antigo saiu", File(version, "mods/Unimixins-0.1.17.jar").exists())
        assertFalse("OptiFine saiu", File(version, "mods/OptiFine_1.7.10_HD_U_E7.jar").exists())
        assertTrue("DragonBlockC foi reescrito", File(version, "mods/DragonBlockC-v1.4.85.jar").exists())
        assertEquals("novo", File(version, "mods/Unimixins-0.3.2.jar").readText())
    }

    @Test
    fun `player data and pack config survive the mods swap`() = runBlocking<Unit> {
        val version = versionDir()
        val level = writeFile(File(version, "saves/Mundo/level.dat"), "mundo")
        val options = writeFile(File(version, "options.txt"), "options")
        val servers = writeFile(File(version, "servers.dat"), "servers")
        val config = writeFile(File(version, "config/dbc.toml"), "pack")
        val oldMod = writeFile(File(version, "mods/Antigo.jar"))
        val archive = File(temp.newFolder("cache"), "mods.zip")
        zipWith(mapOf("mods/Novo.jar" to "novo", "config/ignorar.toml" to "nao-copiar"), archive)

        applyModsArchive(archive, version, File(temp.root, "staging"))

        assertEquals("mundo", level.readText())
        assertEquals("options", options.readText())
        assertEquals("servers", servers.readText())
        assertEquals("o config do pack nao e do zip", "pack", config.readText())
        assertFalse("mod antigo saiu", oldMod.exists())
        assertTrue("mod novo entrou", File(version, "mods/Novo.jar").exists())
        assertFalse(
            "o zip nao pode escrever fora de mods/",
            File(version, "config/ignorar.toml").exists()
        )
    }

    @Test
    fun `a nested mods folder is recreated`() = runBlocking<Unit> {
        val version = versionDir()
        val archive = File(temp.newFolder("cache"), "mods.zip")
        zipWith(mapOf("mods/1.7.10/CodeChickenLib.jar" to "ccl"), archive)

        applyModsArchive(archive, version, File(temp.root, "staging"))

        assertTrue(
            "CodeChickenLib vive em mods/1.7.10/ e o FML espera esse caminho",
            File(version, "mods/1.7.10/CodeChickenLib.jar").isFile
        )
    }

    @Test
    fun `an archive that is not a zip is refused before any file is touched`() = runBlocking<Unit> {
        val version = versionDir()
        val mod = writeFile(File(version, "mods/OptiFine_1.7.10_HD_U_E7.jar"))
        val rar = File(temp.newFolder("cache"), "mods.zip")
        rar.writeBytes(byteArrayOf(0x52, 0x61, 0x72, 0x21, 0x1A, 0x07, 0x01, 0x00))

        try {
            applyModsArchive(rar, version, File(temp.root, "staging"))
            throw AssertionError("um RAR tem que ser recusado")
        } catch (expected: ModsArchiveInvalidException) {
            assertTrue(
                "a falha tem que explicar o link/arquivo",
                expected.message!!.contains("zip", ignoreCase = true)
            )
        }

        assertTrue("com o arquivo recusado, os mods antigos continuam", mod.exists())
    }
}