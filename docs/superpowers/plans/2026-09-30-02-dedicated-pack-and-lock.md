# Super Launcher — Instalação do Pack Dedicado e Travamento da Navegação — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar as Fases 2–5 da spec: cliente Technic, instalador do pack com manifesto, semeamento (servidor/controle/version.config), máquina de estados, tela dedicada e travamento total da navegação — o launcher fica preso ao pack `dbc-super-oficial`.

**Architecture:** Sete arquivos novos sob `game/download/modpack/technic/` (cliente + instalador das 8 etapas da §5.2), `game/dedicated/` (manifesto, seeder, máquina de estados) e `ui/screens/content/dedicated/` (tela única). O instalador reaproveita sem modificar `GameInstaller` (base 1.7.10+Forge pelo caminho legado), `DownloadEngine` (só ganha um User-Agent opcional) e `TaskFlowExecutor`; o travamento é um conjunto de gates `BuildKeys.DEDICATED_MODE` nos pontos de navegação existentes. Toda lógica pura (fixtures da API, manifesto, diff de update, classificação de estados) é testada em JUnit4/JVM; o que exige Android só entra depois das partes puras verdes.

**Tech Stack:** Kotlin, Jetpack Compose (Material 3), kotlinx.serialization, OkHttp + `DownloadEngine` (HttpURLConnection por baixo), Gradle `buildKeys {}`, JUnit4 + `runBlocking`.

**Spec:** `docs/superpowers/specs/2026-09-29-super-launcher-dedicated-design.md` — este plano implementa **Fases 2, 3, 4 e 5** (§5.1, §5.2, §5.3, §6, §7, §9). A Fase 6 (§5.5 polimento de erros + testes manuais 2–5) fica para o Plano 3. O plano argumenta a partir da spec; executor deve ler a spec junto.

## Global Constraints

- **R1 — travado no pack:** sem lista de versões, sem telas de download, sem navegação de plataformas; entradas ficam *escondidas **e** guardadas* (§6) — todos os gates abaixo são obrigatórios, não opcionais.
- **Aviso de licença:** string exata `Unofficial Modified Version by Hakkaiz`, `unofficial_modified_notice` com `translatable="false"`, exibido **incondicionalmente** — na home (systemCards, Fase 1) **e** dentro do `DedicatedScreen`.
- **Identidade:** `launcher_name=SuperLauncher`, `launcher_app_name=Super Launcher`, `launcher_short_name=SL` (sem "ZL").
- **Runtime:** só Minecraft 1.7.10 + Forge `10.13.4.1558` → só Java 8; nunca reintroduzir JRE 17/21/25.
- **Licença:** cabeçalhos GPLv3 do projeto nunca removidos; **todo arquivo novo** começa com este cabeçalho (verbatim, mesmo dos existentes):
  ```kotlin
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
  ```
- **Integridade:** `skipGameIntegrityCheck` permanece **false** — o seeder grava `SettingState.DISABLE` (nunca `ENABLE`).
- **BuildKeys (build.gradle.kts:220-224, consumir como `BuildKeys.*`):** `DEDICATED_MODE=true`, `DEDICATED_PACK_SLUG="dbc-super-oficial"`, `DEDICATED_SERVER_NAME="Minecraft Server"`, `DEDICATED_SERVER_IP="dbcsuper.com"`.
- **`TECHNIC_API_BUILD = 1166`:** com build diferente a API do Technic responde **401** (spec §4) — o 401 vira erro explícito, nunca silencioso.
- **Manifesto:** `versions/<slug>/<LAUNCHER_IDENTIFIER>/pack-manifest.json` (ao lado do `version.config`), gravação **atômica** e escrita **por último** (pós-semer; crash sem manifesto ⇒ volta a `NOT_INSTALLED`/`NEEDS_UPDATE`).
- **Regras do overlay (§5.2 etapa 6, §5.3):** nunca copiar `bin/`; em update nunca sobrescrever `saves/`, `options.txt`, `servers.dat`; o manifesto nunca lista `bin/` nem protegidos; na **primeira instalação** protegidos são copiados normalmente.
- **Ordem obrigatória (§5.1):** `PathManager.refreshPaths(context)` → `GamePathManager.reloadPath()` / `waitForRefresh()` → `VersionsManager.waitForRefresh()` → ler manifesto / semear / `saveCurrentVersion()`.
- **Identidade do pack (§5.1):** igualdade de string sobre `version`; se vazio → campo `url`; se ambos vazios → log de aviso + `READY` (**nunca** loop de atualização).
- **Seeder (§7.1):** nunca remove entradas de `servers.dat`; nunca reescreve o arquivo quando nada mudou (atualizações).
- **Strings novas:** inglês em `ZalithLauncher/src/main/res/values/strings.xml` + pt-BR em `res/values-pt-rBR/strings.xml` (arquivo existe).
- **Testes:** JUnit4 com imports explícitos (`org.junit.Test`, `org.junit.Assert.*`), `runBlocking` para suspend, temp dirs via `Files.createTempDirectory(...)` + `finally { dir.deleteRecursively() }`, **sem** `kotlinx-coroutines-test`. Cada arquivo de teste novo carrega o cabeçalho GPLv3 acima.
- **Comandos** (sempre pela junction, o `ndk-build` não aceita espaços):
  - testes completos: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain` (~3 min)
  - teste específico: mesma linha com `--tests "*NomeDaClasse*"`
  - build: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:assembleDebug --console=plain`
- **Gate por task:** suíte **completa verde** antes de cada `git commit`; commits convencionais; **nenhum push** até a Task 13 (push único ao final, `git push origin main`).

## Review Focus

Cinco classes de entrada/falha que a spec implica mas que nenhum teste cobre diretamente — cada linha tem o teste que a prende à task dona do código:

1. **Zip de 90 MB no Dropbox, sem SHA-1, download lento:** se alguém trocar para `downloadFileFromSources`, o timeout global de 5 min (`DOWNLOAD_SOURCES_TIMEOUT`) mata o download no meio; e o Dropbox pode bloquear UA de launcher (§5.5 pede User-Agent de navegador + redirect). *Esperado:* `sha1=null`, `DownloadEngine.download` direto (`expectedSize=-1`), UA de navegador nas requests. → Testes na **Task 4**: `pack request goes straight to the DownloadEngine` + os dois testes de UA novos no `FetcherHttpTest`.
2. **Atualização toca dados do jogador:** `saves/`, `options.txt`, `servers.dat` não podem ser sobrescritos nem entrar na lista de remoção — mesmo que um manifesto antigo os liste (§5.3 regra 4). *Esperado:* bytes do jogador intactos após update; órfãos protegidos nunca apagados. → Testes: **Task 6** `update never overwrites player data and removes orphan mods` (lado da sobrescrita) + **Task 3** `protected paths never appear in the orphan list` (lado da remoção).
3. **`bin/` (DLLs Windows) vaza para o overlay:** o zip traz `bin/natives/*.dll` inúteis no Android (§4/§10). *Esperado:* `bin/` nunca copiado e nunca listado no manifesto. → Testes na **Task 3** (`pack files exclude bin and player data`) e **Task 6** (`overlay excludes bin even on first install`).
4. **Estado fantasma / loop de update:** manifesto existe mas o `versions/<slug>/<slug>.json` foi apagado pelo jogador → launcher ficaria `READY` e travaria sem saída (lock!); API com identidade vazia não pode gerar `NEEDS_UPDATE` infinito (§5.1). *Esperado:* json ausente ⇒ `NOT_INSTALLED`; identidade nula ⇒ `READY` + log. → Testes na **Task 9**: `deleted version json is not installed` e `empty api identity stays ready`.
5. **401 ≠ offline:** rejeição de build da API (build desatualizado) precisa virar `FAILED` com mensagem explícita; queda de rede vira `OFFLINE` com "Tentar de novo" — nunca silencioso, nunca trocados (§5.5). *Esperado:* `BuildRejectedException` → `API_BUILD_REJECTED`; `UnknownHostException` (mesmo dentro de `AllSourcesFailedException`) → `OFFLINE`. → Testes na **Task 9**: `errors are classified distinctly`.

---

## File Structure

**Criados:**

| Arquivo | Responsabilidade |
|---|---|
| `game/download/modpack/technic/TechnicApi.kt` | Cliente da Platform API (§5.2 etapa 1): DTOs, `getPack`, erro de build (401), `BROWSER_USER_AGENT` |
| `game/download/modpack/technic/TechnicPackVersionResolver.kt` | Resolve `1.7.10` + Forge `10.13.4.1558` do staging (§5.2 etapa 4) |
| `game/download/modpack/technic/TechnicPackInstaller.kt` | As 8 etapas da §5.2: download, extract, resolve, base, overlay, seed, select |
| `game/dedicated/PackManifest.kt` | `pack-manifest.json`: leitura/escrita atômica, `packFiles`, `orphans`, `isProtected`, `identityOf` (§5.1/§5.3) |
| `game/dedicated/DedicatedSeeder.kt` | Servidor em `servers.dat` + `version.config` (§7.1/§7.3) |
| `game/dedicated/DedicatedPackState.kt` | Máquina de estados `PackState` + `check()`/`install()` (§3/§5.1) |
| `ui/screens/content/dedicated/DedicatedScreen.kt` | Tela única: ícone, nome, aviso, Instalar/Atualizar/Jogar/Offline (§6) |

**Modificados:**

| Arquivo | Mudança |
|---|---|
| `path/PathManager.kt` | `DIR_CACHE_DEDICATED_PACK = File(DIR_CACHE, "temp_dedicated_pack")` |
| `game/download/engine/DownloadRequest.kt` | `userAgent: String? = null` (default preserva comportamento atual) |
| `game/download/engine/FileDownloader.kt` | repassa `request.userAgent` ao `Fetcher` |
| `game/download/engine/Fetcher.kt` | threading de `userAgent` até `openConnection` |
| `game/control/ControlManager.kt` | `ensureDefaultLayout(context)` + seam `ensureLayoutFile` (§7.2) |
| `res/values/strings.xml` + `res/values-pt-rBR/strings.xml` | todas as strings novas do dedicado |
| `ui/screens/main/MainScreen.kt` | `LauncherMain` renderiza `DedicatedScreen`; TopBar sem Download/Multiplayer/pasta |
| `ui/screens/content/DownloadScreen.kt` | `navigateToDownload()` vira no-op no modo dedicado |
| `ui/screens/content/elements/LauncherElements.kt` | `NoVersion` não navega para o gerenciador |
| `ui/activities/MainActivity.kt` | gate em `toVersionManageScreen` + `DedicatedPackState.launchCheck()` na abertura |
| `ui/screens/content/home/HomeCards.kt` | `userCardTypes` vazio no modo dedicado |

**Testes criados:** `technic/TechnicApiTest.kt`, `technic/TechnicPackVersionResolverTest.kt`, `technic/TechnicPackInstallerTest.kt`, `dedicated/PackManifestTest.kt`, `dedicated/DedicatedSeederTest.kt`, `dedicated/DedicatedPackStateTest.kt`, `game/control/ControlManagerSeedTest.kt`, `ui/screens/content/DedicatedLockTest.kt`. **Modificado:** `engine/FetcherHttpTest.kt` (+2 testes de UA).

Ordem das tasks: cada uma só depende de números anteriores; os blocos `Interfaces` dizem exatamente o que a próxima task consome.

---

### Task 1: `TechnicApi` — cliente da Technic Platform

**Files:**
- Create: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicApi.kt`
- Test: `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicApiTest.kt`

**Interfaces:**
- Consumes: `com.movtery.zalithlauncher.path.GLOBAL_JSON` (UrlManager.kt:110), `DOWNLOAD_OKHTTP_CLIENT` (UrlManager.kt:175), `createRequestBuilder(url)` (UrlManager.kt:143).
- Produces: `TechnicApi.getPack(slug: String): TechnicPack` (suspend); `TechnicPack(displayName, minecraft, version, url, solder, icon: TechnicIcon?)`; `TechnicApi.packUrl(slug, baseUrl): String`; `TechnicApi.BROWSER_USER_AGENT: String`; exceções `TechnicApi.BuildRejectedException`, `MalformedResponseException`, `PackUnavailableException`; `internal fun getPack(url, fetch: (String) -> RawResponse)` para teste sem rede.

- [ ] **Step 1: Escrever o teste falhando**

```kotlin
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

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TechnicApiTest {

    /** Fixture da spec §4 (dados verificados em 2026-09-29), com campo extra para ignorar desconhecidos. */
    private val zipFixture = """
        {
          "displayName": "DBC Super (Oficial)",
          "minecraft": "1.7.10",
          "version": "10.8",
          "url": "https://www.dropbox.com/scl/fi/example/ATT58.zip?rlkey=abc&dl=1",
          "solder": null,
          "icon": { "url": "https://cdn.technicpack.net/platform2/pack-icons/1132904.png" },
          "unknownFutureField": { "ignored": true }
        }
    """.trimIndent()

    private fun fetch(body: String, code: Int = 200) =
        { _: String -> TechnicApi.RawResponse(code, body) }

    @Test
    fun `zip mode fixture parses all fields and ignores unknown ones`() {
        val pack = TechnicApi.getPack("https://api.technicpack.net/modpack/x", fetch(zipFixture))
        assertEquals("DBC Super (Oficial)", pack.displayName)
        assertEquals("1.7.10", pack.minecraft)
        assertEquals("10.8", pack.version)
        assertEquals("https://www.dropbox.com/scl/fi/example/ATT58.zip?rlkey=abc&dl=1", pack.url)
        assertNull(pack.solder)
        assertEquals("https://cdn.technicpack.net/platform2/pack-icons/1132904.png", pack.icon?.url)
    }

    @Test
    fun `explicit solder null parses as null`() {
        val pack = TechnicApi.getPack("https://api.technicpack.net/modpack/x",
            fetch("""{"minecraft":"1.7.10","solder":null}"""))
        assertNull(pack.solder)
        assertEquals("1.7.10", pack.minecraft)
    }

    @Test(expected = TechnicApi.BuildRejectedException::class)
    fun `http 401 rejects the launcher build`() {
        TechnicApi.getPack("https://api.technicpack.net/modpack/x",
            fetch("""{"code":401}""", code = 401))
    }

    @Test(expected = TechnicApi.PackUnavailableException::class)
    fun `http 503 surfaces as pack unavailable`() {
        TechnicApi.getPack("https://api.technicpack.net/modpack/x",
            fetch("maintenance", code = 503))
    }

    @Test(expected = TechnicApi.MalformedResponseException::class)
    fun `malformed json throws malformed response`() {
        TechnicApi.getPack("https://api.technicpack.net/modpack/x", fetch("not-json{{"))
    }

    @Test
    fun `pack url embeds the current launcher build`() {
        val url = TechnicApi.packUrl("dbc-super-oficial")
        assertTrue(url.startsWith("https://api.technicpack.net/modpack/dbc-super-oficial"))
        assertTrue(url.contains("build=1166"))
    }
}
```

- [ ] **Step 2: Rodar para ver falhar**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*TechnicApiTest*" --console=plain`
Expected: **FAIL** no compile — `unresolved reference: TechnicApi` (arquivo ainda não existe).

- [ ] **Step 3: Implementar o `TechnicApi`**

```kotlin
/* (cabeçalho GPLv3 completo, igual ao bloco de Global Constraints) */

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
            RawResponse(resp.code, resp.body?.string() ?: "")
        }
}
```

- [ ] **Step 4: Rodar para ver passar**

Run: mesmo comando do Step 2.
Expected: **PASS** — 6 testes verdes.

- [ ] **Step 5: Suíte completa + commit**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain`
Expected: **BUILD SUCCESSFUL** (suíte inteira verde, 101+ testes).

```bash
git add ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicApi.kt ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicApiTest.kt
git commit -m "feat(technic): add Technic Platform API client with build-rejection handling"
```

---

### Task 2: `TechnicPackVersionResolver` — resolvedor de versão do staging

**Files:**
- Create: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackVersionResolver.kt`
- Test: `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackVersionResolverTest.kt`

**Interfaces:**
- Consumes: apenas stdlib (`kotlinx.serialization.json`, `java.util.zip.ZipFile`).
- Produces: `data class PackVersionInfo(minecraft: String, forgeBuild: String?)`; `TechnicPackVersionResolver.resolve(stagingDir: File, apiMinecraft: String?): PackVersionInfo`; exceção `PackVersionUnresolvableException`; `internal fun parseVersionJson(json: String): PackVersionInfo?`.

- [ ] **Step 1: Escrever o teste falhando**

```kotlin
/* (cabeçalho GPLv3 completo) */

package com.movtery.zalithlauncher.game.download.modpack.technic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class TechnicPackVersionResolverTest {

    /** Fixture da spec §4: bin/version.json do pack real. */
    private val versionJson = """
        { "id": "1.7.10-Forge10.13.4.1558-1.7.10",
          "inheritsFrom": "1.7.10",
          "libraries": ["net.minecraftforge:forge:1.7.10-10.13.4.1558-1.7.10", "org.lwjgl.lwjgl:lwjgl:2.9.4"] }
    """.trimIndent()

    private fun staging(): File = Files.createTempDirectory("resolver-staging").toFile()

    @Test
    fun `reads bin version json`() {
        val dir = staging()
        try {
            File(dir, "bin").mkdirs()
            File(dir, "bin/version.json").writeText(versionJson)

            val info = TechnicPackVersionResolver.resolve(dir, apiMinecraft = null)

            assertEquals(PackVersionInfo("1.7.10", "10.13.4.1558"), info)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `falls back to version json inside modpack jar`() {
        val dir = staging()
        try {
            File(dir, "bin").mkdirs()
            ZipOutputStream(FileOutputStream(File(dir, "bin/modpack.jar"))).use { zos ->
                zos.putNextEntry(ZipEntry("version.json"))
                zos.write(versionJson.toByteArray())
                zos.closeEntry()
            }

            val info = TechnicPackVersionResolver.resolve(dir, apiMinecraft = null)

            assertEquals(PackVersionInfo("1.7.10", "10.13.4.1558"), info)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `falls back to the api minecraft field`() {
        val dir = staging()
        try {
            val info = TechnicPackVersionResolver.resolve(dir, apiMinecraft = "1.7.10")

            assertEquals(PackVersionInfo("1.7.10", null), info)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `libraries declared as objects still resolve the forge build`() {
        val json = """
            { "id": "1.7.10-Forge10.13.4.1558-1.7.10",
              "inheritsFrom": "1.7.10",
              "libraries": [{"name": "net.minecraftforge:forge:1.7.10-10.13.4.1558-1.7.10"}] }
        """.trimIndent()
        val dir = stagingWith(json)
        try {
            assertEquals(PackVersionInfo("1.7.10", "10.13.4.1558"),
                TechnicPackVersionResolver.resolve(dir, apiMinecraft = null))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `forge build falls back to the version id`() {
        val json = """
            { "id": "1.7.10-Forge10.13.4.1558-1.7.10",
              "inheritsFrom": "1.7.10",
              "libraries": ["org.lwjgl.lwjgl:lwjgl:2.9.4"] }
        """.trimIndent()
        val dir = stagingWith(json)
        try {
            assertEquals(PackVersionInfo("1.7.10", "10.13.4.1558"),
                TechnicPackVersionResolver.resolve(dir, apiMinecraft = null))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test(expected = PackVersionUnresolvableException::class)
    fun `throws when there is no version source at all`() {
        val dir = staging()
        try {
            TechnicPackVersionResolver.resolve(dir, apiMinecraft = null)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `api fallback leaves forge unknown`() {
        val dir = staging()
        try {
            assertNull(TechnicPackVersionResolver.resolve(dir, "1.7.10").forgeBuild)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Monta um staging só com bin/version.json para testar o parser puro. */
    private fun stagingWith(json: String): File {
        val dir = staging()
        File(dir, "bin").mkdirs()
        File(dir, "bin/version.json").writeText(json)
        return dir
    }
}
```

- [ ] **Step 2: Rodar para ver falhar**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*TechnicPackVersionResolverTest*" --console=plain`
Expected: **FAIL** no compile — `unresolved reference: TechnicPackVersionResolver`.

- [ ] **Step 3: Implementar o resolvedor**

```kotlin
/* (cabeçalho GPLv3 completo) */

package com.movtery.zalithlauncher.game.download.modpack.technic

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.ZipFile

/** Versão resolvida do staging (spec §5.2 etapa 4): base + build do Forge. */
data class PackVersionInfo(
    val minecraft: String,
    val forgeBuild: String?
)

class PackVersionUnresolvableException(message: String) : RuntimeException(message)

object TechnicPackVersionResolver {

    /**
     * Prioridade (spec §5.2 etapa 4): bin/version.json →
     * bin/modpack.jar!/version.json → campo `minecraft` da API.
     */
    fun resolve(stagingDir: File, apiMinecraft: String?): PackVersionInfo {
        File(stagingDir, "bin/version.json").takeIf { it.isFile }?.let { file ->
            parseVersionJson(file.readText())?.let { return it }
        }
        File(stagingDir, "bin/modpack.jar").takeIf { it.isFile }?.let { jar ->
            runCatching {
                ZipFile(jar).use { zip ->
                    val entry = zip.getEntry("version.json") ?: return@runCatching null
                    zip.getInputStream(entry).use { stream ->
                        parseVersionJson(stream.reader().readText())
                    }
                }
            }.getOrNull()?.let { return it }
        }
        apiMinecraft?.takeIf { it.isNotBlank() }?.let { return PackVersionInfo(it, null) }
        throw PackVersionUnresolvableException("No version source for the pack in $stagingDir")
    }

    /** `inheritsFrom` + build do Forge; null quando o JSON não serve como fonte. */
    internal fun parseVersionJson(json: String): PackVersionInfo? = runCatching {
        val root = Json.parseToJsonElement(json).jsonObject
        val inheritsFrom = root["inheritsFrom"]?.jsonPrimitive?.contentOrNull ?: return null
        val forgeBuild = root["libraries"]
            ?.let { it as? JsonArray }
            ?.firstNotNullOfOrNull { element -> forgeBuildFromLibrary(element) }
            ?: forgeBuildFromId(root["id"]?.jsonPrimitive?.contentOrNull)
            ?: return null
        PackVersionInfo(inheritsFrom, forgeBuild)
    }.getOrNull()

    /** `net.minecraftforge:forge:1.7.10-10.13.4.1558-1.7.10` → `10.13.4.1558`. */
    private fun forgeBuildFromLibrary(element: kotlinx.serialization.json.JsonElement): String? {
        val name = when (element) {
            is JsonObject -> element["name"]?.jsonPrimitive?.contentOrNull
            is JsonPrimitive -> element.content
            else -> null
        } ?: return null
        if (!name.startsWith("net.minecraftforge:forge:")) return null
        val coordinate = name.removePrefix("net.minecraftforge:forge:")
        val parts = coordinate.split('-')
        return parts.getOrNull(1)?.takeIf { it.isNotBlank() }
    }

    /** `1.7.10-Forge10.13.4.1558-1.7.10` → `10.13.4.1558` (quando as libraries não trazem o forge). */
    private fun forgeBuildFromId(id: String?): String? {
        id ?: return null
        return Regex("Forge(\\d+(?:\\.\\d+)+)").find(id)?.groupValues?.get(1)
    }
}
```

- [ ] **Step 4: Rodar para ver passar**

Run: mesmo comando do Step 2.
Expected: **PASS** — 7 testes verdes.

- [ ] **Step 5: Suíte completa + commit**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain`
Expected: **BUILD SUCCESSFUL**.

```bash
git add ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackVersionResolver.kt ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackVersionResolverTest.kt
git commit -m "feat(technic): resolve pack Minecraft and Forge versions from staging"
```

---

### Task 3: `PackManifest` — manifesto do pack e regras do update

**Files:**
- Create: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/dedicated/PackManifest.kt`
- Test: `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/dedicated/PackManifestTest.kt`

**Interfaces:**
- Consumes: `getZalithVersionPath(versionFolder: File): File` (game/version/installed/Version.kt:238 — devolve `versions/<slug>/<LAUNCHER_IDENTIFIER>/`).
- Produces: `data class PackManifest(slug: String, packVersion: String, files: List<String>)`; `PackManifest.manifestFile(versionDir): File`; `PackManifest.read(versionDir): PackManifest?`; `PackManifest.write(versionDir, manifest)` (atômico); `PackManifest.isProtected(rel): Boolean`; `PackManifest.packFiles(stagingDir): List<String>`; `PackManifest.orphans(old, newPackFiles): List<String>`; `PackManifest.identityOf(version, url): String?`.

- [ ] **Step 1: Escrever o teste falhando**

```kotlin
/* (cabeçalho GPLv3 completo) */

package com.movtery.zalithlauncher.game.dedicated

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PackManifestTest {

    private fun staging(): File = Files.createTempDirectory("pack-staging").toFile()
    private fun versionDir(): File = Files.createTempDirectory("pack-version").toFile()

    private fun writeStaging(vararg paths: String) {
        // usado com staging() em cada teste
    }

    @Test
    fun `write and read round trip atomically`() {
        val dir = versionDir()
        try {
            val manifest = PackManifest("dbc-super-oficial", "10.8", listOf("mods/a.jar", "config/b.cfg"))
            PackManifest.write(dir, manifest)

            assertEquals(manifest, PackManifest.read(dir))
            assertTrue(PackManifest.manifestFile(dir).isFile)
            val leftovers = PackManifest.manifestFile(dir).parentFile.listFiles().orEmpty()
                .filter { it.name.endsWith(".tmp") }
            assertTrue("gravação atômica deixou arquivo .tmp", leftovers.isEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `missing manifest reads as null`() {
        val dir = versionDir()
        try {
            assertNull(PackManifest.read(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `corrupt manifest reads as null instead of throwing`() {
        val dir = versionDir()
        try {
            val file = PackManifest.manifestFile(dir)
            file.parentFile?.mkdirs()
            file.writeText("{broken json")
            assertNull(PackManifest.read(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `pack files exclude bin and player data`() {
        val dir = staging()
        try {
            listOf(
                "bin/minecraft.jar", "bin/natives/awt.dll",
                "mods/x.jar", "config/a.cfg",
                "servers.dat", "options.txt", "saves/world/level.dat"
            ).forEach { path ->
                val file = File(dir, path)
                file.parentFile?.mkdirs()
                file.writeText("x")
            }

            assertEquals(listOf("config/a.cfg", "mods/x.jar"), PackManifest.packFiles(dir))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `orphans list only removed pack files`() {
        val old = PackManifest("dbc-super-oficial", "10.7",
            listOf("mods/keep.jar", "mods/gone.jar", "config/old.cfg"))
        val newFiles = listOf("mods/keep.jar")

        assertEquals(listOf("config/old.cfg", "mods/gone.jar"), PackManifest.orphans(old, newFiles))
    }

    @Test
    fun `protected paths never appear in the orphan list`() {
        val old = PackManifest("dbc-super-oficial", "10.7",
            listOf("servers.dat", "options.txt", "saves/world/level.dat", "bin/version.json", "mods/gone.jar"))

        assertEquals(listOf("mods/gone.jar"), PackManifest.orphans(old, emptyList()))
        assertTrue(PackManifest.isProtected("saves/any/thing"))
        assertTrue(PackManifest.isProtected("options.txt"))
        assertTrue(PackManifest.isProtected("servers.dat"))
        assertFalse(PackManifest.isProtected("mods/x.jar"))
    }

    @Test
    fun `identity uses version then url then nothing`() {
        assertEquals("10.8", PackManifest.identityOf("10.8", "https://dl/x.zip"))
        assertEquals("https://dl/x.zip", PackManifest.identityOf(null, "https://dl/x.zip"))
        assertEquals("https://dl/x.zip", PackManifest.identityOf("  ", "https://dl/x.zip"))
        assertNull(PackManifest.identityOf(null, null))
        assertNull(PackManifest.identityOf("", ""))
    }
}
```

- [ ] **Step 2: Rodar para ver falhar**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*PackManifestTest*" --console=plain`
Expected: **FAIL** no compile — `unresolved reference: PackManifest`.

- [ ] **Step 3: Implementar o manifesto**

```kotlin
/* (cabeçalho GPLv3 completo) */

package com.movtery.zalithlauncher.game.dedicated

import com.movtery.zalithlauncher.game.version.installed.getZalithVersionPath
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/**
 * Conteúdo do pack-manifest.json (spec §5.3): o que o pack possui,
 * para apagar órfãos no update sem tocar nos dados do jogador.
 */
@Serializable
data class PackManifest(
    val slug: String,
    val packVersion: String,
    val files: List<String>
) {
    companion object {
        const val FILE_NAME = "pack-manifest.json"
        private val JSON = Json { prettyPrint = true }

        /** `versions/<slug>/<LAUNCHER_IDENTIFIER>/pack-manifest.json` — ao lado do version.config (§5.3). */
        fun manifestFile(versionDir: File): File = File(getZalithVersionPath(versionDir), FILE_NAME)

        /** Manifesto ausente ou corrompido = ausente; nunca lança (spec §5.5: volta a instalar). */
        fun read(versionDir: File): PackManifest? {
            val file = manifestFile(versionDir)
            if (!file.isFile) return null
            return runCatching { JSON.decodeFromString(PackManifest.serializer(), file.readText()) }
                .getOrNull()
        }

        /** Gravação atômica: tmp no mesmo diretório + move com replace (§5.3 regra 5). */
        fun write(versionDir: File, manifest: PackManifest) {
            val target = manifestFile(versionDir)
            target.parentFile?.mkdirs()
            val tmp = File(target.parentFile, "$FILE_NAME.tmp")
            tmp.writeText(JSON.encodeToString(PackManifest.serializer(), manifest))
            Files.move(
                tmp.toPath(), target.toPath(),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE
            )
        }

        /** Dados do jogador: fora do manifesto e nunca removidos (§5.3 regra 4). */
        fun isProtected(relativePath: String): Boolean {
            val path = relativePath.replace('\\', '/')
            return path == "options.txt" || path == "servers.dat" || path.startsWith("saves/")
        }

        private fun isBinPath(relativePath: String): Boolean {
            val path = relativePath.replace('\\', '/')
            return path == "bin" || path.startsWith("bin/")
        }

        /** O que pertence ao pack: staging inteiro menos `bin/` e dados do jogador (§5.2 etapa 6). */
        fun packFiles(stagingDir: File): List<String> =
            stagingDir.walkTopDown()
                .filter { it.isFile }
                .map { it.relativeTo(stagingDir).invariantSeparatorsPath }
                .filter { !isBinPath(it) && !isProtected(it) }
                .sorted()
                .toList()

        /** Update: arquivos do manifesto antigo que não existem mais no pack novo (§5.3 regra 2). */
        fun orphans(old: PackManifest, newPackFiles: List<String>): List<String> {
            val kept = newPackFiles.toSet()
            return old.files
                .map { it.replace('\\', '/') }
                .filter { it !in kept && !isProtected(it) && !isBinPath(it) }
                .distinct()
                .sorted()
                .toList()
        }

        /**
         * Identidade do pack (spec §5.1): `version`; se vazio cai para `url`;
         * null quando os dois vazios (aí o estado considera READY, nunca loop).
         */
        fun identityOf(version: String?, url: String?): String? =
            version?.takeIf { it.isNotBlank() }
                ?: url?.takeIf { it.isNotBlank() }
    }
}
```

- [ ] **Step 4: Rodar para ver passar**

Run: mesmo comando do Step 2.
Expected: **PASS** — 7 testes verdes.

- [ ] **Step 5: Suíte completa + commit**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain`
Expected: **BUILD SUCCESSFUL**.

```bash
git add ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/dedicated/PackManifest.kt ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/dedicated/PackManifestTest.kt
git commit -m "feat(dedicated): add pack manifest with atomic write and diff rules"
```

---

### Task 4: Estágio de download — engine com User-Agent, diretório de cache, strings e `downloadPhase`

**Files:**
- Modify: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/engine/DownloadRequest.kt:28-36`
- Modify: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/engine/FileDownloader.kt:29-36`
- Modify: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/engine/Fetcher.kt:197-230, 249-255, 297-320, 440-447`
- Modify: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/path/PathManager.kt:53` (declaração) e `:96` (atribuição)
- Modify: `ZalithLauncher/src/main/res/values/strings.xml` e `ZalithLauncher/src/main/res/values-pt-rBR/strings.xml`
- Create: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackInstaller.kt`
- Test: `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackInstallerTest.kt`
- Modify: `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/download/engine/FetcherHttpTest.kt` (+2 testes)

**Interfaces:**
- Consumes: `TechnicApi` (Task 1: `BROWSER_USER_AGENT`, `getPack`), `PackManifest.identityOf` (Task 3), `DownloadEngine.download` (DownloadEngine.kt:38), `withSpeedReport` (utils/network/NetWorkUtils.kt:119 — `suspend fun <T> withSpeedReport(onSpeedReport: (Long) -> Unit, onClear: () -> Unit = {}, block: suspend (onBytesWritten: (Long) -> Unit) -> T): T`).
- Produces: `buildPackRequest(url, zipFile): DownloadRequest`, `progressOf(downloaded, total): Float`, `headContentLength(url): Long`, `PathManager.DIR_CACHE_DEDICATED_PACK: File`, classe `TechnicPackInstaller(scope: CoroutineScope)` com `tasksFlow`, `logOutput: StateFlow<TaskLogOutput?>` e `internal fun downloadPhase(): TaskFlowExecutor.TaskPhase`; campo `userAgent: String?` em `DownloadRequest`; todas as strings `dedicated_*`.

- [ ] **Step 1: Escrever os testes falhando**

Crie `TechnicPackInstallerTest.kt`:

```kotlin
/* (cabeçalho GPLv3 completo) */

package com.movtery.zalithlauncher.game.download.modpack.technic

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class TechnicPackInstallerTest {

    @Test
    fun `pack request goes straight to the DownloadEngine`() {
        val url = "https://www.dropbox.com/scl/fi/example/ATT58.zip?rlkey=abc&dl=1"
        val request = buildPackRequest(url, File("pack.zip"))

        assertEquals(listOf(url), request.urls)
        assertNull(request.sha1)                 // a Platform não publica hash do zip (spec §4)
        assertEquals(-1L, request.expectedSize)  // sem gate de tamanho; total chega via HEAD
        assertEquals(TechnicApi.BROWSER_USER_AGENT, request.userAgent)  // §5.5: UA de navegador
    }

    @Test
    fun `progress is indeterminate when the total is unknown`() {
        assertEquals(-1f, progressOf(512L, -1L), 0.0001f)
        assertEquals(-1f, progressOf(512L, 0L), 0.0001f)
        assertEquals(0.5f, progressOf(512L, 1024L), 0.0001f)
        assertEquals(1f, progressOf(4096L, 1024L), 0.0001f)
    }
}
```

Em `FetcherHttpTest.kt`, inserir logo **após** a linha `private val payload = ByteArray(1024).also { Random(7).nextBytes(it) }` (antes do primeiro `@Test`):

```kotlin
    @Test
    fun `sends the custom user agent when provided`() = runBlocking<Unit> {
        val customUa = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        val source = ScriptedSource { _, _ -> scriptedResponse(200, payload) }
        val server = startServer(source)
        val target = File(newWorkDir(), "out.bin")

        Fetcher.downloadFile(listOf(server.url("/file").toString()), target, userAgent = customUa)

        assertEquals(customUa, source.requests.first().headers["User-Agent"])
    }

    @Test
    fun `keeps the launcher user agent by default`() = runBlocking<Unit> {
        val source = ScriptedSource { _, _ -> scriptedResponse(200, payload) }
        val server = startServer(source)
        val target = File(newWorkDir(), "out.bin")

        Fetcher.downloadFile(listOf(server.url("/file").toString()), target)

        assertEquals(URL_USER_AGENT, source.requests.first().headers["User-Agent"])
    }
```

- [ ] **Step 2: Rodar para ver falhar**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*TechnicPackInstallerTest*" --tests "*FetcherHttpTest*" --console=plain`
Expected: **FAIL** no compile — `unresolved reference: buildPackRequest` e `no parameter with name 'userAgent'`.

- [ ] **Step 3: Implementar engine + PathManager + strings + instalador**

**(a) `DownloadRequest.kt`** — adicionar o parâmetro final (default `null` mantém o comportamento de todos os outros chamadores):

```kotlin
class DownloadRequest(
    val urls: List<String>,
    val targetFile: File,
    val sha1: String? = null,
    /** 已知的文件大小，未知时传 -1；仅用于预分配与进度统计，最终以实际响应为准 */
    val expectedSize: Long = -1L,
    /** 调用方附带的上下文对象，在进度与成功回调中原样带回 */
    val tag: Any? = null,
    /** 覆盖默认 User-Agent；null 沿用启动器 UA（Dropbox 等源需要浏览器 UA） */
    val userAgent: String? = null
) {
```

**(b) `FileDownloader.kt`** — repassar:

```kotlin
    suspend fun download() {
        Fetcher.downloadFile(
            urls = request.urls,
            targetFile = request.targetFile,
            sha1 = request.sha1,
            onBytes = stats::addBytes,
            userAgent = request.userAgent
        )
    }
```

**(c) `Fetcher.kt`** — threading do parâmetro pela cadeia (4 pontos):

```kotlin
// L197 — assinatura
suspend fun downloadFile(
    urls: List<String>,
    targetFile: File,
    sha1: String? = null,
    retry: Int = DEFAULT_RETRY,
    onBytes: (Long) -> Unit = {},
    userAgent: String? = null
) {
    // ... (corpo inalterado, exceto a chamada:)
            downloadCandidate(url, targetFile, sha1, retry, onBytes, userAgent)

// L249 — assinatura
private suspend fun downloadCandidate(
    url: URL,
    targetFile: File,
    sha1: String?,
    retry: Int,
    onBytes: (Long) -> Unit,
    userAgent: String?
) {
    // ... dentro de runInterruptible:
                    attempt(url, targetFile, sha1, state, onBytes, userAgent)

// L297 — assinatura
private fun attempt(
    url: URL,
    targetFile: File,
    sha1: String?,
    state: CandidateState,
    onBytes: (Long) -> Unit,
    userAgent: String?
): AttemptResult {
    // ... dentro do while de redirects:
            val conn = openConnection(currentUri, userAgent)

// L440 — assinatura + header
private fun openConnection(url: URL, userAgent: String?): HttpURLConnection {
    val connection = url.openConnection() as HttpURLConnection
    connection.connectTimeout = TIMEOUT_MILLIS
    connection.readTimeout = TIMEOUT_MILLIS
    connection.instanceFollowRedirects = false
    connection.setRequestProperty("User-Agent", userAgent ?: URL_USER_AGENT)
```

**(d) `PathManager.kt`** — novo diretório, mesmo padrão dos vizinhos:

```kotlin
// declaração (~L53, após DIR_CACHE_HOME_PAGE)
    /** temp_dedicated_pack — download e staging do pack dedicado (spec §5.2 etapa 2) */
    lateinit var DIR_CACHE_DEDICATED_PACK: File

// atribuição (~L96, após a de DIR_CACHE_HOME_PAGE)
        DIR_CACHE_DEDICATED_PACK = File(DIR_CACHE, "temp_dedicated_pack")
```

**(e) Strings** — em `res/values/strings.xml`, inserir **imediatamente após** a entrada `unofficial_modified_notice` (linha ~91):

```xml
<!-- Dedicated Super Launcher build: dedicated screen, installer and errors (spec Fases 3-5) -->
<string name="dedicated_pack_title" translatable="false">DBC Super (Oficial)</string>
<string name="dedicated_install">Install</string>
<string name="dedicated_update">Update</string>
<string name="dedicated_play">Play</string>
<string name="dedicated_retry">Try again</string>
<string name="dedicated_offline">Offline</string>
<string name="dedicated_installing_title">Installing DBC Super</string>
<string name="dedicated_error_api_build">The Technic API rejected the launcher build number</string>
<string name="dedicated_error_no_space">Not enough free storage to extract the pack</string>
<string name="dedicated_error_generic">Installation failed</string>
<string name="dedicated_task_resolve_pack">Checking pack version</string>
<string name="dedicated_task_download">Downloading the pack</string>
<string name="dedicated_task_extract">Extracting the pack</string>
<string name="dedicated_task_resolve">Resolving the game version</string>
<string name="dedicated_task_base">Installing Minecraft and Forge</string>
<string name="dedicated_task_overlay">Applying pack files</string>
<string name="dedicated_task_seed">Configuring server and controls</string>
<string name="dedicated_task_select">Selecting the version</string>
```

Em `res/values-pt-rBR/strings.xml`, inserir antes de `</resources>` (mesmo bloco, `dedicated_pack_title` fica de fora por ser `translatable="false"`):

```xml
<!-- Super Launcher dedicado: tela, instalador e erros (spec Fases 3-5) -->
<string name="dedicated_install">Instalar</string>
<string name="dedicated_update">Atualizar</string>
<string name="dedicated_play">Jogar</string>
<string name="dedicated_retry">Tentar de novo</string>
<string name="dedicated_offline">Offline</string>
<string name="dedicated_installing_title">Instalando o DBC Super</string>
<string name="dedicated_error_api_build">A API do Technic rejeitou o número de build</string>
<string name="dedicated_error_no_space">Espaço de armazenamento insuficiente para extrair o pack</string>
<string name="dedicated_error_generic">Falha na instalação</string>
<string name="dedicated_task_resolve_pack">Verificando a versão do pack</string>
<string name="dedicated_task_download">Baixando o pack</string>
<string name="dedicated_task_extract">Extraindo o pack</string>
<string name="dedicated_task_resolve">Resolvendo a versão do jogo</string>
<string name="dedicated_task_base">Instalando Minecraft e Forge</string>
<string name="dedicated_task_overlay">Aplicando arquivos do pack</string>
<string name="dedicated_task_seed">Configurando servidor e controles</string>
<string name="dedicated_task_select">Selecionando a versão</string>
```

**(f) `TechnicPackInstaller.kt`** (primeira versão — só as etapas 1-2):

```kotlin
/* (cabeçalho GPLv3 completo) */

package com.movtery.zalithlauncher.game.download.modpack.technic

import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.coroutine.TaskFlowExecutor
import com.movtery.zalithlauncher.coroutine.TaskLogOutput
import com.movtery.zalithlauncher.coroutine.addTask
import com.movtery.zalithlauncher.coroutine.buildPhase
import com.movtery.zalithlauncher.game.download.engine.DownloadEngine
import com.movtery.zalithlauncher.game.download.engine.DownloadRequest
import com.movtery.zalithlauncher.game.dedicated.PackManifest
import com.movtery.zalithlauncher.path.DOWNLOAD_OKHTTP_CLIENT
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.path.createRequestBuilder
import com.movtery.zalithlauncher.ui.androidText
import com.movtery.zalithlauncher.utils.network.withSpeedReport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

private const val TAG = "TechnicPackInstaller"

/** Zip em `cache/temp_dedicated_pack/pack.zip` (spec §5.2 etapa 2). */
private fun packZipFile(): File = File(PathManager.DIR_CACHE_DEDICATED_PACK, "pack.zip")

/** Staging extraído (spec §5.2 etapa 3). */
private fun stagingDirectory(): File = File(PathManager.DIR_CACHE_DEDICATED_PACK, "staging")

/**
 * Request do pack: `DownloadEngine` direto — sem o timeout global de 5 min do
 * `downloadFileFromSources` (o zip tem 90 MB), `sha1=null` porque a Platform não
 * publica hash, e User-Agent de navegador para o Dropbox (spec §5.5).
 */
internal fun buildPackRequest(url: String, zipFile: File): DownloadRequest =
    DownloadRequest(
        urls = listOf(url),
        targetFile = zipFile,
        sha1 = null,
        userAgent = TechnicApi.BROWSER_USER_AGENT
    )

/** -1f = total desconhecido ⇒ progresso indeterminado; nunca NaN, nunca acima de 1. */
internal fun progressOf(downloaded: Long, total: Long): Float =
    if (total > 0) (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f) else -1f

/** Tamanho total via HEAD — o engine só reporta bytes incrementais. */
internal fun headContentLength(url: String): Long = runCatching {
    DOWNLOAD_OKHTTP_CLIENT.newCall(createRequestBuilder(url).head().build()).execute().use { resp ->
        if (resp.isSuccessful) resp.body?.contentLength() ?: -1L else -1L
    }
}.getOrDefault(-1L)

/**
 * Instala/atualiza o pack dedicado (8 etapas da spec §5.2).
 * O `Context` entra por `install()` — a construção precisa ficar testável em JVM.
 */
class TechnicPackInstaller(private val scope: CoroutineScope) {
    private val taskExecutor = TaskFlowExecutor(scope)
    val tasksFlow = taskExecutor.tasksFlow

    private val _logOutput = MutableStateFlow<TaskLogOutput?>(null)
    val logOutput: StateFlow<TaskLogOutput?> = _logOutput.asStateFlow()

    private var pack: TechnicPack? = null
    private var apiIdentity: String = ""

    /** Etapas 1-2: limpa o temp, resolve o pack na API e baixa o zip com % e velocidade. */
    internal fun downloadPhase(): TaskFlowExecutor.TaskPhase = buildPhase {
        addTask(id = "Dedicated.ClearTemp", title = androidText(R.string.download_install_clear_temp)) {
            PathManager.DIR_CACHE_DEDICATED_PACK.deleteRecursively()
            PathManager.DIR_CACHE_DEDICATED_PACK.mkdirs()
        }
        addTask(id = "Dedicated.ResolvePack", title = androidText(R.string.dedicated_task_resolve_pack)) { task ->
            val fetched = TechnicApi.getPack(BuildKeys.DEDICATED_PACK_SLUG)
            pack = fetched
            apiIdentity = PackManifest.identityOf(fetched.version, fetched.url) ?: ""
            task.updateProgress(1f)
        }
        addTask(id = "Dedicated.Download", title = androidText(R.string.dedicated_task_download)) { task ->
            val packUrl = requireNotNull(requireNotNull(pack).url) { "Pack has no download url" }
            val zipFile = packZipFile()
            val totalBytes = headContentLength(packUrl)
            var downloaded = 0L
            task.updateProgress(progressOf(0L, totalBytes))
            withSpeedReport(
                onSpeedReport = { task.updateSpeed(it) },
                onClear = { task.clearSpeed() }
            ) { report ->
                DownloadEngine.download(buildPackRequest(packUrl, zipFile)) { delta ->
                    downloaded += delta
                    task.updateProgress(progressOf(downloaded, totalBytes))
                    report(delta)
                }
            }
            task.updateProgress(1f)
        }
    }
}
```

Nota (fato verificado): `TaskLogOutput` declara-se em `coroutine/TaskLogOutput.kt:34` → o import `com.movtery.zalithlauncher.coroutine.TaskLogOutput` está correto.

- [ ] **Step 4: Rodar para ver passar**

Run: mesmo comando do Step 2.
Expected: **PASS** — 4 testes verdes (2 novos + 2 de UA).

- [ ] **Step 5: Suíte completa + commit**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain`
Expected: **BUILD SUCCESSFUL** (a suíte inteira prova que o engine com `userAgent=null` não mudou comportamento de ninguém).

```bash
git add ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/engine/DownloadRequest.kt ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/engine/FileDownloader.kt ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/engine/Fetcher.kt ZalithLauncher/src/main/java/com/movtery/zalithlauncher/path/PathManager.kt ZalithLauncher/src/main/res/values/strings.xml ZalithLauncher/src/main/res/values-pt-rBR/strings.xml ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackInstaller.kt ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackInstallerTest.kt ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/download/engine/FetcherHttpTest.kt
git commit -m "feat(technic): download pack zip with browser UA and percent progress"
```

---

### Task 5: Estágios de extração, resolução e base 1.7.10+Forge

**Files:**
- Modify: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackInstaller.kt`
- Test: `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackInstallerTest.kt`

**Interfaces:**
- Consumes: `TechnicPackVersionResolver.resolve` / `PackVersionInfo` (Task 2); `extractFromZip(internalPath: String, outputDir: File)` suspend em `ZipFile` (utils/file/FileUtils.kt:274); `ForgeVersions.fetchForgeList(mcVersion: String): List<ForgeVersion>?` suspend (addons/modloader/forgelike/forge/ForgeVersions.kt:56) com propriedade `forgeBuildVersion`; `GameDownloadInfo(gameVersion, customVersionName, overwrite, ..., forge)` (game/download/game/GameDownloadInfo.kt:30); `GameInstaller(context, info, scope, logOutputHolder, targetGameFolder)` + `getTaskPhase(createIsolation: Boolean = true, ...)` (GameInstaller.kt:91/294); `VersionsManager.isVersionExists(name, checkJson)` (VersionsManager.kt:80).
- Produces: `ensureFreeSpace(availableBytes, requiredBytes)`, exceções `InsufficientSpaceException`, `PackForgeNotFoundException`, `extractPack(zipFile, stagingDir)` suspend, `internal fun extractBasePhase(): TaskFlowExecutor.TaskPhase` (tasks `Dedicated.Extract`, `Dedicated.ResolveVersion`, `Dedicated.InstallBase`); campos de estado `versionInfo`, `gameInstaller`, `appContext`.

- [ ] **Step 1: Escrever os testes falhando**

Adicionar ao `TechnicPackInstallerTest.kt` (imports: `kotlinx.coroutines.runBlocking`, `java.io.FileOutputStream`, `java.nio.file.Files`, `java.util.zip.ZipEntry`, `java.util.zip.ZipOutputStream`, `org.junit.Assert.assertTrue`, `org.junit.Assert.fail`):

```kotlin
    @Test
    fun `extract unpacks the whole archive into staging`() = runBlocking<Unit> {
        val root = Files.createTempDirectory("dedicated-extract").toFile()
        try {
            val zip = File(root, "pack.zip")
            ZipOutputStream(FileOutputStream(zip)).use { zos ->
                zos.putNextEntry(ZipEntry("bin/version.json"))
                zos.write("""{"id":"x"}""".toByteArray())
                zos.closeEntry()
                zos.putNextEntry(ZipEntry("mods/a.jar"))
                zos.write(byteArrayOf(1, 2, 3))
                zos.closeEntry()
            }
            val staging = File(root, "staging")

            extractPack(zip, staging)

            assertTrue(File(staging, "bin/version.json").isFile)
            assertTrue(File(staging, "mods/a.jar").isFile)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `free space is required before extracting`() {
        ensureFreeSpace(availableBytes = 200L, requiredBytes = 100L)   // não lança
        try {
            ensureFreeSpace(availableBytes = 50L, requiredBytes = 100L)
            fail("deveria lançar InsufficientSpaceException")
        } catch (expected: InsufficientSpaceException) {
            assertTrue(expected.message!!.contains("100"))
        }
    }
```

- [ ] **Step 2: Rodar para ver falhar**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*TechnicPackInstallerTest*" --console=plain`
Expected: **FAIL** no compile — `unresolved reference: extractPack`.

- [ ] **Step 3: Implementar os estágios**

Adicionar ao `TechnicPackInstaller.kt` — imports novos: `android.content.Context`, `com.movtery.zalithlauncher.game.addons.modloader.forgelike.forge.ForgeVersions`, `com.movtery.zalithlauncher.game.download.game.GameDownloadInfo`, `com.movtery.zalithlauncher.game.download.game.GameInstaller`, `com.movtery.zalithlauncher.game.version.installed.VersionsManager`, `com.movtery.zalithlauncher.utils.file.extractFromZip`, `java.util.zip.ZipFile`.

Top-level (junto dos helpers da Task 4):

```kotlin
class InsufficientSpaceException(message: String) : RuntimeException(message)
class PackForgeNotFoundException(message: String) : RuntimeException(message)

/** Espaço livre antes de extrair (spec §10): zip + margem para o conteúdo extraído. */
internal fun ensureFreeSpace(availableBytes: Long, requiredBytes: Long) {
    if (availableBytes < requiredBytes) {
        throw InsufficientSpaceException(
            "Need ${requiredBytes / (1024 * 1024)} MB free, have ${availableBytes / (1024 * 1024)} MB"
        )
    }
}

/** Etapa 3 (spec §5.2): extrai o zip inteiro para o staging, validando espaço antes. */
internal suspend fun extractPack(zipFile: File, stagingDir: File) {
    stagingDir.deleteRecursively()
    stagingDir.mkdirs()
    ensureFreeSpace(stagingDir.usableSpace, zipFile.length() * 2)
    ZipFile(zipFile).use { it.extractFromZip("", stagingDir) }
}
```

Dentro da classe `TechnicPackInstaller` (campos + fase; o `InstallBase` ganha o `addPhases` dinâmico que a Task 8 estende):

```kotlin
    private var versionInfo: PackVersionInfo? = null
    private var gameInstaller: GameInstaller? = null
    private var appContext: Context? = null

    /** Etapas 3-5: extrai, resolve a versão do pack e instala a base via caminho legado do Forge. */
    internal fun extractBasePhase(): TaskFlowExecutor.TaskPhase = buildPhase {
        addTask(id = "Dedicated.Extract", title = androidText(R.string.dedicated_task_extract)) { task ->
            extractPack(packZipFile(), stagingDirectory())
            task.updateProgress(1f)
        }
        addTask(id = "Dedicated.ResolveVersion", title = androidText(R.string.dedicated_task_resolve)) { task ->
            val pack = requireNotNull(pack)
            versionInfo = TechnicPackVersionResolver.resolve(stagingDirectory(), pack.minecraft)
            task.updateProgress(1f)
        }
        addTask(id = "Dedicated.InstallBase", title = androidText(R.string.dedicated_task_base)) { task ->
            val info = requireNotNull(versionInfo)
            val forgeVersion = ForgeVersions.fetchForgeList(info.minecraft)
                ?.firstOrNull { it.forgeBuildVersion == info.forgeBuild }
                ?: throw PackForgeNotFoundException(
                    "Forge ${info.forgeBuild} not found for Minecraft ${info.minecraft}"
                )
            val slug = BuildKeys.DEDICATED_PACK_SLUG
            val gameInfo = GameDownloadInfo(
                gameVersion = info.minecraft,
                customVersionName = slug,
                // sobrescreve quando o json já existe: evita GameAlreadyInstalledException no update
                overwrite = VersionsManager.isVersionExists(slug, true),
                forge = forgeVersion
            )
            val installer = GameInstaller(
                context = requireNotNull(appContext) { "install() was not called yet" },
                info = gameInfo,
                scope = scope,
                logOutputHolder = _logOutput
            )
            gameInstaller = installer
            // addPhases dinâmico: as fases do GameInstaller entram no fluxo em tempo de execução
            // (mesmo padrão do ModPackInstaller.kt:241-280); overlay/seed/select chegam na Task 8.
            taskExecutor.addPhases(installer.getTaskPhase(createIsolation = true))
            task.updateProgress(1f)
        }
    }
```

- [ ] **Step 4: Rodar para ver passar**

Run: mesmo comando do Step 2.
Expected: **PASS** — 4 testes verdes.

- [ ] **Step 5: Suíte completa + commit**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain`
Expected: **BUILD SUCCESSFUL**.

```bash
git add ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackInstaller.kt ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackInstallerTest.kt
git commit -m "feat(technic): extract staging and install the 1.7.10 Forge base"
```

---

### Task 6: Overlay — cópia do pack com regras de update

**Files:**
- Modify: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackInstaller.kt`
- Test: `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackInstallerTest.kt`

**Interfaces:**
- Consumes: `PackManifest.isProtected` / `packFiles` / `orphans` (Task 3).
- Produces: `overlayPack(stagingDir: File, versionDir: File, oldManifest: PackManifest?, onProgress: (Float) -> Unit)` suspend; `internal fun overlayPhase(stagingDir, versionDir, oldManifest): TaskFlowExecutor.TaskPhase` (task `Dedicated.Overlay`).

- [ ] **Step 1: Escrever os testes falhando**

Adicionar ao `TechnicPackInstallerTest.kt`:

```kotlin
    @Test
    fun `first install copies player data but never bin`() = runBlocking<Unit> {
        val root = Files.createTempDirectory("overlay-first").toFile()
        try {
            val staging = File(root, "staging")
            val versionDir = File(root, "version")
            listOf("bin/minecraft.jar", "mods/pack.jar", "servers.dat", "options.txt", "saves/packworld/level.dat")
                .forEach { path ->
                    File(staging, path).let { it.parentFile?.mkdirs(); it.writeText("pack") }
                }
            val progress = mutableListOf<Float>()

            overlayPack(staging, versionDir, oldManifest = null) { progress.add(it) }

            assertFalse(File(versionDir, "bin/minecraft.jar").exists())
            assertEquals("pack", File(versionDir, "mods/pack.jar").readText())
            assertEquals("pack", File(versionDir, "servers.dat").readText())
            assertEquals("pack", File(versionDir, "options.txt").readText())
            assertEquals("pack", File(versionDir, "saves/packworld/level.dat").readText())
            assertEquals(1f, progress.last(), 0.0001f)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun `update never overwrites player data and removes orphan mods`() = runBlocking<Unit> {
        val root = Files.createTempDirectory("overlay-update").toFile()
        try {
            val staging = File(root, "staging")
            val versionDir = File(root, "version")
            // estado do jogador após uma instalação anterior
            File(versionDir, "mods/old.jar").let { it.parentFile?.mkdirs(); it.writeText("old") }
            File(versionDir, "mods/new.jar").let { it.parentFile?.mkdirs(); it.writeText("stale") }
            File(versionDir, "options.txt").let { it.parentFile?.mkdirs(); it.writeText("player-options") }
            File(versionDir, "servers.dat").let { it.parentFile?.mkdirs(); it.writeText("player-servers") }
            File(versionDir, "saves/mysave/level.dat").let { it.parentFile?.mkdirs(); it.writeText("player-save") }
            // zip novo: mods/new.jar atualizado + protegidos diferentes (que NÃO devem entrar)
            File(staging, "mods/new.jar").let { it.parentFile?.mkdirs(); it.writeText("pack-new") }
            File(staging, "options.txt").let { it.parentFile?.mkdirs(); it.writeText("pack-options") }
            File(staging, "servers.dat").let { it.parentFile?.mkdirs(); it.writeText("pack-servers") }

            val oldManifest = PackManifest("dbc-super-oficial", "10.7", listOf("mods/old.jar", "mods/new.jar"))
            overlayPack(staging, versionDir, oldManifest) { }

            assertFalse("órfão do manifesto antigo deveria sumir", File(versionDir, "mods/old.jar").exists())
            assertEquals("pack-new", File(versionDir, "mods/new.jar").readText())
            assertEquals("player-options", File(versionDir, "options.txt").readText())
            assertEquals("player-servers", File(versionDir, "servers.dat").readText())
            assertEquals("player-save", File(versionDir, "saves/mysave/level.dat").readText())
        } finally {
            root.deleteRecursively()
        }
    }
```

- [ ] **Step 2: Rodar para ver falhar**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*TechnicPackInstallerTest*" --console=plain`
Expected: **FAIL** no compile — `unresolved reference: overlayPack`.

- [ ] **Step 3: Implementar o overlay**

Adicionar ao `TechnicPackInstaller.kt` (top-level + método de fase):

```kotlin
/**
 * Etapa 6 (spec §5.2) + regras de update (§5.3): copia o staging para
 * `versions/<slug>/` sem `bin/`; no update apaga os órfãos do manifesto antigo e
 * **nunca** sobrescreve `saves/`, `options.txt` nem `servers.dat`; na primeira
 * instalação os protegidos do pack são copiados normalmente.
 */
internal suspend fun overlayPack(
    stagingDir: File,
    versionDir: File,
    oldManifest: PackManifest?,
    onProgress: (Float) -> Unit
) {
    val copyable = stagingDir.walkTopDown()
        .filter { it.isFile }
        .map { it.relativeTo(stagingDir).invariantSeparatorsPath }
        .filter { path ->
            !path.startsWith("bin/") &&
                (oldManifest == null || !PackManifest.isProtected(path))
        }
        .sorted()
        .toList()

    if (oldManifest != null) {
        PackManifest.orphans(oldManifest, PackManifest.packFiles(stagingDir)).forEach { rel ->
            if (!PackManifest.isProtected(rel)) {
                File(versionDir, rel).takeIf { it.exists() }?.delete()
            }
        }
    }

    versionDir.mkdirs()
    copyable.forEachIndexed { index, rel ->
        val source = File(stagingDir, rel)
        val target = File(versionDir, rel)
        target.parentFile?.mkdirs()
        source.copyTo(target, overwrite = true)
        onProgress((index + 1).toFloat() / copyable.size)
    }
    if (copyable.isEmpty()) onProgress(1f)
}
```

Dentro da classe:

```kotlin
    /** Etapa 6 como fase do fluxo. */
    internal fun overlayPhase(
        stagingDir: File,
        versionDir: File,
        oldManifest: PackManifest?
    ): TaskFlowExecutor.TaskPhase = buildPhase {
        addTask(id = "Dedicated.Overlay", title = androidText(R.string.dedicated_task_overlay)) { task ->
            overlayPack(stagingDir, versionDir, oldManifest) { pct -> task.updateProgress(pct) }
        }
    }
```

- [ ] **Step 4: Rodar para ver passar**

Run: mesmo comando do Step 2.
Expected: **PASS** — 6 testes verdes.

- [ ] **Step 5: Suíte completa + commit**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain`
Expected: **BUILD SUCCESSFUL**.

```bash
git add ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackInstaller.kt ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackInstallerTest.kt
git commit -m "feat(dedicated): overlay pack files honoring player-data rules"
```

---

### Task 7: `DedicatedSeeder` + variante "semeia se não existe" do `ControlManager`

**Files:**
- Create: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/dedicated/DedicatedSeeder.kt`
- Modify: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/control/ControlManager.kt:47` (const) e `:162` (inserir após `unpackDefaultControl`)
- Test: `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/dedicated/DedicatedSeederTest.kt`
- Test: `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/control/ControlManagerSeedTest.kt`

**Interfaces:**
- Consumes: `AllServers.loadServers(dataFile: File)` suspend / `save(savePath: File)` suspend / `addServer(ServerData)` (game/version/multiplayer/AllServers.kt:50,106,80); `ServerData(name: String, originIp: String, ...)` (ServerData.kt:44); `VersionConfig.parseConfig(versionPath: File)` (VersionConfig.kt:270) + setters `isolationType`/`skipGameIntegrityCheck`/`ramAllocation`/`control` + `save()` (:164); `SettingState.ENABLE/DISABLE` (VersionConfig.kt:303-310); `BuildKeys.DEDICATED_SERVER_*`; `Context.copyAssetFile(fileName, output: File, overwrite)` (context/Contexts.kt:77).
- Produces: `DedicatedSeeder.ensureServer(versionDir: File): Boolean` suspend; `DedicatedSeeder.ensureVersionConfig(versionDir: File, controlFileName: String, applyDefaults: Boolean)` suspend; `ControlManager.ensureDefaultLayout(context: Context): String` suspend; `ControlManager.ensureLayoutFile(dir, copyIfMissing)` suspend (seam).

- [ ] **Step 1: Escrever os testes falhando**

`DedicatedSeederTest.kt` (imports: cabeçalho GPLv3, `org.junit.Assert.*` — `assertEquals`, `assertTrue`, `assertFalse`, `assertArrayEquals`; `kotlinx.coroutines.runBlocking`; `java.nio.file.Files`; `com.movtery.zalithlauncher.BuildKeys`, `AllServers`, `ServerData`, `VersionConfig`, `SettingState`):

```kotlin
/* (cabeçalho GPLv3 completo) */

package com.movtery.zalithlauncher.game.dedicated

import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.game.version.installed.SettingState
import com.movtery.zalithlauncher.game.version.installed.VersionConfig
import com.movtery.zalithlauncher.game.version.multiplayer.AllServers
import com.movtery.zalithlauncher.game.version.multiplayer.ServerData
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class DedicatedSeederTest {

    private fun tempDir(): File = Files.createTempDirectory("dedicated-seeder").toFile()

    private suspend fun readServers(dir: File): AllServers =
        AllServers().also { it.loadServers(File(dir, "servers.dat")) }

    @Test
    fun `seeds the dedicated server into an empty directory`() = runBlocking<Unit> {
        val dir = tempDir()
        try {
            assertTrue(DedicatedSeeder.ensureServer(dir))

            val servers = readServers(dir)
            assertEquals(1, servers.serverList.size)
            assertEquals(BuildKeys.DEDICATED_SERVER_NAME, servers.serverList[0].name)
            assertEquals(BuildKeys.DEDICATED_SERVER_IP, servers.serverList[0].originIp)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `never duplicates the entry nor rewrites an unchanged file`() = runBlocking<Unit> {
        val dir = tempDir()
        try {
            DedicatedSeeder.ensureServer(dir)
            val bytes = File(dir, "servers.dat").readBytes()

            assertFalse("segunda chamada não deve mudar nada", DedicatedSeeder.ensureServer(dir))
            assertArrayEquals("arquivo não deveria ser reescrito", bytes, File(dir, "servers.dat").readBytes())
            assertEquals(1, readServers(dir).serverList.size)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `preserves player entries`() = runBlocking<Unit> {
        val dir = tempDir()
        try {
            val player = AllServers()
            player.addServer(ServerData(name = "Minha Hypixel", originIp = "mc.hypixel.net"))
            player.save(dir)

            DedicatedSeeder.ensureServer(dir)

            val servers = readServers(dir)
            assertEquals(2, servers.serverList.size)
            assertTrue(servers.serverList.any {
                it.name == "Minha Hypixel" && it.originIp == "mc.hypixel.net"
            })
            assertTrue(servers.serverList.any {
                it.name == BuildKeys.DEDICATED_SERVER_NAME && it.originIp == BuildKeys.DEDICATED_SERVER_IP
            })
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `renames the dedicated entry when the configured name differs`() = runBlocking<Unit> {
        val dir = tempDir()
        try {
            val pack = AllServers()
            pack.addServer(ServerData(name = "Outro Nome", originIp = BuildKeys.DEDICATED_SERVER_IP))
            pack.save(dir)

            assertTrue(DedicatedSeeder.ensureServer(dir))

            val servers = readServers(dir)
            assertEquals(1, servers.serverList.size)
            assertEquals(BuildKeys.DEDICATED_SERVER_NAME, servers.serverList[0].name)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `first install applies the dedicated version config`() = runBlocking<Unit> {
        val dir = tempDir()
        try {
            DedicatedSeeder.ensureVersionConfig(dir, "default_layout.json", applyDefaults = true)

            val config = VersionConfig.parseConfig(dir)
            assertEquals(SettingState.ENABLE, config.isolationType)
            assertEquals(SettingState.DISABLE, config.skipGameIntegrityCheck)
            assertEquals(-1, config.ramAllocation)
            assertEquals("default_layout.json", config.control)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `pack update preserves the player version config`() = runBlocking<Unit> {
        val dir = tempDir()
        try {
            DedicatedSeeder.ensureVersionConfig(dir, "default_layout.json", applyDefaults = true)
            val custom = VersionConfig.parseConfig(dir).apply {
                ramAllocation = 2048
                control = "custom.json"
            }
            custom.save()

            DedicatedSeeder.ensureVersionConfig(dir, "default_layout.json", applyDefaults = false)

            val config = VersionConfig.parseConfig(dir)
            assertEquals(2048, config.ramAllocation)
            assertEquals("custom.json", config.control)
        } finally {
            dir.deleteRecursively()
        }
    }
}
```

`ControlManagerSeedTest.kt` (mesmo cabeçalho; package `com.movtery.zalithlauncher.game.control`):

```kotlin
/* (cabeçalho GPLv3 completo) */

package com.movtery.zalithlauncher.game.control

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.nio.file.Files

class ControlManagerSeedTest {

    @Test
    fun `copies the default layout only when missing`() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("control-seed").toFile()
        try {
            var copies = 0

            val first = ControlManager.ensureLayoutFile(dir) { output ->
                copies++
                output.writeText("{}")
            }

            assertEquals("default_layout.json", first.name)
            assertTrue(first.isFile)
            assertEquals(1, copies)

            val second = ControlManager.ensureLayoutFile(dir) { output ->
                copies++
                output.writeText("{}")
            }

            assertEquals("não deve copiar de novo", 1, copies)
            assertEquals(first.absolutePath, second.absolutePath)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun `leaves an existing layout untouched`() = runBlocking<Unit> {
        val dir = Files.createTempDirectory("control-seed").toFile()
        try {
            java.io.File(dir, "default_layout.json").writeText("player-layout")

            ControlManager.ensureLayoutFile(dir) { _ ->
                fail("não deveria copiar: o arquivo já existe")
            }

            assertEquals("player-layout", java.io.File(dir, "default_layout.json").readText())
        } finally {
            dir.deleteRecursively()
        }
    }
}
```

- [ ] **Step 2: Rodar para ver falhar**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*DedicatedSeederTest*" --tests "*ControlManagerSeedTest*" --console=plain`
Expected: **FAIL** no compile — `unresolved reference: DedicatedSeeder` e `ensureLayoutFile`.

- [ ] **Step 3: Implementar seeder + ControlManager**

`DedicatedSeeder.kt`:

```kotlin
/* (cabeçalho GPLv3 completo) */

package com.movtery.zalithlauncher.game.dedicated

import android.content.Context
import com.movtery.zalithlauncher.BuildKeys
import com.movtery.zalithlauncher.game.control.ControlManager
import com.movtery.zalithlauncher.game.version.installed.SettingState
import com.movtery.zalithlauncher.game.version.installed.VersionConfig
import com.movtery.zalithlauncher.game.version.multiplayer.AllServers
import com.movtery.zalithlauncher.game.version.multiplayer.ServerData
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "DedicatedSeeder"

object DedicatedSeeder {

    /**
     * Spec §7.1: garante a entrada `DEDICATED_SERVER_NAME` / `DEDICATED_SERVER_IP`
     * em `servers.dat` (NBT cru do 1.7.10). Nunca remove entradas existentes e,
     * quando nada muda, não reescreve o arquivo (atualizações ficam intactas).
     * @return true somente quando o arquivo foi alterado.
     */
    suspend fun ensureServer(versionDir: File): Boolean = withContext(Dispatchers.IO) {
        val dataFile = File(versionDir, "servers.dat")
        val servers = AllServers()
        if (dataFile.exists()) servers.loadServers(dataFile)

        val name = BuildKeys.DEDICATED_SERVER_NAME
        val ip = BuildKeys.DEDICATED_SERVER_IP
        val existing = servers.serverList.firstOrNull { it.originIp == ip }
        val changed = when {
            existing == null -> {
                servers.addServer(ServerData(name = name, originIp = ip))
                true
            }
            existing.name != name -> {
                existing.name = name
                true
            }
            else -> false
        }
        if (changed) servers.save(versionDir)
        changed
    }

    /**
     * Spec §7.3: na primeira instalação liga isolamento de versão, RAM herdada
     * (-1), o layout de controle e `skipGameIntegrityCheck=false` (nunca pular).
     * Em atualização (`applyDefaults=false`) o `version.config` não é tocado.
     */
    suspend fun ensureVersionConfig(
        versionDir: File,
        controlFileName: String,
        applyDefaults: Boolean
    ) = withContext(Dispatchers.IO) {
        if (!applyDefaults) {
            Logger.info(TAG, "version.config preserved (pack update)")
            return@withContext
        }
        val config = VersionConfig.parseConfig(versionDir)
        config.isolationType = SettingState.ENABLE
        config.skipGameIntegrityCheck = SettingState.DISABLE
        config.ramAllocation = -1
        config.control = controlFileName
        config.save()
    }
}
```

`ControlManager.kt` — const perto do `TAG` (linha ~47):

```kotlin
    private const val DEFAULT_LAYOUT_FILE_NAME = "default_layout.json"
```

Inserir logo **após** o `unpackDefaultControl` (linha ~162):

```kotlin
    /**
     * Spec §7.2: semeia o layout padrão apenas se o arquivo ainda não existir —
     * não depende do estado da pasta como o `checkDefaultAndRefresh`.
     * Devolve o nome do arquivo para `versionConfig.control`.
     */
    suspend fun ensureDefaultLayout(context: Context): String = withContext(Dispatchers.IO) {
        ensureLayoutFile(PathManager.DIR_CONTROL_LAYOUTS) { output ->
            context.copyAssetFile(fileName = DEFAULT_LAYOUT_FILE_NAME, output = output, overwrite = false)
        }.name
    }

    /** Seam testável: copia somente quando o alvo ainda não existe. */
    internal suspend fun ensureLayoutFile(dir: File, copyIfMissing: suspend (File) -> Unit): File {
        val target = File(dir, DEFAULT_LAYOUT_FILE_NAME)
        if (!target.exists()) {
            dir.mkdirs()
            copyIfMissing(target)
        }
        return target
    }
```

Nota (fato verificado): o arquivo já importa `android.content.Context` (:21), `copyAssetFile` (:29), `PathManager` (:30), `Dispatchers` (:35), `withContext` (:41) e `java.io.File` (:44) — nenhum import novo é necessário.

- [ ] **Step 4: Rodar para ver passar**

Run: mesmo comando do Step 2.
Expected: **PASS** — 8 testes verdes.

- [ ] **Step 5: Suíte completa + commit**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain`
Expected: **BUILD SUCCESSFUL**.

```bash
git add ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/dedicated/DedicatedSeeder.kt ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/control/ControlManager.kt ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/dedicated/DedicatedSeederTest.kt ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/control/ControlManagerSeedTest.kt
git commit -m "feat(dedicated): seed server, control layout and version.config"
```

---

### Task 8: Wiring das 8 etapas — `buildPhases`, `install()`, `cancelInstall()`

**Files:**
- Modify: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackInstaller.kt`
- Test: `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackInstallerTest.kt`

**Interfaces:**
- Consumes: `downloadPhase` (Task 4), `extractBasePhase` (Task 5), `overlayPhase` (Task 6), `DedicatedSeeder` + `ControlManager.ensureDefaultLayout` (Task 7), `PackManifest` (Task 3), `VersionsManager.saveCurrentVersion(versionName)` (VersionsManager.kt:232), `getVersionsHome(): String` (game/path/GamePathHome.kt:31), `executePhasesAsync(onStart, onComplete, onError, onCancel)` (coroutine/TaskFlowExecutor.kt:166).
- Produces: `TechnicPackInstaller.install(context: Context, onInstalled: () -> Unit, onCancel: () -> Unit, onError: (Throwable) -> Unit)`; `cancelInstall()`; `internal fun buildPhases(): List<TaskFlowExecutor.TaskPhase>`; `seedPhase(...)` (task `Dedicated.Seed`); `selectPhase()` (task `Dedicated.Select`). A Task 10 consome exatamente essa assinatura.

- [ ] **Step 1: Escrever o teste falhando**

Adicionar ao `TechnicPackInstallerTest.kt` (imports novos: `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.Dispatchers`):

```kotlin
    @Test
    fun `pipeline starts with download then extract and base phases`() {
        val installer = TechnicPackInstaller(CoroutineScope(Dispatchers.Unconfined))

        val phases = installer.buildPhases()

        assertEquals(2, phases.size)
        assertEquals(3, phases[0].tasks.size)   // ClearTemp + ResolvePack + Download
        assertEquals(3, phases[1].tasks.size)   // Extract + ResolveVersion + InstallBase
    }
```

- [ ] **Step 2: Rodar para ver falhar**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*TechnicPackInstallerTest*" --console=plain`
Expected: **FAIL** no compile — `unresolved reference: buildPhases`.

- [ ] **Step 3: Implementar o wiring**

Imports novos no `TechnicPackInstaller.kt`: `com.movtery.zalithlauncher.game.control.ControlManager`, `com.movtery.zalithlauncher.game.dedicated.DedicatedSeeder`, `com.movtery.zalithlauncher.game.path.getVersionsHome`.

Campos/método auxiliar na classe:

```kotlin
    /** `versions/<slug>` — alvo do overlay e do seed (isolation ativa). */
    private fun versionDir(): File = File(getVersionsHome(), BuildKeys.DEDICATED_PACK_SLUG)
```

**Alterar o final do task `Dedicated.InstallBase`** (Task 5) para anexar o resto do pipeline junto com as fases do `GameInstaller`:

```kotlin
            gameInstaller = installer
            // Fases do GameInstaller + as nossas 6-8, anexadas em tempo de execução
            // (mesmo padrão do ModPackInstaller.kt:241-280).
            val oldManifest = PackManifest.read(versionDir())
            taskExecutor.addPhases(
                installer.getTaskPhase(createIsolation = true) + listOf(
                    overlayPhase(stagingDirectory(), versionDir(), oldManifest),
                    seedPhase(
                        context = requireNotNull(appContext) { "install() was not called yet" },
                        stagingDir = stagingDirectory(),
                        versionDir = versionDir(),
                        // primeira instalação aplica os defaults; update preserva o version.config
                        applyDefaults = oldManifest == null
                    ),
                    selectPhase()
                )
            )
            task.updateProgress(1f)
```

Métodos novos na classe:

```kotlin
    /** Etapas 7 (§5.2 + §7): layout, version.config, servidor — e o manifesto POR ÚLTIMO. */
    internal fun seedPhase(
        context: Context,
        stagingDir: File,
        versionDir: File,
        applyDefaults: Boolean
    ): TaskFlowExecutor.TaskPhase = buildPhase {
        addTask(id = "Dedicated.Seed", title = androidText(R.string.dedicated_task_seed)) { task ->
            val controlFile = ControlManager.ensureDefaultLayout(context)
            DedicatedSeeder.ensureVersionConfig(versionDir, controlFile, applyDefaults)
            DedicatedSeeder.ensureServer(versionDir)
            PackManifest.write(
                versionDir,
                PackManifest(
                    slug = BuildKeys.DEDICATED_PACK_SLUG,
                    packVersion = apiIdentity,
                    files = PackManifest.packFiles(stagingDir)
                )
            )
            task.updateProgress(1f)
        }
    }

    /** Etapa 8 (§5.2): seleciona a versão instalada como atual. */
    internal fun selectPhase(): TaskFlowExecutor.TaskPhase = buildPhase {
        addTask(id = "Dedicated.Select", title = androidText(R.string.dedicated_task_select)) { task ->
            VersionsManager.saveCurrentVersion(BuildKeys.DEDICATED_PACK_SLUG)
            task.updateProgress(1f)
        }
    }

    /** Etapas 1-5 estáticas; 6-8 entram dinamicamente depois da base (InstallBase). */
    internal fun buildPhases(): List<TaskFlowExecutor.TaskPhase> = listOf(
        downloadPhase(),
        extractBasePhase()
    )

    /** Pipeline completo (spec §5.2). Erros caem em onError; cancelamento em onCancel. */
    fun install(
        context: Context,
        onInstalled: () -> Unit,
        onCancel: () -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (taskExecutor.isRunning()) return
        appContext = context.applicationContext
        taskExecutor.executePhasesAsync(
            onStart = { taskExecutor.addPhases(buildPhases()) },
            onComplete = { onInstalled() },
            onCancel = { onCancel() },
            onError = { e -> onError(e) }
        )
    }

    fun cancelInstall() {
        taskExecutor.cancel()
        gameInstaller?.cancelInstall(clearTarget = false)
    }
```

- [ ] **Step 4: Rodar para ver passar**

Run: mesmo comando do Step 2.
Expected: **PASS** — 7 testes verdes.

- [ ] **Step 5: Suíte completa + commit**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain`
Expected: **BUILD SUCCESSFUL**.

```bash
git add ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackInstaller.kt ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/download/modpack/technic/TechnicPackInstallerTest.kt
git commit -m "feat(technic): wire the eight step install pipeline"
```

---

### Task 9: `DedicatedPackState` — máquina de estados pura

**Files:**
- Create: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/dedicated/DedicatedPackState.kt`
- Test: `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/dedicated/DedicatedPackStateTest.kt`

**Interfaces:**
- Consumes: `PackManifest` + `identityOf` (Task 3), `TechnicApi.BuildRejectedException` (Task 1), `InsufficientSpaceException` (Task 5), `AndroidStringText` + `androidText(@StringRes)` (ui/AndroidStringText.kt:117-143), `Logger.warning(tag, msg)` (utils/logging).
- Produces: `sealed interface PackState` (`Checking`, `Offline`, `NotInstalled`, `NeedsUpdate(installedVersion, availableVersion)`, `Ready`, `Installing`, `Failed(message: AndroidStringText)`); `sealed interface DedicatedEvent`; `enum class ErrorKind { OFFLINE, API_BUILD_REJECTED, INSUFFICIENT_SPACE, GENERIC }`; objeto `DedicatedPackState` com `state: StateFlow<PackState>`, `internal fun dispatch(event)`, `internal fun reduce(current, event)`, `internal fun classifyCheck(manifest, versionJsonPresent, apiIdentity)`, `internal fun classifyError(throwable)`. A Task 10 adiciona `launchCheck/check/install/cancelInstall/tasks/logOutput`.

- [ ] **Step 1: Escrever o teste falhando**

```kotlin
/* (cabeçalho GPLv3 completo) */

package com.movtery.zalithlauncher.game.dedicated

import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.download.engine.AllSourcesFailedException
import com.movtery.zalithlauncher.game.download.modpack.technic.InsufficientSpaceException
import com.movtery.zalithlauncher.game.download.modpack.technic.TechnicApi
import com.movtery.zalithlauncher.ui.androidText
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

class DedicatedPackStateTest {

    private val installed = PackManifest("dbc-super-oficial", "10.8", emptyList())

    @Test
    fun `check flow goes not installed to installing to ready`() {
        var state: PackState = PackState.Checking

        state = DedicatedPackState.reduce(state, DedicatedEvent.CheckCompleted(installed = null, versionJsonPresent = true, apiIdentity = "10.8"))
        assertEquals(PackState.NotInstalled, state)

        state = DedicatedPackState.reduce(state, DedicatedEvent.InstallRequested)
        assertEquals(PackState.Installing, state)

        state = DedicatedPackState.reduce(state, DedicatedEvent.InstallSucceeded)
        assertEquals(PackState.Ready, state)
    }

    @Test
    fun `failed can go back to installing`() {
        var state: PackState = PackState.Checking
        state = DedicatedPackState.reduce(state, DedicatedEvent.CheckFailed(androidText(R.string.dedicated_error_generic)))
        assertTrue(state is PackState.Failed)

        state = DedicatedPackState.reduce(state, DedicatedEvent.InstallRequested)
        assertEquals(PackState.Installing, state)
    }

    @Test
    fun `double install request is ignored`() {
        val installing: PackState = PackState.Installing
        assertSame(installing, DedicatedPackState.reduce(installing, DedicatedEvent.InstallRequested))
    }

    @Test
    fun `matching identity is ready`() {
        assertEquals(PackState.Ready, DedicatedPackState.classifyCheck(installed, true, "10.8"))
    }

    @Test
    fun `different identity requests an update`() {
        val state = DedicatedPackState.classifyCheck(installed, true, "10.9")
        assertTrue(state is PackState.NeedsUpdate)
        state as PackState.NeedsUpdate
        assertEquals("10.8", state.installedVersion)
        assertEquals("10.9", state.availableVersion)
    }

    @Test
    fun `empty api identity stays ready`() {
        // spec §5.1: dois lados vazios ⇒ READY com aviso, nunca loop de update
        assertEquals(PackState.Ready, DedicatedPackState.classifyCheck(installed, true, null))
    }

    @Test
    fun `deleted version json is not installed`() {
        // Review Focus 4: manifesto existe mas o json da versão sumiu ⇒ instalar de novo
        assertEquals(PackState.NotInstalled, DedicatedPackState.classifyCheck(installed, false, "10.8"))
    }

    @Test
    fun `offline and failed events land in the right state`() {
        assertEquals(PackState.Offline,
            DedicatedPackState.reduce(PackState.Checking, DedicatedEvent.CheckOffline))
        val failed = DedicatedPackState.reduce(PackState.Checking,
            DedicatedEvent.CheckFailed(androidText(R.string.dedicated_error_api_build)))
        assertTrue(failed is PackState.Failed)
    }

    @Test
    fun `errors are classified distinctly`() {
        // Review Focus 5: 401 nunca vira offline; offline nunca some
        assertEquals(ErrorKind.API_BUILD_REJECTED,
            DedicatedPackState.classifyError(TechnicApi.BuildRejectedException()))
        assertEquals(ErrorKind.OFFLINE,
            DedicatedPackState.classifyError(UnknownHostException("api.technicpack.net")))
        assertEquals(ErrorKind.OFFLINE,
            DedicatedPackState.classifyError(
                AllSourcesFailedException("https://dropbox/x.zip", UnknownHostException("dropbox.com"))))
        assertEquals(ErrorKind.INSUFFICIENT_SPACE,
            DedicatedPackState.classifyError(InsufficientSpaceException("need 200 MB")))
        assertEquals(ErrorKind.GENERIC,
            DedicatedPackState.classifyError(IOException("disk exploded")))
    }

    @Test
    fun `identity falls back to url then to nothing`() {
        assertEquals("10.8", PackManifest.identityOf("10.8", "https://dl/x.zip"))
        assertEquals("https://dl/x.zip", PackManifest.identityOf(null, "https://dl/x.zip"))
        assertNull(PackManifest.identityOf(null, null))
    }

    @Test
    fun `initial state is checking`() {
        // Asserção de sanidade do fluxo de abertura: nunca READY sem passar pelo check
        assertSame(PackState.Checking, DedicatedPackState.state.value)
    }
}
```

Nota (fatos verificados): `AllSourcesFailedException(summary: String, cause: Throwable?)` vive em `game/download/engine/DownloadRequest.kt:52` — o construtor usado no teste está correto.

- [ ] **Step 2: Rodar para ver falhar**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*DedicatedPackStateTest*" --console=plain`
Expected: **FAIL** no compile — `unresolved reference: DedicatedPackState`.

- [ ] **Step 3: Implementar a máquina**

`DedicatedPackState.kt` (primeira versão — só a parte pura; a Task 10 acrescenta `check/install`):

```kotlin
/* (cabeçalho GPLv3 completo) */

package com.movtery.zalithlauncher.game.dedicated

import com.movtery.zalithlauncher.game.download.modpack.technic.InsufficientSpaceException
import com.movtery.zalithlauncher.game.download.modpack.technic.TechnicApi
import com.movtery.zalithlauncher.ui.AndroidStringText
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.ConnectException
import java.net.UnknownHostException

/** Estados da tela dedicada (spec §3/§5.1). */
sealed interface PackState {
    data object Checking : PackState
    data object Offline : PackState
    data object NotInstalled : PackState
    data class NeedsUpdate(val installedVersion: String, val availableVersion: String) : PackState
    data object Ready : PackState
    data object Installing : PackState
    data class Failed(val message: AndroidStringText) : PackState
}

/** Eventos puros da máquina de estados (spec §9 teste 5). */
sealed interface DedicatedEvent {
    data object CheckStarted : DedicatedEvent
    data class CheckCompleted(
        val installed: PackManifest?,
        val versionJsonPresent: Boolean,
        val apiIdentity: String?
    ) : DedicatedEvent
    data object CheckOffline : DedicatedEvent
    data class CheckFailed(val message: AndroidStringText) : DedicatedEvent
    data object InstallRequested : DedicatedEvent
    data object InstallSucceeded : DedicatedEvent
    data class InstallFailed(val message: AndroidStringText) : DedicatedEvent
}

/** Tipo de falha puro; o mapeamento para string fica na wiring (spec §5.5). */
enum class ErrorKind { OFFLINE, API_BUILD_REJECTED, INSUFFICIENT_SPACE, GENERIC }

object DedicatedPackState {
    private const val TAG = "DedicatedPackState"

    private val _state = MutableStateFlow<PackState>(PackState.Checking)
    val state: StateFlow<PackState> = _state.asStateFlow()

    internal fun dispatch(event: DedicatedEvent) {
        _state.update { current -> reduce(current, event) }
    }

    /** Transições puras (spec §9 teste 5): CHECKING→NOT_INSTALLED→INSTALLING→READY e →FAILED→INSTALLING. */
    internal fun reduce(current: PackState, event: DedicatedEvent): PackState = when (event) {
        DedicatedEvent.CheckStarted -> PackState.Checking
        is DedicatedEvent.CheckCompleted ->
            classifyCheck(event.installed, event.versionJsonPresent, event.apiIdentity)
        DedicatedEvent.CheckOffline -> PackState.Offline
        is DedicatedEvent.CheckFailed -> PackState.Failed(event.message)
        // dup-tap: não reinicia uma instalação em andamento
        DedicatedEvent.InstallRequested -> if (current is PackState.Installing) current else PackState.Installing
        DedicatedEvent.InstallSucceeded -> PackState.Ready
        is DedicatedEvent.InstallFailed -> PackState.Failed(event.message)
    }

    /**
     * §5.1: sem manifesto (ou sem o json da versão — base apagada) = instalar;
     * identidade nula = READY com aviso (nunca loop de update); igual = READY;
     * diferente = atualizar.
     */
    internal fun classifyCheck(
        manifest: PackManifest?,
        versionJsonPresent: Boolean,
        apiIdentity: String?
    ): PackState {
        if (manifest == null || !versionJsonPresent) return PackState.NotInstalled
        if (apiIdentity == null) {
            Logger.warning(TAG, "Pack identity empty on both sides; treating as READY")
            return PackState.Ready
        }
        return if (manifest.packVersion == apiIdentity) PackState.Ready
        else PackState.NeedsUpdate(manifest.packVersion, apiIdentity)
    }

    /** Separa queda de rede, rejeição de build (401) e erro comum (spec §5.5). */
    internal fun classifyError(throwable: Throwable): ErrorKind {
        var current: Throwable? = throwable
        while (current != null) {
            when {
                current is TechnicApi.BuildRejectedException -> return ErrorKind.API_BUILD_REJECTED
                current is InsufficientSpaceException -> return ErrorKind.INSUFFICIENT_SPACE
                current is UnknownHostException || current is ConnectException -> return ErrorKind.OFFLINE
            }
            current = current.cause
        }
        return ErrorKind.GENERIC
    }
}
```

Nota (fato verificado): `Logger.warning(tag, msg, t?)` existe em `utils/logging/Logger.kt:213` — a chamada acima está correta.

- [ ] **Step 4: Rodar para ver passar**

Run: mesmo comando do Step 2.
Expected: **PASS** — 11 testes verdes.

- [ ] **Step 5: Suíte completa + commit**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain`
Expected: **BUILD SUCCESSFUL**.

```bash
git add ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/dedicated/DedicatedPackState.kt ZalithLauncher/src/test/java/com/movtery/zalithlauncher/game/dedicated/DedicatedPackStateTest.kt
git commit -m "feat(dedicated): add pack state machine and classification rules"
```

---

### Task 10: Wiring de `check()` e `install()` no estado

**Files:**
- Modify: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/dedicated/DedicatedPackState.kt`
- Test: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain` (suíte completa — gate)

**Interfaces:**
- Consumes: `PathManager.refreshPaths(context)` (path/PathManager.kt:67), `GamePathManager.reloadPath()` (:82) + `waitForRefresh()` (:115), `VersionsManager.waitForRefresh()`, `TechnicApi.getPack` (Task 1), `TechnicPackInstaller(scope).install(context, onInstalled, onCancel, onError)` + `tasksFlow` + `logOutput` + `cancelInstall()` (Task 8), `androidText` + strings `dedicated_*` (Task 4).
- Produces (API final consumido pela Task 11): `DedicatedPackState.launchCheck(context: Context)`, `DedicatedPackState.install(context: Context)`, `DedicatedPackState.cancelInstall()`, `DedicatedPackState.tasks: StateFlow<List<TitledTask>>`, `DedicatedPackState.logOutput: StateFlow<TaskLogOutput?>`.

- [ ] **Step 1: Implementar a wiring**

Imports novos: `android.content.Context`, `com.movtery.zalithlauncher.BuildKeys`, `com.movtery.zalithlauncher.R`, `com.movtery.zalithlauncher.coroutine.TitledTask`, `com.movtery.zalithlauncher.coroutine.TaskLogOutput` (confirme o pacote em `GameInstaller.kt`), `com.movtery.zalithlauncher.game.download.modpack.technic.TechnicPackInstaller`, `com.movtery.zalithlauncher.game.path.GamePathManager`, `com.movtery.zalithlauncher.game.path.getVersionsHome`, `com.movtery.zalithlauncher.game.version.installed.VersionsManager`, `com.movtery.zalithlauncher.path.PathManager`, `com.movtery.zalithlauncher.ui.androidText`, `kotlinx.coroutines.CoroutineScope`, `kotlinx.coroutines.Dispatchers`, `kotlinx.coroutines.Job`, `kotlinx.coroutines.SupervisorJob`, `kotlinx.coroutines.flow.MutableStateFlow`, `kotlinx.coroutines.launch`, `java.io.File`.

Adicionar dentro do objeto `DedicatedPackState`:

```kotlin
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var installer: TechnicPackInstaller? = null
    private var appContext: Context? = null
    private var collectorJobs: List<Job> = emptyList()

    private val _tasks = MutableStateFlow<List<TitledTask>>(emptyList())
    val tasks: StateFlow<List<TitledTask>> = _tasks.asStateFlow()

    private val _logOutput = MutableStateFlow<TaskLogOutput?>(null)
    val logOutput: StateFlow<TaskLogOutput?> = _logOutput.asStateFlow()

    /**
     * Ordem obrigatória (spec §5.1): refreshPaths → reloadPath/waitForRefresh →
     * VersionsManager.waitForRefresh — só depois disso ler arquivo de jogo.
     * Chamado pela MainActivity na abertura e pelo botão "Tentar de novo".
     */
    fun launchCheck(context: Context) {
        appContext = context.applicationContext
        dispatch(DedicatedEvent.CheckStarted)
        scope.launch { check(context.applicationContext) }
    }

    internal suspend fun check(context: Context) {
        try {
            PathManager.refreshPaths(context)
            GamePathManager.reloadPath()
            GamePathManager.waitForRefresh()
            VersionsManager.waitForRefresh()

            val slug = BuildKeys.DEDICATED_PACK_SLUG
            val versionDir = File(getVersionsHome(), slug)
            val manifest = PackManifest.read(versionDir)
            val versionJsonPresent = VersionsManager.isVersionExists(slug, true)

            val pack = try {
                TechnicApi.getPack(slug)
            } catch (t: Throwable) {
                dispatch(checkEvent(t))
                return
            }

            val identity = PackManifest.identityOf(pack.version, pack.url)
            dispatch(DedicatedEvent.CheckCompleted(manifest, versionJsonPresent, identity))
        } catch (t: Throwable) {
            dispatch(checkEvent(t))
        }
    }

    private fun checkEvent(t: Throwable): DedicatedEvent = when (classifyError(t)) {
        ErrorKind.OFFLINE -> DedicatedEvent.CheckOffline
        ErrorKind.API_BUILD_REJECTED -> DedicatedEvent.CheckFailed(androidText(R.string.dedicated_error_api_build))
        ErrorKind.INSUFFICIENT_SPACE -> DedicatedEvent.CheckFailed(androidText(R.string.dedicated_error_no_space))
        ErrorKind.GENERIC -> DedicatedEvent.CheckFailed(androidText(R.string.dedicated_error_generic))
    }

    /** Inicia instalação/atualização (etapas 1-8 da spec §5.2). */
    fun install(context: Context) {
        if (_state.value is PackState.Installing) return
        appContext = context.applicationContext
        dispatch(DedicatedEvent.InstallRequested)

        val inst = TechnicPackInstaller(scope)
        installer = inst
        collectorJobs.forEach { it.cancel() }
        collectorJobs = listOf(
            scope.launch { inst.tasksFlow.collect { _tasks.value = it } },
            scope.launch { inst.logOutput.collect { _logOutput.value = it } }
        )
        inst.install(
            context = context.applicationContext,
            onInstalled = { dispatch(DedicatedEvent.InstallSucceeded) },
            onCancel = { appContext?.let { c -> launchCheck(c) } },
            onError = { e ->
                if (classifyError(e) == ErrorKind.OFFLINE) dispatch(DedicatedEvent.CheckOffline)
                else dispatch(DedicatedEvent.InstallFailed(errorMessage(e)))
            }
        )
    }

    private fun errorMessage(t: Throwable): AndroidStringText = when (classifyError(t)) {
        ErrorKind.API_BUILD_REJECTED -> androidText(R.string.dedicated_error_api_build)
        ErrorKind.INSUFFICIENT_SPACE -> androidText(R.string.dedicated_error_no_space)
        else -> androidText(R.string.dedicated_error_generic)
    }

    fun cancelInstall() {
        installer?.cancelInstall()
    }
```

Notas de intenção:
- `check()` lê o manifesto **e** o json da versão (`isVersionExists(slug, true)`) — se o jogador apagar `versions/<slug>/<slug>.json`, o estado volta a `NotInstalled` em vez de travar `READY` sem saída (Review Focus 4).
- 401 (`BuildRejectedException`) vira `FAILED` com `dedicated_error_api_build` explícito; `UnknownHost` vira `Offline` com "Tentar de novo" (Review Focus 5).
- Cancelamento do diálogo → `launchCheck()` recalcula o estado verdadeiro (manifesto não gravado ⇒ `NotInstalled`/`NeedsUpdate`).

- [ ] **Step 2: Rodar a suíte completa**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain`
Expected: **BUILD SUCCESSFUL** (compilação + 112+ testes verdes; esta task não tem teste novo — o gate é a suíte inteira porque tudo aqui é wiring Android puro).

- [ ] **Step 3: Commit**

```bash
git add ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/dedicated/DedicatedPackState.kt
git commit -m "feat(dedicated): wire check and install flows into the state"
```

---

### Task 11: `DedicatedScreen` — a tela única

**Files:**
- Create: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/dedicated/DedicatedScreen.kt`
- Test: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain` (suíte completa) + `assembleDebug` (gate)

**Interfaces:**
- Consumes: `DedicatedPackState.state/tasks/logOutput/install/launchCheck/cancelInstall` (Task 10), `PackState` (Task 9), `TitleTaskFlowDialog(title: String, tasks: List<TitledTask>, onCancel: () -> Unit = {}, logOutput: TaskLogOutput? = null)` (ui/screens/content/elements/CommonElements.kt:356), `AndroidStringText(text, style, softWrap, maxLines)` composable (ui/AndroidStringText.kt:135), strings `dedicated_*` + `unofficial_modified_notice` (Task 4 / Fase 1).
- Produces: `@Composable fun DedicatedScreen(onLaunchGame: () -> Unit, modifier: Modifier = Modifier)` — consumido pela Task 12 no entry `LauncherMain`.

- [ ] **Step 1: Implementar a tela**

```kotlin
/* (cabeçalho GPLv3 completo) */

package com.movtery.zalithlauncher.ui.screens.content.dedicated

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.game.dedicated.DedicatedPackState
import com.movtery.zalithlauncher.game.dedicated.PackState
import com.movtery.zalithlauncher.ui.AndroidStringText
import com.movtery.zalithlauncher.ui.screens.content.elements.TitleTaskFlowDialog

/**
 * Tela única do launcher dedicado (spec §6): ícone do pack, nome, aviso de
 * versão modificada e um botão grande. Não há lista de versões nem navegação.
 */
@Composable
fun DedicatedScreen(
    onLaunchGame: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val state by DedicatedPackState.state.collectAsStateWithLifecycle()
    val tasks by DedicatedPackState.tasks.collectAsStateWithLifecycle()
    val logOutput by DedicatedPackState.logOutput.collectAsStateWithLifecycle()

    if (tasks.isNotEmpty()) {
        TitleTaskFlowDialog(
            title = stringResource(R.string.dedicated_installing_title),
            tasks = tasks,
            onCancel = { DedicatedPackState.cancelInstall() },
            logOutput = logOutput
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Image(
            painter = painterResource(R.mipmap.ic_launcher),
            contentDescription = null,
            modifier = Modifier.size(112.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.dedicated_pack_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(8.dp))
        // Aviso obrigatório exibido incondicionalmente (R6 / §8)
        Text(
            text = stringResource(R.string.unofficial_modified_notice),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        when (val current = state) {
            PackState.Checking, PackState.Installing -> {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
            PackState.Ready -> {
                DedicatedButton(text = stringResource(R.string.dedicated_play), onClick = onLaunchGame)
            }
            PackState.NotInstalled -> {
                DedicatedButton(
                    text = stringResource(R.string.dedicated_install),
                    onClick = { DedicatedPackState.install(context) }
                )
            }
            is PackState.NeedsUpdate -> {
                Text(
                    text = "${current.installedVersion} → ${current.availableVersion}",
                    style = MaterialTheme.typography.bodySmall
                )
                Spacer(Modifier.height(8.dp))
                DedicatedButton(
                    text = stringResource(R.string.dedicated_update),
                    onClick = { DedicatedPackState.install(context) }
                )
            }
            PackState.Offline -> {
                Text(
                    text = stringResource(R.string.dedicated_offline),
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                DedicatedButton(
                    text = stringResource(R.string.dedicated_retry),
                    onClick = { DedicatedPackState.launchCheck(context) }
                )
            }
            is PackState.Failed -> {
                AndroidStringText(
                    text = current.message,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                DedicatedButton(
                    text = stringResource(R.string.dedicated_retry),
                    onClick = { DedicatedPackState.launchCheck(context) }
                )
            }
        }
    }
}

@Composable
private fun DedicatedButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
    ) {
        Text(text = text, style = MaterialTheme.typography.titleMedium)
    }
}
```

Nota (fato verificado): a assinatura real é `AndroidStringText(text, modifier, autoSize, fontSize, ..., textAlign: TextAlign? = null, ..., softWrap, maxLines, style: TextStyle = LocalTextStyle.current)` (`ui/AndroidStringText.kt:194`) — os argumentos usados acima (`text`, `textAlign`, `style`) são válidos. O import resolve para o composable no contexto de composição (mesmo padrão do resto do projeto).

- [ ] **Step 2: Suíte completa + build**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain`
Expected: **BUILD SUCCESSFUL**.

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:assembleDebug --console=plain`
Expected: **BUILD SUCCESSFUL** (a tela ainda não é exibida — o entry vem na Task 12 — mas precisa compilar e o APK precisa montar).

- [ ] **Step 3: Commit**

```bash
git add ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/dedicated/DedicatedScreen.kt
git commit -m "feat(ui): add the dedicated home screen"
```

---

### Task 12: Travamento da navegação — todos os gates do §6

**Files:**
- Modify: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/main/MainScreen.kt:406` (pasta), `:415` (Multiplayer), `:424` (Download), `:522-539` (entry `LauncherMain`)
- Modify: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/DownloadScreen.kt:83-90` (`navigateToDownload`)
- Modify: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/elements/LauncherElements.kt:171` (`NoVersion`)
- Modify: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/activities/MainActivity.kt:350` (`toVersionManageScreen`)
- Modify: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/home/HomeCards.kt:69` (`userCardTypes`)
- Test: `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/ui/screens/content/DedicatedLockTest.kt`

**Interfaces:**
- Consumes: `BuildKeys.DEDICATED_MODE` (build.gradle.kts:220), `DedicatedScreen(onLaunchGame)` (Task 11), `ScreenBackStackViewModel.mainScreen.currentKey` (viewmodel/ScreenBackStackViewModel.kt:25), `HomeCards.userCardTypes` (HomeCards.kt:69).
- Produces: `LauncherMain` renderiza `DedicatedScreen` no modo dedicado; `navigateToDownload()` vira no-op; `userCardTypes` vazio; TopBar sem Download/Multiplayer/pasta; `NoVersion` e `toVersionManageScreen` não navegam. `DedicatedLockTest` prende os dois gates testáveis.

- [ ] **Step 1: Escrever o teste falhando**

```kotlin
/* (cabeçalho GPLv3 completo) */

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
    }

    @Test
    fun `dedicated mode hides the home cards`() {
        assertTrue(BuildKeys.DEDICATED_MODE)
        assertTrue("nenhum card navegável na home dedicada", HomeCards.userCardTypes.isEmpty())
    }
}
```

Nota (fatos verificados): `NestedNavKey` é `ui/screens/NestedNavKey.kt:32`, `HomeCards` é `object` em `ui/screens/content/home/HomeCards.kt:48` e `ScreenBackStackViewModel : ViewModel()` em `viewmodel/ScreenBackStackViewModel.kt:25` — os imports acima estão corretos como escritos.

- [ ] **Step 2: Rodar para ver falhar**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*DedicatedLockTest*" --console=plain`
Expected: **FAIL** — `navigateToDownload` ainda navega (`currentKey is Download`) e `userCardTypes` ainda tem o card de versão.

- [ ] **Step 3: Implementar os gates**

**(a) `MainScreen.kt` — entry `LauncherMain` (:522-539):** envolver o corpo existente:

```kotlin
entry<NormalNavKey.LauncherMain> {
    if (BuildKeys.DEDICATED_MODE) {
        DedicatedScreen(
            onLaunchGame = {
                eventViewModel.sendEvent(EventViewModel.Event.Launch.Game(null))
            }
        )
    } else {
        // ... corpo atual do LauncherScreen, inalterado ...
    }
}
```

(+ import `com.movtery.zalithlauncher.ui.screens.content.dedicated.DedicatedScreen` — `BuildKeys` já está importado em MainScreen.kt:75.)

**(b) `MainScreen.kt` — `TopBar`:** embrulhar cada um dos três botões:

```kotlin
if (!BuildKeys.DEDICATED_MODE) {
    IconButton(onClick = openFileManager) { Icon(painterResource(R.drawable.ic_folder_filled), contentDescription = null) }
}
if (!BuildKeys.DEDICATED_MODE) {
    TopBarRailItem(/* Multiplayer — corpo atual, inalterado */)
}
if (!BuildKeys.DEDICATED_MODE) {
    TopBarRailItem(/* Download — corpo atual, inalterado */)
}
```

**(c) `DownloadScreen.kt:83` — funil único de downloads:**

```kotlin
fun ScreenBackStackViewModel.navigateToDownload(targetScreen: TitledNavKey? = null) {
    // §6: modo dedicado — funil de downloads vira no-op (escondida E guardada)
    if (BuildKeys.DEDICATED_MODE) return

    downloadScreen.clearWith(targetScreen ?: downloadGameScreen)
    mainScreen.removeAndNavigateTo(
        removes = clearBeforeNavKeys,
        screenKey = downloadScreen,
        useClassEquality = true
    )
}
```

(+ import `com.movtery.zalithlauncher.BuildKeys`.) Esse único ponto cobre o botão da TopBar, `VersionsManageScreen.onInstall:319` e os chamadores de `VersionSettingsScreen` — todos passam por ele.

**(d) `LauncherElements.kt:171` — falha de launch sem versão:**

```kotlin
is LaunchGameOperation.NoVersion -> {
    LaunchedEffect(Unit) {
        eventViewModel.sendToast(androidText(R.string.game_launch_no_version))
        // §6: no modo dedicado não escapa para o gerenciador de versões
        if (!BuildKeys.DEDICATED_MODE) toVersionManageScreen()
        launchGameViewModel.updateOperation(LaunchGameOperation.None)
    }
}
```

**(e) `MainActivity.kt:350` — caminho de erro para o gerenciador:**

```kotlin
toVersionManageScreen = {
    if (!BuildKeys.DEDICATED_MODE) {
        screenBackStackModel.mainScreen.removeAndNavigateTo(
            remove = NestedNavKey.VersionSettings::class,
            screenKey = NormalNavKey.VersionsManager
        )
    }
},
```

**(f) `HomeCards.kt:69`:**

```kotlin
val userCardTypes: List<CardType> =
    if (BuildKeys.DEDICATED_MODE) emptyList() else listOf(versionCardType)
```

(+ import `com.movtery.zalithlauncher.BuildKeys`.)

Nota de completude: `LauncherScreen` (dropdown `VersionsContent`, long-press, `VersionsManageScreen`) não é composto no modo dedicado pelo gate **(a)** — essas rotas ficam inalcançáveis estruturalmente; os gates **(c)(d)(e)** cobrem os caminhos de erro/deep-link que existiriam mesmo sem a tela, e **(b)** remove os atalhos visíveis. Nenhuma rota de download fica acessível por botão, deep link ou erro de launch (testado manualmente na Task 13, item 5 da spec).

- [ ] **Step 4: Rodar para ver passar**

Run: mesmo comando do Step 2.
Expected: **PASS** — 2 testes verdes.

- [ ] **Step 5: Suíte completa + build + commit**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain`
Expected: **BUILD SUCCESSFUL**.

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:assembleDebug --console=plain`
Expected: **BUILD SUCCESSFUL**.

```bash
git add ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/main/MainScreen.kt ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/DownloadScreen.kt ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/elements/LauncherElements.kt ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/activities/MainActivity.kt ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/home/HomeCards.kt ZalithLauncher/src/test/java/com/movtery/zalithlauncher/ui/screens/content/DedicatedLockTest.kt
git commit -m "feat(ui): lock navigation to the dedicated pack"
```

---

### Task 13: Disparo do check na abertura + verificação final

**Files:**
- Modify: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/activities/MainActivity.kt` (logo após o `refreshData()` em `onCreate`, linha ~196)

**Interfaces:**
- Consumes: `DedicatedPackState.launchCheck(context)` (Task 10), `BuildKeys.DEDICATED_MODE`.

- [ ] **Step 1: Ligar o check na abertura**

Em `MainActivity.onCreate`, logo **após** a chamada `refreshData()` (~linha 196):

```kotlin
if (BuildKeys.DEDICATED_MODE) {
    DedicatedPackState.launchCheck(this)
}
```

(+ import `com.movtery.zalithlauncher.game.dedicated.DedicatedPackState`.)

- [ ] **Step 2: Verificação automatizada completa**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain`
Expected: **BUILD SUCCESSFUL** — suíte inteira verde (101 da Fase 1/Java8 + ~40 novos testes deste plano).

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:assembleDebug --console=plain`
Expected: **BUILD SUCCESSFUL** → APK em `ZalithLauncher/build/outputs/apk/debug/`.

- [ ] **Step 3: Checklist manual no aparelho (especificação §9 — testes 1 e 6)**

Instalar com `adb install -r` e verificar (adb disponível: `am start ... -c android.intent.category.HOME` + `screencap` funcionam; `input`/`keyevent` estão bloqueados no MIUI — usar toque manual quando necessário):

1. **Primeira abertura:** `DedicatedScreen` com ícone do pack, nome "DBC Super (Oficial)", aviso `Unofficial Modified Version by Hakkaiz` visível e botão **Instalar**; nenhum card de versão na home; TopBar sem Download/Multiplayer/pasta.
2. **Instalação:** progresso % + velocidade no diálogo de tarefas; ao final, botão **Jogar** e versão atual = `dbc-super-oficial`.
3. **In-game:** servidor "Minecraft Server" / `dbcsuper.com` presente na lista.
4. **Segunda abertura:** direto em **Jogar** (READY), sem download (spec §9 teste 2).
5. **Travamento (spec §9 teste 6):** nenhuma rota de download acessível — botão da TopBar ausente, card de versão ausente, long-press/dropdown da home não existem (tela não composta), `NoVersion` não navega, deep link para download cai no no-op.
6. **Ligar sem rede:** tela **Offline** com **Tentar de novo**.

Itens que ficaram bloqueados por ambiente (registrar como pendentes, não rejeitar o plano): `adb input`/`keyevent` (INJECT_EVENTS do MIUI) e login Microsoft (falta `oauth_client_id` — config, fora do escopo deste plano).

- [ ] **Step 4: Commit + push**

```bash
git add ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/activities/MainActivity.kt
git commit -m "feat(dedicated): run pack check at startup and verify the phase"
git push origin main
```

- [ ] **Step 5: Reportar conclusão**

Reportar: horário de conclusão em **America/São_Paulo**, estado da suíte (número de testes verdes), hash do push e a lista do checklist manual com PENDING/OK. Declarar que a Fase 6 (§5.5 polimento de erros + testes manuais 2–5) é o Plano 3.

---

## Self-Review Checklist (executor: confira antes do handoff)

- [ ] Spec coberta: §5.2 etapas 1–8 → Tasks 4, 5, 6, 7, 8; §5.1 → Tasks 9, 10; §6 → Task 12; §7 → Task 7; §9 testes 1–5 → Tasks 1, 2, 3, 7, 9; §12 Fases 2–5 → plano inteiro (Fase 6 = Plano 3).
- [ ] Review Focus: 5 itens, cada um com teste em uma task dona do código (T4, T6+T3, T3+T6, T9, T9).
- [ ] Nenhum placeholder: sem "TODO", sem "adicione o que faltar"; TODOs de assinatura são apontamentos verificáveis (file:line fornecido).
- [ ] Constraints do projeto preservadas: GPLv3 headers, `skipGameIntegrityCheck=false`, `translatable="false"` no aviso, só Java 8, BuildKeys consumidos como `BuildKeys.*`, strings EN + pt-BR.
- [ ] Ordenação sem dependências para frente: T1→T13 só referenciam números anteriores ou código existente do projeto (com file:line).
- [ ] Cada task: teste vermelho → implementação → teste verde → suíte completa → commit convencional; push só na T13.
