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

package com.movtery.zalithlauncher.ui.screens.content.dedicated

import com.movtery.zalithlauncher.ui.theme.assertContrast
import org.junit.Test

/**
 * O botão Jogar é o único elemento da tela com gradiente próprio (dourado, da
 * identidade DBC SUPER) e não usa a cor do tema — por isso o rótulo não pode
 * depender de onPrimary: ele precisa contrastar com as DUAS pontas douradas,
 * em qualquer tema que o jogador escolher.
 */
class DedicatedPlayButtonTest {

    @Test
    fun `play button label is readable on both gradient ends`() {
        assertContrast("label/gradient start", playButtonLabel, playButtonGradient.first())
        assertContrast("label/gradient end", playButtonLabel, playButtonGradient.last())
    }

    @Test
    fun `the two gradient ends are actually different`() {
        // Um gradiente de uma cor só não é gradiente; este teste pega o caso em
        // que alguém "simplifica" a lista para um único valor.
        org.junit.Assert.assertNotEquals(
            "o gradiente precisa ter duas cores distintas",
            playButtonGradient.first(),
            playButtonGradient.last()
        )
        org.junit.Assert.assertEquals(2, playButtonGradient.size)
    }
}