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

import com.movtery.zalithlauncher.setting.enums.ResolutionRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Chave dos flags experimentais no Java 8; precisa vir antes de usá-los. */
private const val UNLOCK_EXPERIMENTAL_ARG = "-XX:+UnlockExperimentalVMOptions"

/**
 * Defaults de configuração que o modo dedicado entrega pronto.
 *
 * Estão isolados em funções puras de propósito: os valores são gravados no
 * MMKV no primeiro boot, e um default errado aqui não aparece em nenhum teste
 * de UI — só quando o jogo sobe com RAM/JVM errados. O teste abaixo fixa os
 * valores e, principalmente, a persistência deles.
 */
class DedicatedDefaultsTest {

    @Test
    fun `dedicated jvm args are the tuned g1gc set`() {
        val args = dedicatedJvmArgs

        assertTrue(
            "os argumentos de JVM da marca devem usar o G1GC",
            args.contains("-XX:+UseG1GC")
        )
        // Todos precisam existir no Java 8 (o runtime do pack); um flag de
        // Java 11+ faria o jogo não iniciar com "Unrecognized VM option".
        for (flag in listOf(
            "-XX:+UseG1GC",
            "-XX:+ParallelRefProcEnabled",
            "-XX:MaxGCPauseMillis=200",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+DisableExplicitGC",
            "-XX:+AlwaysPreTouch",
            "-XX:G1NewSizePercent=30",
            "-XX:G1MaxNewSizePercent=40",
            "-XX:G1ReservePercent=20",
            "-XX:G1HeapRegionSize=8M"
        )) {
            assertTrue("falta o argumento $flag", args.contains(flag))
        }
    }

    @Test
    fun `dedicated jvm args never carry the lwjgl opengl flag`() {
        // SettingsInitializer remove esse flag do usuário porque ele quebra o
        // renderizador; gravá-lo no default seria ressuscitá-lo.
        assertEquals(
            "o default não pode conter o flag de OpenGL do LWJGL",
            false,
            dedicatedJvmArgs.contains(LWJGL_OPENGL_LIB_NAME_ARG)
        )
    }

    @Test
    fun `dedicated jvm args unlock the experimental options before using them`() {
        // Verificado no aparelho (OpenJDK 1.8.0_442): o HotSpot lê os args na
        // ordem e recusa os flags experimentais que ainda estão trancados.
        val args = dedicatedJvmArgs
        val unlock = args.indexOf(UNLOCK_EXPERIMENTAL_ARG)
        assertTrue("falta o $UNLOCK_EXPERIMENTAL_ARG", unlock >= 0)

        for (flag in listOf(
            "-XX:G1NewSizePercent",
            "-XX:G1MaxNewSizePercent",
            "-XX:G1ReservePercent"
        )) {
            val at = args.indexOf(flag)
            assertTrue("falta o argumento $flag", at >= 0)
            assertTrue(
                "$flag precisa vir depois de $UNLOCK_EXPERIMENTAL_ARG, " +
                    "senao o Java 8 recusa o jogo inteiro",
                at > unlock
            )
        }
    }

    @Test
    fun `dedicated ram allocation is 4096`() {
        assertEquals(4096, DEDICATED_RAM_ALLOCATION)
    }

    @Test
    fun `dedicated render scale is 75 percent`() {
        assertEquals(75, DEDICATED_RESOLUTION_RATIO)
    }

    @Test
    fun `dedicated resolution rule stays on percentage`() {
        // 75% só faz sentido com a regra percentual; com CUSTOM o valor é
        // ignorado e a resolução viraria a da tela.
        assertEquals(ResolutionRule.PERCENTAGE, DEDICATED_RESOLUTION_RULE)
    }

    @Test
    fun `dedicated sustained performance is enabled`() {
        assertTrue(DEDICATED_SUSTAINED_PERFORMANCE)
    }
}