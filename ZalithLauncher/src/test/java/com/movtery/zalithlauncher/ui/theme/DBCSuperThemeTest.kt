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

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Paleta de marca "DBC Super" (derivada da arte oficial do pack).
 *
 * Não é um teste de snapshot: ele fixa as propriedades que o olho não
 * percebe num hex errado — contraste mínimo dos pares que realmente são
 * lidos na tela, e ausência de papéis deixados no padrão (default
 * incorreto do Material).
 */
class DBCSuperThemeTest {

    @Test
    fun `dark scheme keeps readable contrast on the roles the launcher renders`() {
        assertContrast("primary/onPrimary", dbcSuperDark.primary, dbcSuperDark.onPrimary)
        assertContrast("primaryContainer/onPrimaryContainer", dbcSuperDark.primaryContainer, dbcSuperDark.onPrimaryContainer)
        assertContrast("surface/onSurface", dbcSuperDark.surface, dbcSuperDark.onSurface)
        assertContrast("surface/onSurfaceVariant", dbcSuperDark.surface, dbcSuperDark.onSurfaceVariant)
        assertContrast("surface/tertiary", dbcSuperDark.surface, dbcSuperDark.tertiary)
        assertContrast("surface/secondary", dbcSuperDark.surface, dbcSuperDark.secondary)
        assertContrast("surface/outline", dbcSuperDark.surface, dbcSuperDark.outline)
        assertContrast("error/onError", dbcSuperDark.error, dbcSuperDark.onError)
        assertContrast("primary/onPrimary", dbcSuperDark.primary, dbcSuperDark.onPrimary)
    }

    @Test
    fun `light scheme keeps readable contrast on the roles the launcher renders`() {
        assertContrast("primary/onPrimary", dbcSuperLight.primary, dbcSuperLight.onPrimary)
        assertContrast("primaryContainer/onPrimaryContainer", dbcSuperLight.primaryContainer, dbcSuperLight.onPrimaryContainer)
        assertContrast("surface/onSurface", dbcSuperLight.surface, dbcSuperLight.onSurface)
        assertContrast("surface/onSurfaceVariant", dbcSuperLight.surface, dbcSuperLight.onSurfaceVariant)
        assertContrast("surface/tertiary", dbcSuperLight.surface, dbcSuperLight.tertiary)
        assertContrast("surface/secondary", dbcSuperLight.surface, dbcSuperLight.secondary)
        assertContrast("surface/outline", dbcSuperLight.surface, dbcSuperLight.outline)
        assertContrast("error/onError", dbcSuperLight.error, dbcSuperLight.onError)
    }

    @Test
    fun `the play button label stays readable on both ends of its gradient`() {
        // O botão Jogar usa um gradiente primary → tertiary, e o rótulo usa
        // onPrimary. Isso só é seguro se onPrimary contraste com as DUAS pontas,
        // não só com a primary — é o que estes dois casos fixam.
        assertContrast("dark onPrimary/tertiary", dbcSuperDark.onPrimary, dbcSuperDark.tertiary)
        assertContrast("light onPrimary/tertiary", dbcSuperLight.onPrimary, dbcSuperLight.tertiary)
    }

    @Test
    fun `no brand role is left at the Material default`() {
        // Papéis deliberadamente fora desta lista, porque o valor correto deles
        // coincide com o baseline do Material: onPrimary/onSecondary/onTertiary/
        // onError (branco no claro), error/errorContainer/onErrorContainer (a
        // paleta não toca na família de erro, igual às 7 paletas existentes),
        // scrim (preto) e surfaceContainerLowest (branco no claro).
        val materialDefaults = mapOf(
            "primary" to Color(0xFF6750A4),
            "primaryContainer" to Color(0xFFEADDFF),
            "onPrimaryContainer" to Color(0xFF21005D),
            "secondary" to Color(0xFF625B71),
            "secondaryContainer" to Color(0xFFE8DEF8),
            "onSecondaryContainer" to Color(0xFF1D192B),
            "tertiary" to Color(0xFF7D5260),
            "tertiaryContainer" to Color(0xFFFFD8E4),
            "onTertiaryContainer" to Color(0xFF31111D),
            "background" to Color(0xFFFFFBFE),
            "onBackground" to Color(0xFF1C1B1F),
            "surface" to Color(0xFFFFFBFE),
            "onSurface" to Color(0xFF1C1B1F),
            "surfaceVariant" to Color(0xFFE7E0EC),
            "onSurfaceVariant" to Color(0xFF49454F),
            "outline" to Color(0xFF79747E),
            "outlineVariant" to Color(0xFFCAC4D0),
            "inverseSurface" to Color(0xFF313033),
            "inverseOnSurface" to Color(0xFFF4EFF4),
            "inversePrimary" to Color(0xFFD0BCFF),
            "surfaceContainerLow" to Color(0xFFF7F2FA),
            "surfaceContainer" to Color(0xFFF3EDF7),
            "surfaceContainerHigh" to Color(0xFFEDE7F1),
            "surfaceContainerHighest" to Color(0xFFE7E1EB),
            "surfaceDim" to Color(0xFFDED8E1),
            "surfaceBright" to Color(0xFFF7F2FA)
        )
        listOf("dark" to dbcSuperDark, "light" to dbcSuperLight).forEach { (mode, scheme) ->
            materialDefaults.forEach { (name, materialDefault) ->
                val actual = role(scheme, name)
                assertTrue(
                    "$mode: '$name' ficou no padrão do Material ($materialDefault)",
                    actual != materialDefault
                )
            }
        }
    }

    private fun role(scheme: ColorScheme, name: String): Color = when (name) {
        "primary" -> scheme.primary
        "onPrimary" -> scheme.onPrimary
        "primaryContainer" -> scheme.primaryContainer
        "onPrimaryContainer" -> scheme.onPrimaryContainer
        "secondary" -> scheme.secondary
        "onSecondary" -> scheme.onSecondary
        "secondaryContainer" -> scheme.secondaryContainer
        "onSecondaryContainer" -> scheme.onSecondaryContainer
        "tertiary" -> scheme.tertiary
        "onTertiary" -> scheme.onTertiary
        "tertiaryContainer" -> scheme.tertiaryContainer
        "onTertiaryContainer" -> scheme.onTertiaryContainer
        "background" -> scheme.background
        "onBackground" -> scheme.onBackground
        "surface" -> scheme.surface
        "onSurface" -> scheme.onSurface
        "surfaceVariant" -> scheme.surfaceVariant
        "onSurfaceVariant" -> scheme.onSurfaceVariant
        "outline" -> scheme.outline
        "outlineVariant" -> scheme.outlineVariant
        "inverseSurface" -> scheme.inverseSurface
        "inverseOnSurface" -> scheme.inverseOnSurface
        "inversePrimary" -> scheme.inversePrimary
        "surfaceContainerLowest" -> scheme.surfaceContainerLowest
        "surfaceContainerLow" -> scheme.surfaceContainerLow
        "surfaceContainer" -> scheme.surfaceContainer
        "surfaceContainerHigh" -> scheme.surfaceContainerHigh
        "surfaceContainerHighest" -> scheme.surfaceContainerHighest
        "surfaceDim" -> scheme.surfaceDim
        "surfaceBright" -> scheme.surfaceBright
        else -> error("papel desconhecido: $name")
    }

}
