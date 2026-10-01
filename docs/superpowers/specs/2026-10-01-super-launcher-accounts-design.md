# Super Launcher — Acesso a Contas no Modo Dedicado — Design

**Data:** 2026-10-01
**Estado:** aprovado em conversa (design), aguardando revisão deste spec
**Escopo:** Fase 7 do projeto Super Launcher (o Plano 2 cobriu as Fases 2–5; a Fase 6 continua pendente e não é tocada aqui)

## 1. Objetivo

No launcher travado ao pack `dbc-super-oficial`, o jogador precisa de duas coisas que hoje não existem:

1. **Entrar com um nick qualquer**, sem obrigar conta Microsoft (conta offline).
2. **Gerenciar e trocar de contas** — hoje, com uma conta já configurada, não há caminho nenhum para trocar.

Ambas serão entregues por **reuso do fluxo de contas já existente** no launcher, sem criar telas, chaves de navegação, estado ou strings novas.

## 2. Decisão

**Adotado:** um botão de conta na TopBar do modo dedicado, ao lado do botão de configurações, que abre o `AccountManageScreen` já existente (que já é registrado e funcional em modo dedicado — o que falta é apenas o ponto de entrada).

**Alternativa rejeitada:** campo de nick inline na `DedicatedScreen` com botão "Jogar com nick" (1 toque). Rejeitada por duplicar a lógica de criação de conta, criar UI nova (mais superfície de bug) e por a conta criada continuar aparecendo no gerenciador de qualquer forma. YAGNI.

## 3. Premissas verificadas no código

- `AccountManageScreen` (`ui/screens/content/AccountManageScreen.kt:150`) é o gerenciador completo: lista, seleção por radio (`AccountsManager.setCurrentAccount`), exclusão, skin, e os dialogs de login.
- O `entry<NormalNavKey.AccountManager>` existe em `MainScreen.kt:572-583` **sem** gate `DEDICATED_MODE` — a tela já renderiza no modo dedicado.
- `toAccountManageScreen` (`MainActivity.kt:350-354`) também não tem gate; o fluxo `LaunchGameOperation.NoAccount` (`LauncherElements.kt:225-235`) já leva à tela de contas quando não há conta — apenas mostra um toast e, hoje, não há como chegar lá a partir da `DedicatedScreen`.
- Conta offline: `AccountUtils.localLogin(userName, userUUID?)` (`AccountUtils.kt:326-341`) cria e persiste `Account(type = LOCAL)`; o nick entra no jogo por `auth_player_name` (`LaunchArgs.kt:357-370`) e o UUID é derivado offline (`OfflinePlayer:<nick>`).
- A TopBar já tem um item sempre visível (configurações) e o padrão de gate `if (!BuildKeys.DEDICATED_MODE)` para os três itens escondidos (`MainScreen.kt:407/418/429`).
- Drawable disponível: `R.drawable.ic_person_outlined` (vector).

## 4. Comportamento

### 4.1 TopBar no modo dedicado

Passa a mostrar, da esquerda para a direita na borda direita: menu de tarefas (visível), **conta**, **configurações**. Continuam escondidos: gerenciador de arquivos, Terracota/multiplayer e Downloads.

O item de conta é um `TopBarRailItem` (mesmo componente dos demais), com `text = stringResource(R.string.page_title_account_list)` ("Lista de contas" / "Account List") e `painterResource(R.drawable.ic_person_outlined)`. Fica **imediatamente antes** do item de configurações, para que a engrenagem continue sendo o último item da borda direita.

### 4.2 Cenários

1. **Trocar de conta**: tocar no ícone de conta → tela de contas → tocar no radio da outra conta → voltar. A conta selecionada passa a ser usada no próximo *Jogar*.
2. **Nick qualquer (offline)**: tocar no ícone de conta → *Adicionar Conta* → *Offline* → "Coloque seu nick" → digitar → criar. Voltar e tocar *Jogar*.
3. **Sem conta nenhuma**: tocar *Jogar* → toast `game_launch_no_account` e abertura da tela de contas (comportamento já existente, hoje inalcançável a partir da tela do pack porque o item não existe).
4. **Voltar**: o botão de voltar da TopBar volta para a `DedicatedScreen`; o estado do pack (`DedicatedPackState`, singleton) não é perdido ao navegar para as telas de contas/configurações.

## 5. Mudanças de código

### 5.1 `ui/screens/main/MainScreen.kt`

**(a) Novo estado derivado**, junto de `inSettingsScreen` (`MainScreen.kt:263-265`):

```kotlin
val inAccountScreen = mainScreenKey is NormalNavKey.AccountManager
```

**(b) Nova lambda `toAccountScreen`** no bloco `TopBar(...)` (`MainScreen.kt:188-202`), espelhando `toMultiplayerScreen`: remove as telas "cheias" anteriores para que o voltar volte ao pack:

```kotlin
toAccountScreen = {
    screenBackStackModel.mainScreen.removeAndNavigateTo(
        removes = screenBackStackModel.clearBeforeNavKeys,
        screenKey = NormalNavKey.AccountManager(FirstLoginMenu.NONE)
    )
},
```

**(c) Novo item na TopBar**, inserido imediatamente antes do item de configurações (`MainScreen.kt:440`):

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

O gate é `if (BuildKeys.DEDICATED_MODE)` (positivo) — nos builds não dedicados, o item **não** aparece, porque a home normal já expõe contas pelo avatar (`LauncherScreen.kt:165-169`, `:509-522`). Isso evita duplicar o mesmo destino em dois lugares do mesmo build.

Imports novos: `com.movtery.zalithlauncher.ui.screens.content.FirstLoginMenu` (se ainda não importado no arquivo). Nenhum import removido; `painterResource`, `stringResource`, `TopBarRailItem` e `BuildKeys` já são usados no arquivo.

### 5.2 `src/test/java/com/movtery/zalithlauncher/ui/screens/content/DedicatedLockTest.kt`

Novo caso, no estilo dos dois existentes (JUnit4, `ScreenBackStackViewModel` real, asserção sobre `backStack` — `currentKey` só é espelhado em composição e não serve em teste de unidade):

```kotlin
@Test
fun `dedicated mode keeps accounts reachable while the download funnel stays blocked`() {
    assertTrue(BuildKeys.DEDICATED_MODE)

    val viewModel = ScreenBackStackViewModel()
    viewModel.mainScreen.removeAndNavigateTo(
        removes = viewModel.clearBeforeNavKeys,
        screenKey = NormalNavKey.AccountManager(FirstLoginMenu.NONE)
    )

    assertTrue(
        "a tela de contas deve ser alcançável no modo dedicado",
        viewModel.mainScreen.backStack.any { it is NormalNavKey.AccountManager }
    )

    viewModel.navigateToDownload()

    assertFalse(
        "o funil de download deve continuar bloqueado no modo dedicado",
        viewModel.mainScreen.backStack.any { it is NestedNavKey.Download }
    )
}
```

`FirstLoginMenu` é do mesmo pacote do teste (`com.movtery.zalithlauncher.ui.screens.content`); `NormalNavKey` precisa de import.

## 6. Tabela de gates (§6) após a mudança

| Destino | Antes | Depois |
|---|---|---|
| Lista de versões (`NormalNavKey.VersionsManager`) | escondido + guardado | **inalterado** (escondido + guardado) |
| Downloads (`NestedNavKey.Download`) | escondido + guardado | **inalterado** |
| Multiplayer / Terracota | escondido | **inalterado** |
| Gerenciador de arquivos | escondido | **inalterado** |
| Cartões da home (`HomeCards.userCardTypes`) | vazio | **inalterado** |
| **Contas (`NormalNavKey.AccountManager`)** | sem ponto de entrada | **permitido, via botão na TopBar** |

A tela de contas não possui links para versões, downloads ou multiplayer — abrir contas não cria caminho de fuga do travamento.

## 7. Recursos reutilizados (nada novo)

| Recurso | Origem |
|---|---|
| Ícone | `R.drawable.ic_person_outlined` (já existe) |
| Texto do botão | `R.string.page_title_account_list` (EN + pt-BR) |
| Tela | `AccountManageScreen` + dialogs existentes (`LoginMenuDialog`, `LocalLoginDialog`, `MicrosoftLoginTipDialog`, `MicrosoftReloginDialog`) |
| Criação de conta offline | `AccountUtils.localLogin` |
| Seleção/troca | `AccountsManager.setCurrentAccount` |

Nenhuma string nova, nenhum drawable novo, nenhuma dependência nova.

## 8. Erros e bordas

- **Conta Microsoft sem rede**: no *Jogar*, o fluxo existente `MicrosoftReloginDialog`/`AccountRefreshFailed` aparece (o launcher nunca tenta lançar com token inválido). Nada novo.
- **Conta offline**: nunca exige rede nem token.
- **Nenhuma conta**: `LaunchGameOperation.NoAccount` → toast + abre a tela de contas (`LauncherElements.kt:225-235`). O guard `if (!BuildKeys.DEDICATED_MODE)` em `:210` continua aplicável apenas ao caso *NoVersion*, que não deve abrir o gerenciador de versões.
- **Tocar no botão de conta já estando na tela de contas**: `if (!inAccountScreen)` impede navegação duplicada (mesmo padrão dos outros itens).
- **Perda de estado do pack**: `DedicatedPackState` é singleton; navegar para contas/configurações não dispara `launchCheck` e não altera o estado.

## 9. Testes

**Automático (JUnit4, sem mocks):** o novo caso de `DedicatedLockTest` (§5.2) — contas alcançáveis + funil de download ainda bloqueado. Gate: suíte completa verde + `assembleDebug` verde (a UI Compose é verificada por compilação).

**Manual no aparelho** (o `adb input` é bloqueado por INJECT_EVENTS neste MIUI, então exige toque humano):
1. TopBar em modo dedicado mostra o ícone de conta ao lado da engrenagem — e **não** mostra pasta/Terracota/Downloads.
2. Tocar no ícone abre a lista de contas; voltar retorna à tela do pack.
3. *Adicionar Conta* → *Offline* → digitar um nick → conta criada e selecionada; voltar; *Jogar* → o jogo entra com esse nick.
4. Com duas contas, trocar o radio altera a conta usada no *Jogar* seguinte.
5. Nenhum caminho clicável daqui leva a versões, downloads ou multiplayer.

## 10. Riscos

| Risco | Mitigação |
|---|---|
| Quebrar o travamento (§6) | O único gate alterado é *adicionar* um destino de contas; o teste novo fixa explicitamente que downloads continuam bloqueados; a tela de contas não tem links para as rotas proibidas |
| Duplicar o destino de contas em builds não dedicados | Gate positivo `if (BuildKeys.DEDICATED_MODE)` |
| Duplicar a tela no back stack | `if (!inAccountScreen)` + `removeAndNavigateTo(clearBeforeNavKeys)`, mesmo padrão de settings/multiplayer |
| Regressão de layout na TopBar | Item inserido no mesmo `Row`, usando o mesmo `TopBarRailItem` e o mesmo tamanho de ícone dos vizinhos |

## 11. Fora de escopo

- Nick inline na `DedicatedScreen` (rejeitado na decisão).
- Alterações no `AccountManageScreen` (nenhuma).
- Novos gates de navegação ou qualquer funcionalidade da Fase 6 (§5.5 estados de erro, matriz manual 2–5).
- Erro de mod loading observado no dispositivo (`lwjgl-3.3.3-merged-modules.jar` × ASM 5.0.3 do FML 1.7.10): é questão separada, de compatibilidade do pack/Forge, e será tratada à parte.