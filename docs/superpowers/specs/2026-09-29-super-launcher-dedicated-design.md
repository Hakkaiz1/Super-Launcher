# Super Launcher — Launcher dedicado ao servidor DBC Super

- **Data:** 2026-09-29
- **Status:** Aguardando revisão do usuário
- **Repositório:** `ZalithLauncher/ZalithLauncher2` (fork do ZalithLauncher 2, GPLv3 + termos adicionais)
- **Pack de referência:** [`dbc-super-oficial`](https://www.technicpack.net/modpack/dbc-super-oficial.1132904)

---

## 1. Contexto

O ZalithLauncher 2 é um launcher de Minecraft: Java Edition para Android (Jetpack Compose + Material 3, motor de launch do PojavLauncher). Esta especificação descreve uma **build dedicada** que:

1. distribui um launcher **travado no modpack** do servidor DBC Super (Minecraft 1.7.10 + Forge);
2. instala/atualiza esse modpack automaticamente a partir da **Technic Platform**;
3. já nasce com controles e servidor multiplayer configurados;
4. é **renomeada** e exibe o aviso de versão modificada exigido pela licença.

### Requisitos acordados

| # | Requisito |
|---|---|
| R1 | Launcher **totalmente travado** no pack: sem lista de versões, sem telas de download |
| R2 | Base **1.7.10 + Forge baixada na primeira abertura** (não vai dentro do APK) |
| R3 | Modpack vindo da **Technic Platform**, atualizável pelo `version` da API |
| R4 | Controles já configurados e pré-selecionados (layout padrão por enquanto) |
| R5 | Servidor do DBC Super **já na lista de multijogador** |
| R6 | App renomeado para **Super Launcher** + aviso *"Unofficial Modified Version by Hakkaiz"* |

### Não-objetivos (YAGNI)

- Servidor **Solder** próprio (o pack usa `solder: null` → modo zip direto).
- Múltiplos packs, seleção de pack em runtime.
- Login/autenticação customizada.
- Qualquer alteração no jogo em si.

---

## 2. Dados de configuração

### `ZalithLauncher/gradle.properties`

```properties
launcher_name=SuperLauncher          # identificador: namespace MMKV, nome do APK, BuildKeys.LAUNCHER_IDENTIFIER
launcher_app_name=Super Launcher     # nome exibido
launcher_short_name=SL               # não pode conter "ZL"
launcher_version_code=...            # incrementar em relação ao valor atual (pode reusar o do upstream)
launcher_version_name=...            # alinhar ao version_code, ex.: 1.0.0
```

Os dois valores mantêm os campos obrigatórios preenchidos; incrementar evita conflito de atualização se algum dia o APK for distribuído por cima de outra build.

> ⚠️ `launcher_name` vira `BuildKeys.LAUNCHER_IDENTIFIER`, que é o **namespace do MMKV** (`launcherMMKV()`). Isso isola os settings da build dedicada em relação à oficial — comportamento desejado.

### Novos BuildKeys (`ZalithLauncher/build.gradle.kts`, bloco `buildKeys {}`)

| Chave | Tipo | Valor | Uso |
|---|---|---|---|
| `DEDICATED_MODE` | `bool` | `true` | Habilita o travamento da UI |
| `DEDICATED_PACK_SLUG` | `string` | `dbc-super-oficial` | Slug na Technic Platform |
| `DEDICATED_SERVER_NAME` | `string` | a definir | Nome do servidor a garantir em `servers.dat` |
| `DEDICATED_SERVER_IP` | `string` | a definir (ex.: `dbcsuper.com`) | Endereço do servidor |
| `DEDICATED_NOTICE` | `string` | `Unofficial Modified Version by Hakkaiz` | Aviso de versão modificada |

Leitura idiomática: `BuildKeys.DEDICATED_PACK_SLUG` (const inlined), mesmo padrão de `BuildKeys.CURSEFORGE_API`.

---

## 3. Arquitetura

### Componentes novos

Todos sob `ZalithLauncher/src/main/java/com/movtery/zalithlauncher/`:

| Arquivo | Responsabilidade |
|---|---|
| `game/download/modpack/technic/TechnicApi.kt` | Cliente da Platform API: busca do pack, parse do JSON |
| `game/download/modpack/technic/TechnicPackInstaller.kt` | Instala e atualiza o pack (8 etapas, §5.2) |
| `game/dedicated/DedicatedPackState.kt` | Máquina de estados `CHECKING / OFFLINE / NOT_INSTALLED / NEEDS_UPDATE / READY / INSTALLING / FAILED` |
| `game/dedicated/DedicatedSeeder.kt` | Garante servidor em `servers.dat`, liga o layout de controle, grava `version.config` |
| `ui/screens/content/dedicated/DedicatedScreen.kt` | Tela única: Instalar / Atualizar / Jogar (+ progresso) |

### Componentes existentes reutilizados (sem modificar)

| Componente | Papel |
|---|---|
| `game/download/game/GameInstaller.kt` | Baixa vanilla + assets + bibliotecas, roda o instalador legacy do Forge |
| `game/download/game/forge/Install.ForgeLike.kt` | Caminho legacy do Forge (< 1.13) — cobre 1.7.10 |
| `game/download/engine/{DownloadEngine,BatchDownloader,Fetcher}.kt` | Download com SHA-1, failover e progresso |
| `game/version/installed/VersionsManager.kt` | Seleção/persistência da versão atual |
| `game/version/multiplayer/AllServers.kt` + `ServerData.kt` | Leitura/escrita de `servers.dat` (NBT cru) |
| `game/control/ControlManager.kt` | Seed do layout padrão em `control_layouts/` |
| `components/UnpackSingleTask.kt` / `AbstractUnpackTask.kt` | Extração de assets do APK (se necessário) |
| `viewmodel/ScreenBackStackViewModel.kt`, `ui/screens/main/MainScreen.kt` | Pontos de travamento da navegação |

---

## 4. Dados verificados do pack

Coletados em 2026-09-29 — servem como fixture para os testes.

**API:**

```
GET https://api.technicpack.net/modpack/dbc-super-oficial?build=1166
{
  "displayName": "DBC Super (Oficial)",
  "minecraft": "1.7.10",
  "version": "10.8",                      ← identidade para checagem de update
  "url": "https://www.dropbox.com/scl/fi/.../ATT58.zip?...&dl=1",
  "solder": null,
  "icon": { "url": "https://cdn.technicpack.net/platform2/pack-icons/1132904.png?..." }
}
```

> **`build` precisa ser o número atual do launcher (1166).** Com `build=402` ou `434` a API responde **401**. `GET /launcher/version/stable4` devolve o build corrente.

**Zip (90,5 MB, 1356 entradas, hospedado no Dropbox):**

```
bin/1.7.10.json  bin/minecraft.jar  bin/modpack.jar  bin/version.json  bin/version
bin/natives/*.dll                     ← DLLs Windows: NÃO copiar
config/  mods/  resourcepacks/  resources/
options.txt  servers.dat
```

**`bin/version.json`:**

```json
{ "id": "1.7.10-Forge10.13.4.1558-1.7.10",
  "inheritsFrom": "1.7.10",
  "libraries": ["net.minecraftforge:forge:1.7.10-10.13.4.1558-1.7.10", ...] }
```

**`servers.dat` (14.321 bytes):** magic `0a 00` → **NBT cru, não gzip** — exatamente o formato que `AllServers` lê/escreve. Conteúdo: `{ name: "Minecraft Server", ip: "dbcsuper.com", icon: <png base64> }`.

> **Decisão de compatibilidade:** o Minecraft 1.7.10 usa `CompressedStreamTools.read(File)` / `safeWrite(...)` sem gzip (gzip só no `level.dat`). Logo `AllServers(compressed=false)` está **correto** para 1.7.10 e nenhum fallback gzip é necessário.

---

## 5. Fluxos

### 5.1 Primeira abertura

```
SplashActivity  →  descompacta componentes/JREs (fluxo existente, sem rede nova)
MainActivity    →  DedicatedPackState.check()
                    ├─ sem manifesto       → DedicatedScreen[Instalar]
                    ├─ identidade ≠ API    → DedicatedScreen[Atualizar]
                    ├─ igual               → DedicatedScreen[Jogar]
                    └─ sem rede            → DedicatedScreen[Offline · Tentar de novo]
```

**Identidade do pack** (comparação por igualdade de string, não semver): `packVersion` do manifesto contra o campo `version` da API. Se a API vier com `version` vazio/ausente, cai para o campo `url` do pack como identidade (ele muda quando publicam zip novo); se os dois vierem vazios, loga aviso e considera `READY` — nunca fica preso em loop de atualização.

O seed e o refresh seguem a ordem obrigatória:

```
PathManager.refreshPaths() → GamePathManager.reloadPath() / waitForRefresh()
→ VersionsManager.waitForRefresh() → DedicatedSeeder / saveCurrentVersion()
```

Escrever antes disso usa o diretório de jogo errado.

### 5.2 Instalação (`TechnicPackInstaller`)

| # | Etapa | Detalhe |
|---|---|---|
| 1 | Resolver | `TechnicApi.getPack(slug)` → `url`, `version`, `minecraft` (`icon` só para exibição interna, §6) |
| 2 | Baixar | zip → `cache/temp_dedicated_pack/` via `DownloadEngine`; progresso no `TaskMenu` |
| 3 | Extrair | → diretório de staging (temp) |
| 4 | Resolver versão | `bin/version.json` → senão `bin/modpack.jar!/version.json` → senão campo `minecraft` da API. Extrai `inheritsFrom` (= 1.7.10) e o build do Forge (`10.13.4.1558`) |
| 5 | Instalar base | `GameDownloadInfo(gameVersion="1.7.10", customVersionName=<slug>, forge="10.13.4.1558")` → **`GameInstaller`** (caminho legacy do Forge) |
| 6 | Overlay | Copia o conteúdo do zip para `versions/<slug>/` **exceto `bin/`**, registrando o manifesto |
| 7 | Semeia | `version.config`, servidor em `servers.dat`, layout de controle; grava `pack-manifest.json` (atômico) → então `READY` |
| 8 | Selecionar | `VersionsManager.saveCurrentVersion(<slug>)` |

**Por que a etapa 5 e não usar as libs do `version.json` do pack:** esse JSON **não tem SHA-1**. O `DownloadTask` valida por hash; sem hash, ou o arquivo nunca é considerado válido, ou passa sem verificação. Rodando o instalador legacy oficial, todas as bibliotecas saem com hash correto e a verificação de integridade do launch (`GameLaunchFlow`, `VERIFY_AND_REPAIR`) não apaga nada — `skipGameIntegrityCheck` permanece `false`.

O `bin/version.json` é usado **apenas como fonte de versão**, não como JSON final da instância.

### 5.3 Atualização e manifesto

`pack-manifest.json` (gravado ao lado de `version.config`, dentro de `versions/<slug>/<LAUNCHER_IDENTIFIER>/`):

```json
{ "slug": "dbc-super-oficial", "packVersion": "10.8",
  "files": ["mods/DragonBlockC-v1.4.85.jar", "config/...", "..."] }
```

Regra de atualização:

1. extrai o zip novo em staging;
2. **apaga** os arquivos listados no manifesto antigo que não existem no pack novo (mods removidos pelo autor não ficam órfãos);
3. **sobrescreve** os arquivos do pack novo;
4. **nunca** toca em `saves/`, `options.txt` nem `servers.dat` — são do jogador e por isso ficam **fora do manifesto** (na primeira instalação eles são copiados normalmente);
5. regrava o manifesto (atômico).

### 5.4 Jogar

Pipeline de launch inalterado (`GameLaunchFlow` → `LaunchArgs` → `GameLauncher`). O jogador entra no menu e o servidor já está na lista.

### 5.5 Estados e erros

| Situação | Comportamento |
|---|---|
| Queda no meio da instalação | manifesto não foi gravado → volta a `NEEDS_UPDATE`, overlay refeito do staging. A versão boa nunca fica pela metade |
| Sem rede | tela `Offline` com "Tentar de novo", sem retry automático em loop |
| API responde 401 | erro explícito: *"A API do Technic rejeitou o número de build"* — nunca silencioso |
| Dropbox redireciona/bloqueia | seguir redirect + User-Agent de navegador (verificado: range e redirect funcionam) |
| Integridade no launch | hashes corretos (etapa 5) → nada é apagado; se o jogador apagar arquivo, `VERIFY_AND_REPAIR` re-baixa do Mojang |

---

## 6. Modo dedicado — travamento

Flag: `BuildKeys.DEDICATED_MODE`. As entradas ficam **escondidas e guardadas** (deep links e caminhos de erro não escapam), não apenas escondidas.

| O quê | Ponto de edição |
|---|---|
| Botões Download / Multiplayer / pasta (top bar) | `ui/screens/main/MainScreen.kt` → `TopBar` |
| Funil de downloads | `viewmodel/ScreenBackStackViewModel.kt` → `navigateToDownload()` vira no-op |
| "Instalar" no gerenciador de versões | `VersionsManageScreen.onInstall` |
| Falha de launch → gerenciador | `MainActivity` (linha ~350) e `LauncherElements.NoVersion` |
| Dropdown / long-press do seletor de versão | `LauncherScreen.VersionsContent` |
| Cards da home | `content/home/HomeCards.kt` → `userCardTypes` filtrado |
| Tela inicial | `LauncherMain` renderiza `DedicatedScreen` em vez de `LauncherScreen` |

**Mantido acessível:** Configurações (conta, RAM, renderizador), gerenciador de contas, ajuda. O jogador não fica sem saída para configuração legítima.

### Tela dedicada

Ícone do pack (a **mesma arte 1024×1024 embutida no APK**, §8.1 — nada de buscar no CDN), nome do pack, aviso de versão modificada (§8), e um botão grande com estado:

```
Instalar  →  [progresso: % + velocidade, via TaskMenu]  →  Jogar
Atualizar →  [progresso]                                 →  Jogar
Offline   →  Tentar de novo
```

---

## 7. Semeamento

### 7.1 Servidor (`DedicatedSeeder`)

- Lê `servers.dat` com `AllServers.loadServers(...)`, adiciona a entrada `DEDICATED_SERVER_NAME` / `DEDICATED_SERVER_IP` **se ainda não existir**, grava com `AllServers.save(...)` (NBT cru — compatível com o 1.7.10).
- **Nunca remove** entradas existentes; **nunca reescreve** o arquivo em atualizações.
- Na primeira instalação o pack já traz `dbcsuper.com`; se o nome configurado for diferente, o seeder adiciona/renomeia a entrada.

### 7.2 Controles (R4)

- Por enquanto usa o **layout padrão** (`assets/default_layout.json`), que o `ControlManager` já extrai quando `control_layouts/` está vazio.
- A instalação do pack liga o layout à versão: `versionConfig.control = <arquivo>`.
- O `ControlManager` hoje só semeia **quando o diretório está vazio** — necessário acrescentar a variante *"semeia se o arquivo ainda não existir"* para não depender do estado da pasta.
- Quando o time fornecer um `.json` no formato atual (com `editorVersion`), ele vira `assets/dedicated/control_layout.json` e passa a ser a fonte do seed (mudança pontual).

> **Fora de escopo por ora:** converter `default-3.json` (formato Pojav/ZL1 com `mControlDataList`, `dynamicX/dynamicY` como expressão, `mDrawerDataList`, `mJoystickDataList`) para o formato ZL2. Os modelos de posição são incompatíveis (expressão com `preferred_scale`/`margin` × fração fixa 0–10000) e não há equivalente direto para gavetas. Seria um sub-projeto próprio.

### 7.3 `version.config`

Na etapa 7 da instalação: isolamento de versão **ativo**, RAM herdada das configurações, `control` apontando para o layout semeado, `skipGameIntegrityCheck = false`.

---

## 8. Identidade e licença

- `gradle.properties`: `launcher_name=SuperLauncher`, `launcher_app_name=Super Launcher`, `launcher_short_name=SL`.
- Aviso obrigatório **"Unofficial Modified Version by Hakkaiz"** visível na tela inicial (string em `res/values/strings.xml`; o projeto tem dezenas de locales — adicionar `values/pt-rBR` também).
- Avisos de copyright do projeto permanecem.
- APK gerado como `SuperLauncher-<version>.apk` (padrão existente `"$launcherName-$launcherVersionName.apk"`).

### 8.1 Ícone do APK

Arte fornecida pelo time: `C:\Users\Administrator\Downloads\21c2fe7752bc783c0327827520086fd2.png` (1024×1024, 32bppArgb). **Precisa ser copiada para dentro do repositório** antes do build — `Downloads/` não é um local estável.

**Análise da zona segura:** no ícone adaptável o canvas é 108×108dp e a área sempre visível é o círculo interno de 72dp (66,7% da largura). Mapeando a arte 1024px nesse canvas *full-bleed*, o círculo visível teria raio ≈ 341px — as pontas das orelhas (≈492px do centro) e o texto "SUPER" seriam cortados.

**Solução:** escalar a arte para ~70% do canvas, centrada, sobre fundo transparente; as pontas da arte (≈492px < raio 512px do círculo inscrito) ficam inteiras e os cantos escuros da arte caem fora da máscara, se misturando ao fundo.

| Recurso | O que gerar |
|---|---|
| `drawable/ic_launcher_foreground.xml` | Substituir o vetor por bitmap: arte escalada a ~70% em canvas transparente |
| `values/ic_launcher_background.xml` | Cor sólida extraída do fundo da arte (tom escuro/roxo) |
| `drawable/ic_launcher_monochrome.xml` | Silhueta alpha simplificada (Android 13+); se não houver, manter arte sem detalhes |
| `mipmap-{mdpi,hdpi,xhdpi,xxhdpi,xxxhdpi}/ic_launcher.webp` e `ic_launcher_round.webp` | 48/72/96/144/192 px, arte em square com cantos arredondados |
| `drawable/splash_launcher.xml` | Mesma arte na tela de abertura |
| Ícone de notificação | Versão simplificada — `R.mipmap.ic_launcher` é usado como small icon em `GameService`, `JvmService`, `TaskKeepAliveService` e `TerracottaVPNService`; arte detalhada vira mancha monocromática |

---

## 9. Testes

Uníticos (`src/test/java/`, seguindo o padrão de `.../multiplayer/TestServers.kt`):

1. **`TechnicApi`** com fixtures JSON: modo zip, `solder: null`, resposta 401, JSON malformado.
2. **Resolvedor de versão**: `bin/version.json` → `inheritsFrom=1.7.10` + forge `10.13.4.1558`; fallback para `bin/modpack.jar!/version.json`; fallback para o campo `minecraft` da API.
3. **Manifesto**: update remove mods órfãos; `saves/`, `options.txt`, `servers.dat` nunca entram na lista de remoção.
4. **Seeder de `servers.dat`**: round-trip NBT cru legível por `AllServers`; entrada duplicada não é criada; entradas do jogador preservadas.
5. **Estado**: transições `CHECKING → NOT_INSTALLED → INSTALLING → READY` e `→ FAILED → INSTALLING`.

Manuais (em aparelho):

1. Primeira abertura completa: instalação → jogar → servidor aparece na lista em jogo.
2. Segunda abertura: direto em `READY`, sem download.
3. Atualização do pack (publicar build novo na Platform) → `NEEDS_UPDATE` → overlay → saves preservados.
4. Offline na primeira abertura → mensagem e retry.
5. Lançamento com verificação de integridade ligada → nenhum arquivo apagado.
6. Nenhuma rota de download acessível (botão, deep link, erro de launch).

---

## 10. Riscos conhecidos

| Risco | Mitigação |
|---|---|
| Número de build da API do Technic fica obsoleto (401) | Constante `TECHNIC_API_BUILD` + erro explícito; atualizar junto da versão do launcher |
| Dropbox pode limitar/bloquear download em massa | User-Agent de navegador, redirect automático; se instável, trocar a `url` do pack na Platform por um host próprio (mudança só na Platform, zero código) |
| APK com `launcher_name` novo muda namespace MMKV | Esperado e desejado (settings isolados); testar primeira execução do zero |
| Zip de 90 MB em aparelhos com pouco espaço | Checar espaço livre antes de extrair; staging em `cache/` (limpável) |
| `bin/natives/*.dll` inúteis no Android | `bin/` inteiro excluído do overlay |
| Jogador apaga arquivos do `versions/<slug>/` | `VERIFY_AND_REPAIR` do launch reconstitui a base; o overlay é refeito se o manifesto sumir |

---

## 11. Itens em aberto (não bloqueiam a implementação)

1. **Nome do servidor** e IP exato para `DEDICATED_SERVER_NAME` / `DEDICATED_SERVER_IP` (o pack já traz `dbcsuper.com` como "Minecraft Server").
2. **Layout de controles** no formato atual — quando chegar, entra como `assets/dedicated/control_layout.json`.

---

## 12. Fases de implementação

O escopo é grande demais para um plano único e linear. O `writing-plans` deve dividi-lo em fases pequenas e verificáveis nesta ordem (cada uma deixa o app compilando e o comportamento anterior intacto):

| Fase | Conteúdo | Critério de pronto |
|---|---|---|
| 1 | **Identidade**: `gradle.properties`, BuildKeys novos, aviso obrigatório, ícone (§8) | APK instala com nome/aviso/ícone novos; `./gradlew :ZalithLauncher:assembleDebug` verde |
| 2 | **Cliente Technic + resolvedor de versão** (§5.2 etapas 1 e 4) | Testes unitários das fixtures passando |
| 3 | **Instalador** (§5.2 etapas 2–3, 5–8) + manifesto (§5.3) | Primeira instalação em aparelho chega a `READY` e o jogo abre |
| 4 | **Semeamento** (§7): servidor, controles, `version.config` | Servidor aparece na lista em jogo; layout selecionado |
| 5 | **Tela dedicada + travamento** (§5.1, §6) | Nenhuma rota de download acessível (teste manual 6) |
| 6 | **Atualização + estados/erros** (§5.3, §5.5) | Testes manuais 2–5 passando |

A ordem importa: a fase 3 depende da 2; a 5 depende da 3 (senão a tela dedicada aponta para um fluxo inexistente). A 1 é independente e pode sair primeiro.
