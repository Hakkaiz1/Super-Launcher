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

package com.movtery.zalithlauncher.game.download.modpack.technic

import com.movtery.zalithlauncher.path.DOWNLOAD_OKHTTP_CLIENT
import com.movtery.zalithlauncher.path.GLOBAL_JSON
import com.movtery.zalithlauncher.path.createRequestBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import java.io.IOException

@Serializable
data class TechnicIcon(val url: String? = null)

/** Resposta da Platform API no modo zip — fixture da spec §4. */
@Serializable
data class TechnicPack(
    val displayName: String? = null,
    val minecraft: String? = null,
    val version: String? = null,
    val url: String? = null,
    val solder: String? = null,
    val icon: TechnicIcon? = null
)

object TechnicApi {
    /** Número de build aceito pela Platform; desatualizado = HTTP 401 (spec §4). */
    const val TECHNIC_API_BUILD = 1166
    const val BASE_URL = "https://api.technicpack.net"

    /** User-Agent de navegador exigido pelo Dropbox para o zip do pack (spec §5.5/§10). */
    const val BROWSER_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    fun packUrl(slug: String, baseUrl: String = BASE_URL): String =
        "$baseUrl/modpack/$slug?build=$TECHNIC_API_BUILD"

    data class RawResponse(val code: Int, val body: String)

    class BuildRejectedException :
        IOException("Technic Platform rejected build $TECHNIC_API_BUILD (HTTP 401)")
    class MalformedResponseException(cause: Throwable) :
        IOException("Malformed Technic Platform response", cause)
    class PackUnavailableException(val code: Int) :
        IOException("Technic Platform HTTP $code")

    /** Etapa 1 (spec §5.2): baixa e parseia o pack. Roda em IO — chamado de coroutines. */
    suspend fun getPack(slug: String): TechnicPack = withContext(Dispatchers.IO) {
        getPack(packUrl(slug)) { url -> httpFetch(url) }
    }

    /** Seam sem rede: o teste injeta a resposta HTTP. */
    internal fun getPack(url: String, fetch: (String) -> RawResponse): TechnicPack {
        val response = fetch(url)
        return when {
            response.code == 200 -> parsePack(response.body)
            response.code == 401 -> throw BuildRejectedException()
            else -> throw PackUnavailableException(response.code)
        }
    }

    internal fun parsePack(body: String): TechnicPack = runCatching {
        GLOBAL_JSON.decodeFromString<TechnicPack>(body)
    }.getOrElse { e -> throw MalformedResponseException(e) }

    private fun httpFetch(url: String): RawResponse =
        DOWNLOAD_OKHTTP_CLIENT.newCall(createRequestBuilder(url).build()).execute().use { resp ->
            RawResponse(resp.code, resp.body.string())
        }
}
