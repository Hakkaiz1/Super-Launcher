# Super Launcher — Fase 1: Identidade e Ícone — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Give the build its own identity — name, version, mandatory modified-version notice, new APK icon and committed Android SDK environment — so every later phase ships inside an app that is already recognisably "Super Launcher".

**Architecture:** Identity is configuration, not code: `gradle.properties` feeds `BuildKeys` and the manifest label, and a single non-translatable string resource carries the licence notice into a `SystemCard` on the home grid. The icon is generated deterministically from committed source art by a checked-in PowerShell script (GDI+), producing the adaptive foreground/monochrome plus the five legacy density buckets.

**Tech Stack:** Gradle 9.5.0 + AGP 9.3.0, Kotlin/JUnit4 local unit tests, PowerShell 5.1 + `System.Drawing` (no ImageMagick/Python/cwebp available on this machine), Android SDK 37.2 + NDK 25.2.9519653.

**Spec:** `docs/superpowers/specs/2026-09-29-super-launcher-dedicated-design.md` (§2, §8, §8.1, §12 fase 1)

---

## Global Constraints

- `launcher_name=SuperLauncher` — **não** pode conter `ZalithLauncher` nem `ZL`.
- `launcher_app_name=Super Launcher` (nome exibido; vira `manifestPlaceholders["launcher_name"]` → `android:label`).
- `launcher_short_name=SL`.
- `url_home=https://github.com/Hakkaiz1/Super-Launcher` — GPLv3 §6 exige que a oferta de fonte aponte pra versão modificada.
- `launcher_version_code` deve superar `200043` (senão o Android recusa a atualização por cima da instalação oficial).
- Aviso obrigatório, texto exato: `Unofficial Modified Version by Hakkaiz` — em inglês, `translatable="false"`.
- `DEDICATED_MODE=true`, `DEDICATED_PACK_SLUG=dbc-super-oficial`, `DEDICATED_SERVER_NAME=Minecraft Server`, `DEDICATED_SERVER_IP=dbcsuper.com`.
- Constantes de referência das fases seguintes: Minecraft `1.7.10`, Forge `10.13.4.1558`, `TECHNIC_API_BUILD=1166`.
- Nenhum aviso de copyright do projeto pode ser removido.
- O aviso de versão modificada é **incondicional** — não depende de `DEDICATED_MODE`, porque a obrigação de licença vale pra qualquer build.
- Ícone: arte-fonte versionada em `docs/assets/dbc-super-icon-1024.png`; arte ocupa **70%** de um canvas de **640×640** (zona segura do ícone adaptável).

## Review Focus

Cinco condições que a spec implica mas que nenhum teste cobre automaticamente — cada linha é fixada pelo teste indicado, na task dona do código:

1. **Weblate traduz a string do aviso** e ele deixa de aparecer em inglês (o projeto tem dezenas de locales com `strings.xml` espelhados) → teste `noticeCopyIsEnglishAndNotTranslatable`, task 3, lê `values/strings.xml` e exige `translatable="false"` com a cópia exata.
2. **`url_home` continua apontando pro repositório upstream**, violando a GPLv3 §6 (fonte da versão modificada) → teste `homeUrlPointsAtModifiedSource`, task 2.
3. **Arte cortada pela máscara do ícone adaptável** (as orelhas do personagem ficam na borda do círculo visível) → teste `foregroundKeepsSafeZone`, task 4: cantos e margem do canvas transparentes, centro opaco.
4. **A arte-fonte só existe na pasta Downloads da máquina** — some e o script de geração quebra sem aviso → teste `sourceArtIsCommitted`, task 4: o arquivo está dentro do repo com 1024×1024.
5. **Ícone de notificação vira mancha colorida** (Android exige alpha-only nos small icons) → teste `monochromeIsAlphaOnlyWhite` + `notificationsUseAlphaOnlyIcon`, task 4: silhueta só-branco-com-alpha e os 4 serviços apontando pra ela.

---

## File Structure

| Arquivo | Ação | Responsabilidade |
|---|---|---|
| `docs/assets/dbc-super-icon-1024.png` | criar | Arte-fonte versionada (sai da dependência do Downloads) |
| `scripts/generate-launcher-icon.ps1` | criar | Gera todos os recursos de ícone a partir da arte; reexecutável |
| `ZalithLauncher/gradle.properties` | modificar | Nome, versão, URL da fonte modificada |
| `ZalithLauncher/build.gradle.kts` | modificar | Novos `BuildKeys` dedicados |
| `ZalithLauncher/src/main/res/values/strings.xml` | modificar | String do aviso (`translatable="false"`) |
| `ZalithLauncher/src/main/res/values/ic_launcher_background.xml` | regenerar | Cor de fundo amostrada da arte |
| `ZalithLauncher/src/main/res/drawable-nodpi/` | criar | `ic_launcher_foreground.png`, `ic_launcher_monochrome.png` (640×640) |
| `ZalithLauncher/src/main/res/drawable/ic_launcher_{foreground,monochrome}.xml` | apagar | Vetores substituídos pelo bitmap |
| `ZalithLauncher/src/main/res/mipmap-*/` | regenerar | 5 densidades × 2 nomes em PNG; `.webp` antigos apagados |
| `ZalithLauncher/src/main/java/.../home/HomeCards.kt` | modificar | Card de sistema com o aviso |
| 4 arquivos de serviço (`.setSmallIcon`) | modificar | Small icon alpha-only |
| `local.properties` | criar (gitignored) | `sdk.dir` |
| Testes: `BuildBrandTest`, `HomeCardsSystemTest`, `LauncherIconResourcesTest` | criar | Fixam as 5 condições do Review Focus |

---

### Task 1: Ambiente de build verificado

**Files:**
- Create: `local.properties` (gitignored — nunca commitar)
- Test: nenhuma — o entregável é a suíte de baseline passando

**Interfaces:**
- Consumes: Android SDK já instalado em `%LOCALAPPDATA%\Android\Sdk` (cmdline-tools 15859902, `platforms;android-37.2`, `platform-tools`, `ndk;25.2.9519653`), licenças aceitas.
- Produces: `local.properties` com `sdk.dir`; confirmação de que `:ZalithLauncher:testDebugUnitTest` compila e passa **antes** de qualquer mudança — é a linha de base que prova que as tasks seguintes não quebraram nada pré-existente.

> **STATUS: concluída** durante a preparação (commit `488340f2`). A baseline de origem estava **vermelha com 8 falhas** que nada tinham a ver com a Fase 1; foram reparadas antes do início da execução:
>
> | Falha | Causa | Conserto |
> |---|---|---|
> | 6 × `VersionCompareTest` | `GameVersionNumber` lê `/assets/game/versions.txt` via `getResourceAsStream` — assets do Android não entram no classpath do teste → `ExceptionInInitializerError` | copia de `src/main/assets/game/` para `src/test/resources/assets/game/` (9,4 KB + 1,1 KB; AGP põe `src/test/resources` no classpath). 4 passaram na hora; as 2 restantes eram asserções que discordavam da semântica real da HMCL — corrigidas com comentário inline, conforme decisão do usuário |
> | 1 × `FileTest` + 1 × `MurmurHash2IncrementalTest` | liam `F:\Download\geckolib-...jar` (arquivo da máquina do autor) e **não tinham nenhuma asserção** | arquivo temporário + `assertEquals` de verdade |
>
> Spike descartável confirmou que o `AndroidSourceDirectorySet` do AGP 9.3 não tem filtro de padrão (`filter`/`include` inexistentes, `srcDir` deprecado como erro em Gradle 9) — por isso a rota `src/test/resources` e não uma `srcDir` apontando pra `src/main`.

- [x] **Step 1: Confirmar que o SDK existe (idempotente)**

Run:

```powershell
$sdk = "$env:LOCALAPPDATA\Android\Sdk"
Test-Path "$sdk\cmdline-tools\latest\bin\sdkmanager.bat"   # deve ser True
Test-Path "$sdk\platforms\android-37.2"                     # deve ser True
Test-Path "$sdk\ndk\25.2.9519653"                           # deve ser True
```

Expected: `True` / `True` / `True`.

Se qualquer um for `False`, reinstale (o `--licenses` precisa rodar antes dos pacotes):

```powershell
$ProgressPreference='SilentlyContinue'
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
New-Item -ItemType Directory -Force -Path "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools" | Out-Null
Invoke-WebRequest "https://dl.google.com/android/repository/commandlinetools-win-15859902_latest.zip" -OutFile "$env:TEMP\clt.zip" -UseBasicParsing
Expand-Archive "$env:TEMP\clt.zip" "$env:TEMP\clt" -Force
Move-Item "$env:TEMP\clt\cmdline-tools" "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest"
$sm = "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat"
(("y`n" * 30) | & $sm --licenses) | Select-Object -Last 1
& $sm "platforms;android-37.2" "platform-tools" "ndk;25.2.9519653"
```

Expected: `All SDK package licenses accepted` e `EXIT=0`.

- [x] **Step 2: Criar `local.properties`**

Run (na raiz do repositório):

```powershell
"sdk.dir=C:/Users/Administrator/AppData/Local/Android/Sdk" |
    Set-Content -Path .\local.properties -Encoding ascii
Get-Content .\local.properties
```

Expected: `sdk.dir=C:/Users/Administrator/AppData/Local/Android/Sdk`. O `.gitignore` já cobre `local.properties` (linhas 5 e 11) — confirme com `git status --porcelain` que ele **não** aparece.

- [x] **Step 3: Rodar a baseline**

Run: `.\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, `EXIT=0`.

> **Conferido:** `BUILD SUCCESSFUL in 2m 50s` / `EXIT=0` / 84 testes, 0 falhas (após o reparo acima).

> Primeira execução baixa dependências e o NDK — pode levar vários minutos. Um `FAILURE` aqui é problema de ambiente, não de código: resolva antes de seguir.

- [x] **Step 4: Commit**

Nada para commitar nesta task (`local.properties` é gitignored). O reparo da baseline foi commitado separadamente em `488340f2` como parte da preparação.

---

### Task 2: Identidade da build

**Files:**
- Modify: `ZalithLauncher/gradle.properties:1-5` (nome/url) e `:17-19` (versão)
- Modify: `ZalithLauncher/build.gradle.kts:212-220` (bloco `buildKeys { }`)
- Test: `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/BuildBrandTest.kt`

**Interfaces:**
- Consumes: `local.properties` da task 1.
- Produces:
  - `BuildKeys.DEDICATED_MODE: Boolean` (`const val`, `true`)
  - `BuildKeys.DEDICATED_PACK_SLUG: String` (`const val`, `"dbc-super-oficial"`)
  - `BuildKeys.DEDICATED_SERVER_NAME: String` (`const val`, `"Minecraft Server"`)
  - `BuildKeys.DEDICATED_SERVER_IP: String` (`const val`, `"dbcsuper.com"`)
  - `launcher_name=SuperLauncher` → alimenta `BuildKeys.LAUNCHER_IDENTIFIER` (namespace do MMKV) e o nome do APK `$launcherName-$launcherVersionName.apk`
  - `launcher_app_name=Super Launcher` → `manifestPlaceholders["launcher_name"]` → `android:label`

> Os quatro `DEDICATED_*` usam `hidden=false` (constante em texto puro, não ofuscada) de propósito: são configuração, não segredo, e precisam ser assertáveis em teste unitário. Strings com `hidden=true` passam por decodificação em runtime que, com `isReturnDefaultValues=true`, devolveria `null` silencioso no JVM de teste.

- [x] **Step 1: Escrever o teste falhando**

Crie `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/BuildBrandTest.kt`:

```kotlin
package com.movtery.zalithlauncher

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Fixa a identidade da build dedicada.
 *
 * Os valores moram em gradle.properties; ler o arquivo direto mantém o teste
 * independente da ofuscação de string do BuildKeys (que, com
 * isReturnDefaultValues=true, devolve null silencioso no JVM de teste).
 */
class BuildBrandTest {

    private fun findPropertiesFile(): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, "gradle.properties")
            if (candidate.isFile && candidate.readText().contains("launcher_name=")) {
                return candidate
            }
            dir = dir.parentFile
        }
        error("gradle.properties com launcher_name não encontrado a partir de ${File("").absolutePath}")
    }

    private fun prop(name: String): String =
        findPropertiesFile().readLines()
            .firstOrNull { it.startsWith("$name=") }
            ?.substringAfter("=")
            ?.trim()
            ?: error("propriedade $name não encontrada")

    @Test
    fun launcherNameIsSuperLauncher() {
        val name = prop("launcher_name")
        assertEquals("SuperLauncher", name)
        assertFalse("launcher_name não pode conter ZalithLauncher", name.contains("ZalithLauncher"))
        assertFalse("launcher_name não pode conter ZL", name.contains("ZL"))
    }

    @Test
    fun appNamesAreSuperLauncher() {
        assertEquals("Super Launcher", prop("launcher_app_name"))
        assertEquals("SL", prop("launcher_short_name"))
    }

    @Test
    fun homeUrlPointsAtModifiedSource() {
        // GPLv3 §6: a oferta de fonte deve ser da versão modificada
        assertEquals("https://github.com/Hakkaiz1/Super-Launcher", prop("url_home"))
    }

    @Test
    fun versionCodeExceedsUpstream() {
        // 200043 é o versionCode do upstream; abaixo disso o Android recusa a instalação
        assertTrue(
            "versionCode ${prop("launcher_version_code")} deve superar 200043",
            prop("launcher_version_code").toInt() > 200043
        )
    }

    @Test
    fun dedicatedBuildKeysAreDeclared() {
        assertTrue(BuildKeys.DEDICATED_MODE)
        assertEquals("dbc-super-oficial", BuildKeys.DEDICATED_PACK_SLUG)
        assertEquals("Minecraft Server", BuildKeys.DEDICATED_SERVER_NAME)
        assertEquals("dbcsuper.com", BuildKeys.DEDICATED_SERVER_IP)
    }
}
```

- [x] **Step 2: Rodar e ver falhar**

Run: `.\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*BuildBrandTest" --console=plain`
Expected: `FAILED` — `launcher_name` ainda é `ZalithLauncher`, `url_home` ainda é o upstream, `BuildKeys.DEDICATED_MODE` não compila (símbolo inexistente). Qualquer um desses três já prova que o teste é sensível.

- [x] **Step 3: Trocar o `gradle.properties`**

Em `ZalithLauncher/gradle.properties`, troque o bloco inicial:

```properties
# launcher name & url
launcher_name=SuperLauncher
launcher_app_name=Super Launcher
launcher_short_name=SL
url_home=https://github.com/Hakkaiz1/Super-Launcher
```

e o bloco de versão:

```properties
# launcher version
launcher_version_code=200100
launcher_version_name=1.0.0
```

> `200100 > 200043` é obrigatório (mesmo `applicationId` da instalação oficial); `1.0.0` porque este é um produto novo e o nome do APK passa a ser `SuperLauncher-1.0.0.apk`.

- [x] **Step 4: Declarar os BuildKeys dedicados**

Em `ZalithLauncher/build.gradle.kts`, no bloco `buildKeys { }` (linha 212), **depois** de `string("BUILD_ARCH", projectArch)`, adicione:

```kotlin
    // Build dedicada Super Launcher (spec §2)
    boolean("DEDICATED_MODE", true)
    string("DEDICATED_PACK_SLUG", "dbc-super-oficial")
    string("DEDICATED_SERVER_NAME", "Minecraft Server")
    string("DEDICATED_SERVER_IP", "dbcsuper.com")
```

> O aviso de versão modificada **não** vira BuildKey — ele existe só como string resource (`task 3`), fonte única de verdade. Replicar em dois lugares é como o texto acaba divergindo.

- [x] **Step 5: Rodar e ver passar**

Run: `.\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*BuildBrandTest" --console=plain`
Expected: `BUILD SUCCESSFUL` — os 5 testes passam.

- [x] **Step 6: Commit**

```powershell
git add ZalithLauncher/gradle.properties ZalithLauncher/build.gradle.kts `
        ZalithLauncher/src/test/java/com/movtery/zalithlauncher/BuildBrandTest.kt
git commit -m "feat(identity): rename build to Super Launcher and add dedicated build keys"
```

---

### Task 3: Aviso obrigatório na tela inicial

**Files:**
- Modify: `ZalithLauncher/src/main/res/values/strings.xml:87` (inserir logo após `launcher_version_debug_warning_cant_close`)
- Modify: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/home/HomeCards.kt:72-76` (`systemCards()`) e novo método privado
- Test: `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/ui/screens/content/home/HomeCardsSystemTest.kt`

**Interfaces:**
- Consumes: nada desta fase; a task 4 não depende desta.
- Produces:
  - `R.string.unofficial_modified_notice` — `String`, `translatable="false"`
  - `HomeCards.systemCards(): List<SystemCard>` contendo `SystemCard(id = "system_unofficial_notice", ...)`
  - id estável `"system_unofficial_notice"` — é o que a Fase 5 vai ter que continuar exibindo quando a `DedicatedScreen` substituir a home.

> `HomeCards` é um `object` público e `systemCards()` só **constrói** `SystemCard`s; o lambda `@Composable` só roda na composição. Por isso o teste JVM consegue chamar `systemCards()` sem Android.

- [x] **Step 1: Escrever o teste falhando**

Crie `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/ui/screens/content/home/HomeCardsSystemTest.kt`:

```kotlin
package com.movtery.zalithlauncher.ui.screens.content.home

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeCardsSystemTest {

    private fun repoFile(relative: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("$relative não encontrado a partir de ${File("").absolutePath}")
    }

    @Test
    fun unofficialNoticeCardIsRegistered() {
        val ids = HomeCards.systemCards().map { it.id }
        assertTrue(
            "card do aviso ausente em $ids",
            "system_unofficial_notice" in ids
        )
    }

    @Test
    fun noticeCopyIsEnglishAndNotTranslatable() {
        val xml = repoFile("ZalithLauncher/src/main/res/values/strings.xml").readText()
        assertTrue(
            "o aviso precisa ser inglês e non-translatable, senão o Weblate traduz",
            xml.contains(
                """<string name="unofficial_modified_notice" translatable="false">""" +
                    """Unofficial Modified Version by Hakkaiz</string>"""
            )
        )
    }
}
```

- [x] **Step 2: Rodar e ver falhar**

Run: `.\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*HomeCardsSystemTest" --console=plain`
Expected: `FAILED` — card não registrado e a string não existe no XML.

- [x] **Step 3: Adicionar a string**

Em `ZalithLauncher/src/main/res/values/strings.xml`, logo **após** a linha 87 (`launcher_version_debug_warning_cant_close`) e **antes** de `<!-- Themes -->`:

```xml
    <!-- Dedicated Super Launcher build: mandatory modified-version notice (GPLv3 §7) -->
    <string name="unofficial_modified_notice" translatable="false">Unofficial Modified Version by Hakkaiz</string>
```

- [x] **Step 4: Registrar o card de sistema**

Em `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/home/HomeCards.kt`, troque `systemCards()` (linhas 72-76) por:

```kotlin
    /** 系统卡片（不可变更），由启动器自行提供并绘制在网格之外 */
    fun systemCards(): List<SystemCard> = buildList {
        add(unofficialNoticeCard())
        if (BuildConfig.DEBUG) {
            add(debugWarningCard())
        }
    }
```

e adicione o método logo abaixo de `debugWarningCard()` (antes do fechamento do `object`, linha 108):

```kotlin
    /**
     * GPLv3 §7 + repositório: aviso obrigatório de versão modificada.
     * Incondicional — o dever de licença vale pra qualquer build.
     */
    private fun unofficialNoticeCard() = SystemCard(id = "system_unofficial_notice") {
        BackgroundCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = stringResource(R.string.unofficial_modified_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.alpha(0.9f)
                )
            }
        }
    }
```

Nenhum import novo é necessário: `Arrangement`, `Column`, `fillMaxWidth`, `padding`, `MaterialTheme`, `Text`, `stringResource`, `alpha`, `BackgroundCard`, `R` já estão importados no topo do arquivo.

- [x] **Step 5: Rodar e ver passar**

Run: `.\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*HomeCardsSystemTest" --console=plain`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 6: Rodar a suíte inteira**

Run: `.\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL` — nenhum teste existente quebrou com o card novo.

- [x] **Step 7: Commit**

```powershell
git add ZalithLauncher/src/main/res/values/strings.xml `
        ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/content/home/HomeCards.kt `
        ZalithLauncher/src/test/java/com/movtery/zalithlauncher/ui/screens/content/home/HomeCardsSystemTest.kt
git commit -m "feat(identity): show mandatory modified-version notice on home screen"
```

---

### Task 4: Ícone do APK

**Files:**
- Create: `docs/assets/dbc-super-icon-1024.png`
- Create: `scripts/generate-launcher-icon.ps1`
- Create: `ZalithLauncher/src/main/res/drawable-nodpi/ic_launcher_foreground.png`, `.../ic_launcher_monochrome.png`
- Create: `ZalithLauncher/src/main/res/mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.png` e `ic_launcher_round.png`
- Modify: `ZalithLauncher/src/main/res/values/ic_launcher_background.xml`
- Delete: `ZalithLauncher/src/main/res/drawable/ic_launcher_foreground.xml`, `.../ic_launcher_monochrome.xml`, os 10 `mipmap-*/ic_launcher*.webp`
- Modify (small icon): `keepalive/TaskKeepAliveService.kt:61`, `game/launch/GameService.kt:56`, `terracotta/TerracottaVPNService.java:211`, `game/download/jvm_server/JvmService.kt:140`
- Test: `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/res/LauncherIconResourcesTest.kt`

**Interfaces:**
- Consumes: arte fornecida pelo time em `C:\Users\Administrator\Downloads\21c2fe7752bc783c0327827520086fd2.png`.
- Produces:
  - `docs/assets/dbc-super-icon-1024.png` — arte versionada (fonte única do script)
  - `scripts/generate-launcher-icon.ps1` — reexecutável quando a arte mudar
  - `@drawable/ic_launcher_foreground` / `@drawable/ic_launcher_monochrome` → PNG 640×640 em `drawable-nodpi/` (mesmos nomes, então `mipmap-anydpi-v26/ic_launcher*.xml` e `splash_launcher.xml` **não mudam**)
  - `R.drawable.ic_launcher_monochrome` como small icon das 4 notificações
  - `@color/ic_launcher_background` → cor amostrada das quinas da arte

> `drawable/` e `drawable-nodpi/` são configurações do **mesmo** recurso — por isso os vetores em `drawable/` são apagados em vez de coexistir: senão a resolução fica ambígua.

- [x] **Step 1: Escrever o teste falhando**

Crie `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/res/LauncherIconResourcesTest.kt`:

```kotlin
package com.movtery.zalithlauncher.res

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO

/**
 * Fixa a geometria do ícone: zona segura do adaptável, silhueta alpha-only
 * das notificações, densidades legadas e ausência dos assets antigos.
 */
class LauncherIconResourcesTest {

    private fun repoFile(relative: String): File {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate
            dir = dir.parentFile
        }
        error("$relative não encontrado a partir de ${File("").absolutePath}")
    }

    private fun exists(relative: String): Boolean {
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            if (File(dir, relative).exists()) return true
            dir = dir.parentFile
        }
        return false
    }

    private fun read(relative: String): BufferedImage =
        ImageIO.read(repoFile(relative)) ?: error("não decodifiquei $relative")

    @Test
    fun sourceArtIsCommitted() {
        assertTrue("arte-fonte não está no repositório", exists("docs/assets/dbc-super-icon-1024.png"))
        val art = read("docs/assets/dbc-super-icon-1024.png")
        assertEquals(1024, art.width)
        assertEquals(1024, art.height)
    }

    @Test
    fun foregroundKeepsSafeZone() {
        val fg = read("ZalithLauncher/src/main/res/drawable-nodpi/ic_launcher_foreground.png")
        assertEquals(640, fg.width)
        assertEquals(640, fg.height)
        // arte ocupa 70% (448px) centrada → faixa livre de 96px em cada lado
        assertEquals(0, fg.getRGB(4, 4) ushr 24)
        assertEquals(0, fg.getRGB(635, 635) ushr 24)
        assertEquals(0, fg.getRGB(50, 320) ushr 24)
        assertTrue("centro do ícone ficou vazio", fg.getRGB(320, 320) ushr 24 > 0)
    }

    @Test
    fun monochromeIsAlphaOnlyWhite() {
        val mc = read("ZalithLauncher/src/main/res/drawable-nodpi/ic_launcher_monochrome.png")
        assertEquals(640, mc.width)
        assertEquals(640, mc.height)
        var opaque = 0
        for (y in 0 until mc.height step 4) {
            for (x in 0 until mc.width step 4) {
                val p = mc.getRGB(x, y)
                if (p ushr 24 > 0) {
                    opaque++
                    assertEquals(
                        "small icon precisa ser alpha-only",
                        0x00FFFFFF,
                        p and 0x00FFFFFF
                    )
                }
            }
        }
        assertTrue("silhueta monochrome vazia", opaque > 100)
    }

    @Test
    fun legacyDensityIconsAreGenerated() {
        val densities = mapOf("mdpi" to 48, "hdpi" to 72, "xhdpi" to 96, "xxhdpi" to 144, "xxxhdpi" to 192)
        for ((density, size) in densities) {
            for (name in listOf("ic_launcher.png", "ic_launcher_round.png")) {
                val img = read("ZalithLauncher/src/main/res/mipmap-$density/$name")
                assertEquals("mipmap-$density/$name", size, img.width)
                assertEquals(size, img.height)
            }
        }
    }

    @Test
    fun oldIconAssetsAreGone() {
        for (density in listOf("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi")) {
            for (name in listOf("ic_launcher.webp", "ic_launcher_round.webp")) {
                assertFalse(
                    "asset antigo ainda presente: $density/$name",
                    exists("ZalithLauncher/src/main/res/mipmap-$density/$name")
                )
            }
        }
        assertFalse(exists("ZalithLauncher/src/main/res/drawable/ic_launcher_foreground.xml"))
        assertFalse(exists("ZalithLauncher/src/main/res/drawable/ic_launcher_monochrome.xml"))
    }

    @Test
    fun backgroundColorIsValidHex() {
        val xml = repoFile("ZalithLauncher/src/main/res/values/ic_launcher_background.xml").readText()
        Regex("""#([0-9A-Fa-f]{6})""").find(xml)
            ?: error("nenhuma cor #RRGGBB em ic_launcher_background.xml")
    }

    @Test
    fun notificationsUseAlphaOnlyIcon() {
        val services = listOf(
            "ZalithLauncher/src/main/java/com/movtery/zalithlauncher/keepalive/TaskKeepAliveService.kt",
            "ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/launch/GameService.kt",
            "ZalithLauncher/src/main/java/com/movtery/zalithlauncher/terracotta/TerracottaVPNService.java",
            "ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/jvm_server/JvmService.kt"
        )
        for (file in services) {
            val text = repoFile(file).readText()
            assertTrue(
                "$file precisa usar o small icon alpha-only",
                text.contains("setSmallIcon(R.drawable.ic_launcher_monochrome)")
            )
        }
    }
}
```

- [x] **Step 2: Rodar e ver falhar**

Run: `.\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*LauncherIconResourcesTest" --console=plain`
Expected: `FAILED` — quase todos os testes falham (arquivos não existem ainda).

- [x] **Step 3: Versionar a arte-fonte**

```powershell
New-Item -ItemType Directory -Force -Path docs\assets | Out-Null
Copy-Item "C:\Users\Administrator\Downloads\21c2fe7752bc783c0327827520086fd2.png" `
          "docs\assets\dbc-super-icon-1024.png" -Force
```

- [x] **Step 4: Criar o script de geração**

Crie `scripts/generate-launcher-icon.ps1`:

```powershell
<#
  Gera todos os recursos de ícone do Super Launcher a partir da arte versionada.

  Uso:
    powershell -ExecutionPolicy Bypass -File scripts/generate-launcher-icon.ps1

  Saída (em ZalithLauncher/src/main/res):
    drawable-nodpi/ic_launcher_foreground.png   640x640, arte a 70% centrada, alpha
    drawable-nodpi/ic_launcher_monochrome.png   640x640, silhueta branca alpha-only
    mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher{,_round}.png
    values/ic_launcher_background.xml           cor amostrada das quinas da arte
  O script também apaga os .webp legados.
#>
param(
    [string]$RepoRoot = (Split-Path -Parent $PSScriptRoot)
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$artPath  = Join-Path $RepoRoot 'docs\assets\dbc-super-icon-1024.png'
$resDir   = Join-Path $RepoRoot 'ZalithLauncher\src\main\res'
$canvas   = 640
$artRatio = 0.70

if (-not (Test-Path $artPath)) { throw "arte-fonte não encontrada: $artPath" }

$art = [System.Drawing.Bitmap]::new($artPath)
try {
    # --- 1. cor de fundo: média das quinas (16x16 cada) -------------------
    $sr = 0L; $sg = 0L; $sb = 0L; $sn = 0
    foreach ($pt in @(@(0, 0), @(1007, 0), @(0, 1007), @(1007, 1007))) {
        for ($dx = 0; $dx -lt 16; $dx++) {
            for ($dy = 0; $dy -lt 16; $dy++) {
                $c = $art.GetPixel($pt[0] + $dx, $pt[1] + $dy)
                $sr += $c.R; $sg += $c.G; $sb += $c.B; $sn++
            }
        }
    }
    $bg = [System.Drawing.Color]::FromArgb(255, [int]($sr / $sn), [int]($sg / $sn), [int]($sb / $sn))
    $bgHex = '#{0:X2}{1:X2}{2:X2}' -f $bg.R, $bg.G, $bg.B

    $artPx = [int]($canvas * $artRatio)   # 448
    $offset = [int](($canvas - $artPx) / 2)  # 96
    $dst = [System.Drawing.Rectangle]::new($offset, $offset, $artPx, $artPx)

    $nodpi = Join-Path $resDir 'drawable-nodpi'
    New-Item -ItemType Directory -Force -Path $nodpi | Out-Null

    # --- 2. foreground do ícone adaptável ---------------------------------
    $fg = [System.Drawing.Bitmap]::new($canvas, $canvas, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $gp = [System.Drawing.Graphics]::FromImage($fg)
    $gp.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $gp.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $gp.DrawImage($art, $dst)
    $gp.Dispose()
    $fg.Save((Join-Path $nodpi 'ic_launcher_foreground.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $fg.Dispose()

    # --- 3. monochrome: silhueta branca alpha-only ------------------------
    $mc = [System.Drawing.Bitmap]::new($canvas, $canvas, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
    $gp = [System.Drawing.Graphics]::FromImage($mc)
    $gp.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $gp.DrawImage($art, $dst)
    $gp.Dispose()
    for ($y = 0; $y -lt $canvas; $y++) {
        for ($x = 0; $x -lt $canvas; $x++) {
            $p = $mc.GetPixel($x, $y)
            $lum = [int](0.299 * $p.R + 0.587 * $p.G + 0.114 * $p.B)
            if ($p.A -gt 0 -and $lum -ge 110) {
                $mc.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(255, 255, 255, 255))
            } else {
                $mc.SetPixel($x, $y, [System.Drawing.Color]::FromArgb(0, 0, 0, 0))
            }
        }
    }
    $mc.Save((Join-Path $nodpi 'ic_launcher_monochrome.png'), [System.Drawing.Imaging.ImageFormat]::Png)
    $mc.Dispose()

    # --- 4. densidades legadas --------------------------------------------
    $densities = [ordered]@{ mdpi = 48; hdpi = 72; xhdpi = 96; xxhdpi = 144; xxxhdpi = 192 }
    foreach ($density in $densities.Keys) {
        $size = [int]$densities[$density]
        foreach ($round in @($false, $true)) {
            $bmp = [System.Drawing.Bitmap]::new($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
            $gp = [System.Drawing.Graphics]::FromImage($bmp)
            $gp.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
            $gp.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic

            $path = [System.Drawing.Drawing2D.GraphicsPath]::new()
            if ($round) {
                $path.AddEllipse(0, 0, $size, $size)
            } else {
                $r = [int]($size * 0.2)
                $d = 2 * $r
                $path.AddArc(0, 0, $d, $d, 180, 90)
                $path.AddArc($size - $d, 0, $d, $d, 270, 90)
                $path.AddArc($size - $d, $size - $d, $d, $d, 0, 90)
                $path.AddArc(0, $size - $d, $d, $d, 90, 90)
                $path.CloseFigure()
            }
            $gp.SetClip($path)
            $gp.Clear($bg)

            $a = [int]($size * $artRatio)
            $o = [int](($size - $a) / 2)
            $gp.DrawImage($art, [System.Drawing.Rectangle]::new($o, $o, $a, $a))
            $gp.Dispose()
            $path.Dispose()

            $fileName = if ($round) { 'ic_launcher_round.png' } else { 'ic_launcher.png' }
            $bmp.Save((Join-Path (Join-Path $resDir "mipmap-$density") $fileName),
                      [System.Drawing.Imaging.ImageFormat]::Png)
            $bmp.Dispose()
        }

        # apaga os .webp antigos (mesmo nome de recurso → duplicidade no aapt2)
        foreach ($old in @('ic_launcher.webp', 'ic_launcher_round.webp')) {
            $oldPath = Join-Path (Join-Path $resDir "mipmap-$density") $old
            if (Test-Path $oldPath) { Remove-Item $oldPath -Force }
        }
    }

    # --- 5. cor de fundo do adaptável -------------------------------------
    $bgXml = @"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="ic_launcher_background">$bgHex</color>
</resources>
"@
    [System.IO.File]::WriteAllText((Join-Path $resDir 'values\ic_launcher_background.xml'), $bgXml)

    Write-Host "OK  cor de fundo: $bgHex"
    Write-Host "OK  foreground e monochrome: ${canvas}x${canvas} (arte ${artPx}px centrada em ${offset}px)"
    Write-Host "OK  5 densidades legadas geradas, .webp removidos"
} finally {
    $art.Dispose()
}
```

- [x] **Step 5: Apagar os vetores antigos**

```powershell
Remove-Item ZalithLauncher\src\main\res\drawable\ic_launcher_foreground.xml -Force
Remove-Item ZalithLauncher\src\main\res\drawable\ic_launcher_monochrome.xml -Force
```

> Apagar **antes** de rodar o script evita o estado ambíguo em que `drawable/` e `drawable-nodpi/` disputam o mesmo nome de recurso.

- [x] **Step 6: Rodar o script**

Run: `powershell -ExecutionPolicy Bypass -File scripts\generate-launcher-icon.ps1`
Expected: as três linhas `OK ...`, com `cor de fundo: #` seguido de 6 dígitos hex.

- [x] **Step 7: Trocar os small icons das notificações**

Nos 4 arquivos abaixo, troque `setSmallIcon(R.mipmap.ic_launcher)` por `setSmallIcon(R.drawable.ic_launcher_monochrome)`:

| Arquivo | Linha |
|---|---|
| `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/keepalive/TaskKeepAliveService.kt` | 61 |
| `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/launch/GameService.kt` | 56 |
| `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/terracotta/TerracottaVPNService.java` | 211 |
| `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/game/download/jvm_server/JvmService.kt` | 140 |

Verificação de que sobrou alguma referência antiga:

Run: `Select-String -Path "ZalithLauncher\src\main\java\**\*.kt","ZalithLauncher\src\main\java\**\*.java" -Pattern "R\.mipmap\.ic_launcher"`
Expected: nenhuma ocorrência.

- [x] **Step 8: Rodar e ver passar**

Run: `.\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*LauncherIconResourcesTest" --console=plain`
Expected: `BUILD SUCCESSFUL` (7 testes).

- [x] **Step 9: Commit**

```powershell
git add docs/assets scripts ZalithLauncher/src/main/res ZalithLauncher/src/main/java
git commit -m "feat(identity): replace launcher icon with DBC Super artwork and alpha-only notification icon"
```

---

### Task 5: Build e verificação

**Files:**
- Nenhum arquivo novo — esta task só produz o APK e a evidência.

**Interfaces:**
- Consumes: tasks 1-4.
- Produces: `ZalithLauncher/build/outputs/apk/debug/SuperLauncher-Debug-1.0.0-<abi>.apk` e a lista de verificação manual abaixo.

- [x] **Step 1: Suíte completa**

Run: `.\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain`
Expected: `BUILD SUCCESSFUL`, `EXIT=0`.

- [x] **Step 2: Compilar o APK**

Run: `.\gradlew.bat :ZalithLauncher:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL` e, em `ZalithLauncher\build\outputs\apk\debug\`, arquivos `SuperLauncher-Debug-1.0.0-*.apk` (o build tem split por ABI → um por arquitetura).

- [x] **Step 3: Verificar o manifesto do APK**

Run:

```powershell
# o AGP baixa sozinho o build-tools que precisa (36.0.0 neste projeto);
# se ainda não existir, instale explicitamente
$aapt = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" -Recurse -Filter aapt2.exe -ErrorAction SilentlyContinue |
        Select-Object -First 1
if (-not $aapt) {
    $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot"
    & "$env:LOCALAPPDATA\Android\Sdk\cmdline-tools\latest\bin\sdkmanager.bat" "build-tools;36.0.0"
    $aapt = Get-ChildItem "$env:LOCALAPPDATA\Android\Sdk\build-tools" -Recurse -Filter aapt2.exe |
            Select-Object -First 1
}
$apk = Get-ChildItem ZalithLauncher\build\outputs\apk\debug\SuperLauncher-Debug-1.0.0-arm64-v8a.apk |
       Select-Object -First 1
& $aapt.FullName dump badging $apk.FullName | Select-String "application-label|package:|application-icon"
```

Expected: `package: name='com.movtery.zalithlauncher.v2'`, `application-label: Super Launcher`, ícone declarado.

- [x] **Step 4: Verificação manual (aparelho)**

Com o aparelho conectado (`adb devices` mostra um dispositivo autorizado):

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
& $adb install -r (Get-ChildItem ZalithLauncher\build\outputs\apk\debug\SuperLauncher-Debug-1.0.0-arm64-v8a.apk).FullName
& $adb shell pm list packages | Select-String "movtery"
```

Checklist visual — marque cada item:

- [ ] Nome do app no drawer/launcher do celular é **Super Launcher**
- [ ] Ícone mostra a arte DBC SUPER, com **orelhas e texto "SUPER" inteiros** (não cortados pela máscara circular/squircle)
- [ ] Fundo do ícone é o tom escuro da arte, sem sobra do laranja `#FFE08F` antigo
- [ ] Tela inicial exibe o card **"Unofficial Modified Version by Hakkaiz"**
- [ ] Em *Configurações → Sobre*, o nome exibido é Super Launcher e o link aponta pra `github.com/Hakkaiz1/Super-Launcher`
- [ ] Dispare uma notificação (ex.: iniciar uma tarefa) e confira que o ícone é a **silhueta branca**, não uma mancha colorida
- [ ] Tela de abertura (splash) mostra a nova arte

Se o ícone estiver cortado, ajuste `$artRatio` em `scripts/generate-launcher-icon.ps1` (0.70 → 0.65) e rode de novo os steps 6 e 2 desta task — o teste `foregroundKeepsSafeZone` precisa ser atualizado junto, porque ele fixa a proporção.

- [x] **Step 5: Commit final (se algo tiver mudado)**

```powershell
git add -A
git status --porcelain   # deve estar limpo além do commit já feito
```

---

## Ajustes em relação à spec

Coisas que a spec diz de um jeito e o plano faz de outro, de propósito — revise especialmente estas:

1. **Aviso só em inglês.** A §8 mandava adicionar `values/pt-rBR` também; sua decisão posterior ("deixa em inglês mesmo") venceu. O plano usa `translatable="false"`, então o aviso fica em inglês em **todos** os idiomas e o Weblate não gera tradução. Consequência: em pt-BR o texto não muda — era o esperado?
2. **`DEDICATED_NOTICE` não virou BuildKey.** A §2 lista a chave, mas o texto existe uma única vez em `res/values/strings.xml`. Dois depósitos para a mesma cópia é como o texto diverge depois.
3. **Servidor nomeado.** A §11 deixava `DEDICATED_SERVER_NAME`/`IP` como "a definir"; o plano fixa `Minecraft Server` / `dbcsuper.com`, que são exatamente os valores que o pack já traz no `servers.dat` — assim o seeder da Fase 4 não encontra nada a fazer na primeira instalação. Se quiser outro nome, é trocar as duas constantes na task 2.
4. **`url_home` aponta pro seu repositório.** A §8 não falava disso; foi incluído porque a GPLv3 §6 exige que a oferta de código-fonte seja da **versão modificada**, não do upstream. Confirme que você quer esse repo como face pública do código.

---

## Escopo fora deste plano

Este é o **plano 1 de 3** (spec §12). Não faz aqui, de propósito:

- Instalação/ atualização do modpack Technic, `servers.dat`, `version.config` → **plano 2** (fases 2+3+4+5)
- Máquina de estados, atualização por manifesto, offline/401 → **plano 3** (fase 6)
- Troca da tela inicial pela `DedicatedScreen` — mas o id `"system_unofficial_notice"` já fica reservado pra ela
