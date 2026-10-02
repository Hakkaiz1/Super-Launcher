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

package com.movtery.zalithlauncher.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Contraste WCAG entre duas cores, para os testes de UI.
 *
 * Fica num arquivo só porque tanto a paleta do tema quanto o botão Jogar
 * precisam da mesma conta — duplicar a fórmula em dois testes é exatamente o
 * tipo de coisa que diverge sem ninguém perceber.
 */

/** Razão de contraste WCAG entre [a] e [b], de 1.0 a 21.0. */
fun contrastRatio(a: Color, b: Color): Double {
    val la = relativeLuminance(a)
    val lb = relativeLuminance(b)
    return (max(la, lb) + 0.05) / (min(la, lb) + 0.05)
}

/** Falha o teste se [foreground] sobre [background] ficar abaixo de [min]. */
fun assertContrast(what: String, foreground: Color, background: Color, min: Double = 4.5) {
    val ratio = contrastRatio(foreground, background)
    assertTrue(
        "$what tem contraste %.2f:1, abaixo de %.1f:1 (%s sobre %s)".format(ratio, min, foreground, background),
        ratio >= min
    )
}

private fun relativeLuminance(color: Color): Double {
    fun channel(c: Float): Double {
        val v = c.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}