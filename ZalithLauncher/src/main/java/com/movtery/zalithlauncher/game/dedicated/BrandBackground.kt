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

import java.io.File
import java.io.InputStream

/**
 * Semeamento da imagem de fundo da marca.
 *
 * O launcher já tem todo o apparatus de fundo (imagem, blur, opacidade,
 * vidro); o que faltava era uma fonte além da galeria do jogador. Em vez de
 * criar um caminho paralelo, a arte da marca é gravada uma única vez no mesmo
 * arquivo que o [com.movtery.zalithlauncher.viewmodel.BackgroundViewModel]
 * já lê — a partir daí, blur, opacidade e as configurações existentes passam
 * a valer sem nenhuma linha nova de renderização.
 */

/**
 * A imagem da marca deve ser instalada agora?
 *
 * [alreadyInstalled] é o registro de que ela já foi semeada uma vez. Sem ele,
 * um simples "o arquivo não existe" faria a imagem reaparecer sozinha depois
 * que o jogador usasse "Resetar" nas configurações — tirando do botão a única
 * função dele.
 */
internal fun shouldInstallBrandBackground(
    backgroundFile: File,
    alreadyInstalled: Boolean
): Boolean = !alreadyInstalled && !backgroundFile.exists()

/**
 * Copia a imagem para o arquivo de fundo do launcher.
 *
 * Uma cópia interrompida não pode deixar um arquivo pela metade, senão o
 * BackgroundViewModel pode passar a enxergar algo que não é imagem; por isso
 * a escrita vai para um arquivo temporário e só então substitui o destino.
 *
 * @return true se a imagem foi gravada com sucesso.
 */
internal fun installBrandBackground(source: InputStream, target: File): Boolean {
    val temp = File(target.parentFile, "${target.name}.brand-tmp")
    return try {
        target.parentFile?.mkdirs()
        temp.outputStream().use { output -> source.copyTo(output) }

        if (temp.length() <= 0) {
            temp.delete()
            false
        } else {
            if (target.exists()) target.delete()
            if (temp.renameTo(target)) {
                true
            } else {
                // Renomear pode falhar em alguns sistemas de arquivos; a cópia
                // direta ainda é melhor do que nada.
                temp.copyTo(target, overwrite = true)
                temp.delete()
                target.length() > 0
            }
        }
    } catch (e: Exception) {
        temp.delete()
        false
    }
}