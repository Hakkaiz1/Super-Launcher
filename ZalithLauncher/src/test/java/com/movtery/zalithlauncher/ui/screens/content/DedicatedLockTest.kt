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

package com.movtery.zalithlauncher.ui.screens.content

import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.ui.screens.NestedNavKey
import com.movtery.zalithlauncher.ui.screens.content.home.HomeCards
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DedicatedLockTest {

    @Test
    fun `dedicated mode blocks the download funnel`() {
        assertTrue("o build de teste precisa ser dedicado", BuildKeys.DEDICATED_MODE)

        val viewModel = ScreenBackStackViewModel()
        viewModel.navigateToDownload()

        assertFalse(
            "navigateToDownload deveria ser no-op no modo dedicado",
            viewModel.mainScreen.currentKey is NestedNavKey.Download
        )
        // currentKey só é espelhado em composição (MainScreen NavigationUI); em teste de unidade
        // o efeito real da navegação é o backStack — é ele que o gate precisa impedir.
        assertFalse(
            "navigateToDownload não deveria empilhar a tela de download no modo dedicado",
            viewModel.mainScreen.backStack.any { it is NestedNavKey.Download }
        )
    }

    @Test
    fun `dedicated mode hides the home cards`() {
        assertTrue(BuildKeys.DEDICATED_MODE)
        assertTrue("nenhum card navegável na home dedicada", HomeCards.userCardTypes.isEmpty())
    }
}
