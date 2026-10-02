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

/** Raiz dos mods dentro do zip e dentro da versao instalada. */
private const val MODS_PREFIX = "mods/"

/**
 * O zip de mods nao e um zip (o primeiro link era um RAR 5 renomeado) e a URL
 * pode devolver a pagina de preview do Dropbox em vez do arquivo.
 */
class ModsArchiveInvalidException(message: String) : RuntimeException(message)

/** URL vazia = passo desligado; e assim que o download do Dropbox sai de cena. */
internal fun isModsDownloadDisabled(url: String): Boolean = url.isBlank()

/**
 * Entradas de arquivo do zip que pertencem a `mods/`, mantendo o prefixo.
 *
 * O prefixo fica de proposito: os caminhos sao relativos a pasta da versao, a
 * mesma coisa que [staleModFiles] recebe ao varrer `versions/<slug>/`. Com as
 * duas pontas na mesma convencao, [isModPath] e um guard de verdade - se os
 * caminhos viessem relativos a `mods/`, nada seria filtrado e a protecao do
 * `saves/` seria decorativa.
 *
 * Pastas e qualquer coisa fora de `mods/` sao descartadas: o zip e soberano
 * sobre `mods/`, nao sobre o jogo inteiro.
 */
internal fun modsEntriesIn(entryNames: List<String>): List<String> =
    entryNames.asSequence()
        .map { it.replace('\\', '/') }
        .filter { isModPath(it) && !it.endsWith("/") }
        .distinct()
        .toList()

/**
 * Mesma selecao, mas recusando o arquivo quando nao ha mod nenhum.
 *
 * Sem essa trava, uma URL errada (ou um zip vazio) apagaria todos os mods do
 * pack e o jogo ficaria sem nada para carregar - falha silenciosa e cara.
 */
internal fun requireModsEntries(entryNames: List<String>): List<String> {
    val entries = modsEntriesIn(entryNames)
    if (entries.isEmpty()) {
        throw ModsArchiveInvalidException(
            "The mods archive has no mods/ entries, refusing to touch mods/. " +
                "Check that the URL is a direct download link (dl=1) and that the file is a real zip."
        )
    }
    return entries
}

/** `true` so para caminho dentro de `mods/` - `saves/`, `options.txt` e `config/` ficam de fora. */
internal fun isModPath(relativePath: String): Boolean {
    val path = relativePath.replace('\\', '/')
    return path == "mods" || path.startsWith(MODS_PREFIX)
}

/**
 * Mods instalados que o zip nao traz: sao removidos.
 *
 * Isto e o que evita mods duplicados. Quatro mods do pack mudaram de nome de
 * arquivo no zip (UniMixins 0.1.17→0.3.2, CodeChickenCore, CustomNPC+,
 * dbcsuperitem); so sobrescrever deixaria as duas copias lado a lado e o FML
 * 1.7.10 aborta ao carregar.
 */
internal fun staleModFiles(existing: List<String>, incoming: List<String>): List<String> {
    val kept = incoming.toSet()
    return existing.asSequence()
        .filter { isModPath(it) }
        .filter { it !in kept }
        .distinct()
        .sorted()
        .toList()
}

/**
 * Assinatura local de arquivo zip (`PK\x03\x04`).
 *
 * O extrator do launcher so entende zip; um RAR com nome de `.zip` - que foi
 * exatamente o primeiro link - falharia so no meio da aplicacao, com metade dos
 * mods ja trocada.
 */
internal fun isZipArchive(head: ByteArray): Boolean =
    head.size >= 4 &&
        head[0] == 0x50.toByte() && head[1] == 0x4B.toByte() &&
        head[2] == 0x03.toByte() && head[3] == 0x04.toByte()