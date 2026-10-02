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
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.file.Files

/**
 * Semeamento da imagem de fundo da marca.
 *
 * O ponto sensível não é copiar o arquivo (isso é mecânico) e sim a política:
 * a arte entra uma única vez e nunca mais volta sozinha — nem quando o
 * jogador escolhe a própria imagem, nem quando ele aperta "Resetar" nas
 * configurações. Sem esse teste, um simples `if (!file.exists())` faria o
 * botão de reset não ter efeito nenhum.
 */
class BrandBackgroundTest {

    @Test
    fun `installs the brand image on a fresh install`() {
        val dir = Files.createTempDirectory("brand-bg").toFile()
        try {
            val target = File(dir, "background/background01.file")

            assertTrue(
                "instalação nova deve receber a imagem da marca",
                shouldInstallBrandBackground(target, alreadyInstalled = false)
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `never overwrites a background the player chose`() {
        val dir = Files.createTempDirectory("brand-bg").toFile()
        try {
            val target = File(dir, "background/background01.file")
            target.parentFile?.mkdirs()
            target.writeBytes(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x01))

            assertFalse(
                "uma imagem já existente é escolha do jogador e não deve ser sobrescrita",
                shouldInstallBrandBackground(target, alreadyInstalled = false)
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `does not come back after the player resets the background`() {
        val dir = Files.createTempDirectory("brand-bg").toFile()
        try {
            val target = File(dir, "background/background01.file")

            assertFalse(
                "após o Resetar, a imagem da marca não pode reaparecer sozinha",
                shouldInstallBrandBackground(target, alreadyInstalled = true)
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `copy writes a non empty jpeg and creates missing parent dirs`() {
        val dir = Files.createTempDirectory("brand-bg").toFile()
        try {
            val target = File(dir, "background/background01.file")
            val jpeg = byteArrayOf(
                0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10,
                0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01,
                0x00, 0x00, 0xFF.toByte(), 0xD9.toByte()
            )

            val written = installBrandBackground(ByteArrayInputStream(jpeg), target)

            assertTrue("a cópia deve reportar sucesso", written)
            assertTrue("o arquivo destino deve existir", target.exists())
            assertTrue("o arquivo destino não pode estar vazio", target.length() > 0)
            assertEquals("o conteúdo deve ser copiado intacto", jpeg.size.toLong(), target.length())
            assertEquals(
                "o arquivo precisa ter os bytes mágicos de JPEG (FF D8 FF)",
                listOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte()),
                target.readBytes().take(3)
            )
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `copy reports failure instead of leaving a half written background`() {
        val dir = Files.createTempDirectory("brand-bg").toFile()
        try {
            // Um stream que falha no meio: o destino não pode ficar com lixo,
            // senão o BackgroundViewModel pode enxergar um "arquivo" inválido.
            val target = File(dir, "background/background01.file")

            val written = installBrandBackground(BrokenStream(), target)

            assertFalse("uma cópia interrompida não pode reportar sucesso", written)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Stream que falha depois de alguns bytes. */
    private class BrokenStream : java.io.InputStream() {
        private var served = 0
        override fun read(): Int {
            if (served++ > 8) throw IllegalStateException("stream quebrado")
            return 0xFF
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (served++ > 1) throw IllegalStateException("stream quebrado")
            b.fill(0xFF.toByte(), off, off + len)
            return len
        }
    }
}