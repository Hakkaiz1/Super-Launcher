# Super Launcher — Contas no Modo Dedicado — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dar ao jogador do modo dedicado um botão de contas na TopBar que abre o `AccountManageScreen` já existente — habilitando nick offline (conta local), troca, exclusão e login Microsoft — sem afrouxar o travamento da §6.

**Architecture:** Reuso puro, zero telas/chaves/strings novas. Um novo item de TopBar (`TopBarRailItem`) gated por `if (BuildKeys.DEDICATED_MODE)`, inserido imediatamente antes do item de configurações, chama uma função pura `navigateToAccountScreen(...)` que limpa as telas "cheas" antes de empilhar `NormalNavKey.AccountManager(FirstLoginMenu.NONE)`. A função existe como *seam* testável em JVM: o botão e o teste chamam exatamente o mesmo código, então a navegação do botão é verificada por teste unitário e não só no aparelho.

**Tech Stack:** Kotlin 2.x, Jetpack Compose (Material 3), Navigation 3 (`NavBackStack`/`BackStackNavKey`), JUnit4, Gradle KTS com `buildKeys`.

**Spec:** `docs/superpowers/specs/2026-10-01-super-launcher-accounts-design.md`

## Global Constraints

- `BuildKeys.DEDICATED_MODE` é `true` neste build (`ZalithLauncher/build.gradle.kts:221`). O botão de contas **só** existe no modo dedicado (`if (BuildKeys.DEDICATED_MODE)`), porque em builds normais a home já expõe contas pelo avatar.
- **§6 travamento intacto:** versões, downloads, Terracota/multiplayer, gerenciador de arquivos e `HomeCards.userCardTypes` continuam escondidos **e** guardados. Nenhum gate pode ser afrouxado; o único acréscimo é *permitir* contas.
- **Nada novo:** nenhuma string, drawable, dependência, tela ou chave de navegação. Reutilizar `R.drawable.ic_person_outlined`, `R.string.page_title_account_list`, `NormalNavKey.AccountManager`, `AccountManageScreen`, `AccountUtils.localLogin`, `AccountsManager.setCurrentAccount`.
- `ui/screens/content/AccountManageScreen.kt` **não** é tocado.
- O aviso `Unofficial Modified Version by Hakkaiz` (`unofficial_modified_notice`, `translatable="false"`) permanece intacto e incondicional na `DedicatedScreen`.
- Todo arquivo novo começa com o cabeçalho GPLv3 do projeto, verbatim:
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
- **Testes:** JUnit4 com imports explícitos (`org.junit.Test`, `org.junit.Assert.*`), sem `kotlinx-coroutines-test`, imports não usados removidos. `internal` do source set principal é visível para `src/test` neste projeto.
- **Comandos** (sempre pela junction — `ndk-build` não aceita espaços):
  - suíte completa: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --console=plain` (~3 min)
  - teste focado: mesma linha + `--tests "*DedicatedLockTest*"`
  - build: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:assembleDebug --console=plain`
- **Gate por task:** suíte completa **e** `assembleDebug` verdes antes de cada `git commit`; commits convencionais; **NUNCA `git push`** — o push é do controller.
- Nota de ambiente: o teste pré-existente `TestAbstractAPI.testMirroredAPI` usa rede viva e já flakou com `SocketTimeoutException`; se falhar, reexecutar antes de concluir.

## Review Focus

1. **Nick offline precisa entrar no jogo com o nick digitado.** A conta local persiste no Room e o nick só chega ao jogo via `auth_player_name` (`LaunchArgs.kt:357-370`) — o teste de navegação não prova isso. *Teste:* verificação manual D3 (criar nick, jogar, conferir o nome no jogo).
2. **Trocar de conta precisa mudar a conta do próximo lançamento** (`AllSettings.currentAccount` via `AccountsManager.setCurrentAccount`) — sem cobertura unitária. *Teste:* verificação manual D4 (duas contas, trocar o radio, jogar).
3. **Voltar da tela de contas tem que cair na tela do pack**, sem pilha de telas "cheas" (settings/download/multiplayer) embaixo. *Teste:* asserção unitária no Task 1 — após `navigateToAccountScreen`, o back stack é `[LauncherMain, AccountManager]` (nada mais) e após `onBack` volta a ser `[LauncherMain]`.
4. **Sem conta, tocar `Jogar` tem que abrir a tela de contas** (`LaunchGameOperation.NoAccount` → `toAccountManageScreen`, sem gate em `MainActivity.kt:350`). Não é alcançável por teste unitário (depende do ViewModel de launch e do clique). *Teste:* verificação manual D5.
5. **Conta Microsoft sem rede/token expirado não pode travar o launcher** — fluxos `MicrosoftReloginDialog`/`AccountRefreshFailed` são pré-existentes; a mudança não pode regredir nada aqui. *Teste:* verificação manual D6.

## Estrutura de arquivos

| Arquivo | Ação | Responsabilidade |
|---|---|---|
| `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/main/AccountNavigation.kt` | **criar** | Uma função pura: a navegação exata do botão de contas (limpa telas cheas → empilha `AccountManager(FirstLoginMenu.NONE)`). Sem Compose, sem estado — testável em JVM. |
| `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/main/MainScreen.kt` | **modificar** | `inAccountScreen` derivado, lambda `toAccountScreen` no bloco `TopBar(...)`, e o novo `TopBarRailItem` gated, inserido antes do item de configurações. |
| `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/ui/screens/content/DedicatedLockTest.kt` | **modificar** | Novo caso: contas alcançáveis, voltar cai no pack, funil de download continua bloqueado. |

---

### Task 1: Botão de contas na TopBar do modo dedicado

**Files:**
- Create: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/main/AccountNavigation.kt`
- Modify: `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/main/MainScreen.kt:263-265` (estado derivado), `:188-202` (lambda no `TopBar`), `:440-447` (item antes de configurações)
- Test: `ZalithLauncher/src/test/java/com/movtery/zalithlauncher/ui/screens/content/DedicatedLockTest.kt`

**Interfaces:**
- Consumes: `ScreenBackStackViewModel.mainScreen` (`NestedNavKey.Main`, um `BackStackNavKey<TitledNavKey>` cujo `backStack` já nasce com `NormalNavKey.LauncherMain` — `NestedNavKey.kt:40-44`); `ScreenBackStackViewModel.clearBeforeNavKeys: List<KClass<*>>` (`ScreenBackStackViewModel.kt:52-56`); `onBack(currentBackStack: NavBackStack<E>)` (`ui/screens/_Navigation.kt:40`); `TopBarRailItem(selected, painter, text, onClick)` (`MainScreen.kt:453+`).
- Produces: `internal fun navigateToAccountScreen(backStack: BackStackNavKey<TitledNavKey>, clearBeforeNavKeys: List<KClass<*>>)` — chamada pelo botão **e** pelo teste.

- [ ] **Step 1: Escrever o teste que falha**

Adicionar ao fim da classe `DedicatedLockTest` (mesmos imports explícitos do arquivo; acrescentar `com.movtery.zalithlauncher.ui.screens.NestedNavKey`, `com.movtery.zalithlauncher.ui.screens.NormalNavKey`, `com.movtery.zalithlauncher.ui.screens.onBack`, `com.movtery.zalithlauncher.ui.screens.main.navigateToAccountScreen`. `FirstLoginMenu` é do mesmo pacote do teste, não precisa de import):

```kotlin
    @Test
    fun `dedicated mode keeps accounts reachable while the download funnel stays blocked`() {
        assertTrue(BuildKeys.DEDICATED_MODE)

        val viewModel = ScreenBackStackViewModel()
        val mainScreen = viewModel.mainScreen

        navigateToAccountScreen(mainScreen, viewModel.clearBeforeNavKeys)

        assertEquals(
            "abrir contas deve empilhar apenas LauncherMain + AccountManager",
            listOf<Any>(NormalNavKey.LauncherMain, NormalNavKey.AccountManager(FirstLoginMenu.NONE)),
            mainScreen.backStack.toList()
        )

        onBack(mainScreen.backStack)

        assertEquals(
            "o voltar deve cair na tela do pack (LauncherMain)",
            listOf<Any>(NormalNavKey.LauncherMain),
            mainScreen.backStack.toList()
        )

        viewModel.navigateToDownload()

        assertFalse(
            "o funil de download deve continuar bloqueado no modo dedicado",
            mainScreen.backStack.any { it is NestedNavKey.Download }
        )
    }
```

Acrescentar `import org.junit.Assert.assertEquals` ao arquivo.

- [ ] **Step 2: Rodar o teste e verificar que falha**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*DedicatedLockTest*" --console=plain`
Expected: FALHA de compilação — `Unresolved reference 'navigateToAccountScreen'` (a função ainda não existe). Isso é o RED esperado.

- [ ] **Step 3: Criar o seam de navegação**

Criar `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/main/AccountNavigation.kt` com o cabeçalho GPLv3 (Global Constraints) e:

```kotlin
package com.movtery.zalithlauncher.ui.screens.main

import com.movtery.zalithlauncher.ui.screens.BackStackNavKey
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.TitledNavKey
import com.movtery.zalithlauncher.ui.screens.content.FirstLoginMenu
import kotlin.reflect.KClass

/**
 * Navegação do botão de contas da TopBar no modo dedicado (spec §5.1).
 *
 * É uma função pura (sem Compose nem estado) para que o mesmo código usado
 * pelo botão possa ser verificado em teste de unidade: a tela de contas já
 * existe e já é registrada no modo dedicado, o que faltava era o ponto de
 * entrada. As telas "cheas" são removidas antes de empilhar para que o voltar
 * sempre caia na tela do pack.
 */
internal fun navigateToAccountScreen(
    backStack: BackStackNavKey<TitledNavKey>,
    clearBeforeNavKeys: List<KClass<*>>
) {
    backStack.removeAndNavigateTo(
        removes = clearBeforeNavKeys,
        screenKey = NormalNavKey.AccountManager(FirstLoginMenu.NONE)
    )
}
```

- [ ] **Step 4: Rodar o teste e verificar que passa**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest --tests "*DedicatedLockTest*" --console=plain`
Expected: `DedicatedLockTest` 3/3 verde (os 2 casos anteriores + o novo), `BUILD SUCCESSFUL`, sem warnings novos. Este é o GREEN.

- [ ] **Step 5: Derivar o estado da tela de contas em `MainScreen.kt`**

Em `MainScreen.kt`, junto de `inMultiplayerScreen`/`inDownloadScreen`/`inSettingsScreen` (linhas 263-265), acrescentar:

```kotlin
    val inAccountScreen = mainScreenKey is NormalNavKey.AccountManager
```

- [ ] **Step 6: Ligar a lambda `toAccountScreen` ao `TopBar`**

No bloco `TopBar(...)` (após `toDownloadScreen = { ... },` nas linhas 194-196), acrescentar:

```kotlin
                toAccountScreen = {
                    navigateToAccountScreen(
                        backStack = screenBackStackModel.mainScreen,
                        clearBeforeNavKeys = screenBackStackModel.clearBeforeNavKeys
                    )
                },
```

- [ ] **Step 7: Renderizar o item de conta na TopBar**

Primeiro, **na declaração do composable `TopBar`** (assinatura em `MainScreen.kt:255-256`, junto de `toSettingsScreen`/`toDownloadScreen`), acrescentar o parâmetro:

```kotlin
    toAccountScreen: () -> Unit,
```

Depois, **na chamada** de `TopBar(...)` (mesmo bloco do Step 6), passar `toAccountScreen = toAccountScreen,` na posição correspondente da assinatura.

Por fim, dentro do corpo do `TopBar`, inserir o item imediatamente **antes** do `TopBarRailItem` de configurações (linha 440):

```kotlin
                if (BuildKeys.DEDICATED_MODE) {
                    TopBarRailItem(
                        selected = inAccountScreen,
                        painter = painterResource(R.drawable.ic_person_outlined),
                        text = stringResource(R.string.page_title_account_list),
                        onClick = {
                            if (!inAccountScreen) toAccountScreen()
                        },
                    )
                }
```

Nenhum import novo é preciso neste arquivo: `painterResource`, `stringResource`, `TopBarRailItem`, `BuildKeys` e `NormalNavKey` já são usados; `navigateToAccountScreen` está no mesmo pacote (`ui.screens.main`).

- [ ] **Step 8: Gate completo — suíte e build**

Run: `Set-Location D:\ZLBuild; .\gradlew.bat :ZalithLauncher:testDebugUnitTest :ZalithLauncher:assembleDebug --console=plain`
Expected: `BUILD SUCCESSFUL` nos dois; suíte completa verde (o total atual é 152 testes: 151 anteriores + 1 novo); nenhum warning novo introduzido pelos arquivos tocados. Se `testMirroredAPI` falhar com `SocketTimeoutException`, reexecutar.

- [ ] **Step 9: Commitar**

```bash
git add ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/main/AccountNavigation.kt ZalithLauncher/src/main/java/com/movtery/zalithlauncher/ui/screens/main/MainScreen.kt ZalithLauncher/src/test/java/com/movtery/zalithlauncher/ui/screens/content/DedicatedLockTest.kt
git commit -m "feat(ui): expose account management in the dedicated TopBar"
```

Um único commit. **Não** fazer push.

---

## Verificação no aparelho (após o commit; exige toque humano)

`adb input` é bloqueado neste aparelho (INJECT_EVENTS), então os toques são do usuário; o agente só captura e lê o log.

Preparo (controller):

```powershell
& 'C:\Users\Administrator\AppData\Local\Android\Sdk\platform-tools\adb.exe' install -r 'D:\Meus Projetos\Zalith Super\ZalithLauncher2\ZalithLauncher\build\outputs\apk\debug\SuperLauncher-Debug-1.0.0.apk'
```

Checklist (capturas via `adb shell screencap -p /sdcard/x.png` + `adb pull`):

- **D1** — TopBar do modo dedicado: ícone de conta **ao lado da engrenagem** (que continua no canto direito) e **sem** pasta/Terracota/Downloads.
- **D2** — Toque no ícone abre a lista de contas; o voltar retorna à tela do pack (com `DBC Super (Oficial)` + botão *Jogar*).
- **D3** (Review Focus 1) — *Adicionar Conta* → *Offline* → digitar um nick → criar; voltar; *Jogar*; o jogo entra com esse nick.
- **D4** (Review Focus 2) — com duas contas, trocar o radio altera a conta usada no *Jogar* seguinte.
- **D5** (Review Focus 4) — sem nenhuma conta, *Jogar* mostra o toast e abre a tela de contas.
- **D6** (Review Focus 5) — com conta Microsoft adicionada e rede desligada, *Jogar* mostra o diálogo de relogin (não trava).