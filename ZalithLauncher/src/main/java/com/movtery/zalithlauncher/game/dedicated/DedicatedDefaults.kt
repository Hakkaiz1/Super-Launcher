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

/**
 * Configuração que o launcher dedicado entrega já pronta.
 *
 * Fica em funções e constantes puras (sem Android nem MMKV) para que os
 * defaults sejam testáveis: eles são gravados no primeiro boot, e um valor
 * errado aqui só apareceria como "o jogo não sobe", muito longe da causa.
 */

/** RAM em MB. A heurística do Pojav seria usada aqui sem o modo dedicado. */
internal const val DEDICATED_RAM_ALLOCATION = 4096

/**
 * Argumentos de JVM afinados para o Minecraft 1.7.10 com Forge.
 *
 * Todos existem no Java 8 (o runtime do pack): um flag de Java 11+ faria o
 * jogo sair com "Unrecognized VM option". Verificados no aparelho, no
 * OpenJDK 1.8.0_442 do próprio app, com `-Xmx4096M`.
 *
 * **A ordem é load-bearing.** No Java 8, G1NewSizePercent, G1MaxNewSizePercent
 * e G1ReservePercent são experimentais e só são aceitos *depois* de
 * -XX:+UnlockExperimentalVMOptions, porque o HotSpot lê os argumentos na
 * ordem em que aparecem. Inverter a ordem faz o jogo morrer com
 * "VM option 'G1NewSizePercent' is experimental and must be enabled via
 * -XX:+UnlockExperimentalVMOptions". O DedicatedDefaultsTest trava isso.
 */
internal val dedicatedJvmArgs: String = listOf(
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
).joinToString(" ")

/** Escala de render em %: 75% mantém a nitidez do jogo e pesa menos na GPU. */
internal const val DEDICATED_RESOLUTION_RATIO = 75

/**
 * A regra precisa ser PERCENTAGE para a escala valer; com CUSTOM o valor é
 * ignorado e o jogo usaria a resolução real da tela.
 */
internal val DEDICATED_RESOLUTION_RULE = ResolutionRule.PERCENTAGE

/** Mantém o clock alto durante a sessão, evitando o downclock térmico do aparelho. */
internal const val DEDICATED_SUSTAINED_PERFORMANCE = true

/**
 * Flag do LWJGL que o SettingsInitializer arranca dos argumentos do jogador
 * porque escolhe o renderizador errado. Reproduzido aqui para o teste garantir
 * que o default da marca não o carrega de volta.
 */
internal const val LWJGL_OPENGL_LIB_NAME_ARG = "-Dorg.lwjgl.opengl.libname="