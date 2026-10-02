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

package com.movtery.zalithlauncher.game.account

import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * O estado "não-genuíno" do upstream é o que torna a conta offline impossível.
 *
 * Nele, o botão de adicionar conta ignora o menu e abre direto a autenticação
 * da Microsoft, e o launcher ainda zera a conta corrente — então mesmo uma
 * conta offline já salva não pode ser usada. Como o Super Launcher só oferece
 * conta Microsoft e conta offline, o primeiro usuário num aparelho novo
 * precisa poder criar a offline.
 *
 * A função é pura de propósito: `checkLimit()` original depende de arquivo,
 * locale e banco, e nenhum teste de JVM alcançaria esse gate.
 */
class DedicatedOfflineAccountTest {

    @Test
    fun `dedicated launcher is never in the non genuine state`() {
        for (circumventLimitExists in listOf(true, false)) {
            for (inGreaterChina in listOf(true, false)) {
                for (hasMicrosoftAccount in listOf(true, false)) {
                    assertFalse(
                        "circumventLimit=$circumventLimitExists greaterChina=$inGreaterChina " +
                            "contaMicrosoft=$hasMicrosoftAccount: o modo dedicado tem que aceitar " +
                            "conta offline, nunca travar no gate nao-genuino",
                        isNonGenuineState(
                            circumventLimitExists = circumventLimitExists,
                            inGreaterChina = inGreaterChina,
                            hasMicrosoftAccount = hasMicrosoftAccount
                        )
                    )
                }
            }
        }
    }
}
