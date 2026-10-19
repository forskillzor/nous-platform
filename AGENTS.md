# AGENTS.md — nous-platform

Operating guide for AI agents working in this repository. Keep it up to date:
when a workflow discovery is made (a command pattern, a pitfall, a project
invariant), add it here.

## What this project is

Desktop Kotlin Multiplatform (Compose) trading terminal. The workspace is a
tile grid with panels: chart (candlestick + footprint), DOM depth ladder,
time & sales, docked trading panel. Market data and trading come from
providers (Binance, Bybit, MEXC). A shared paper-trading engine mirrors real trading
so the whole UI can be exercised without exchange credentials. Per-panel
state (trading flags, paper mode, order settings) is persisted in a local
SQLite state store.

## Module map

| Module | Responsibility |
| --- | --- |
| `composeApp` | Desktop entry point, Koin DI (`di/AppModule.kt`), workspace UI, paper settings overlay. This is the executable app (`:composeApp:run`). |
| `platform-core` | Workspace/tile/window system, theme (`TradingTerminalTheme`), shared UI components (`TerminalSwitch`, `TerminalDropdown`, `TerminalPanelHeader`), number formatting (`SymbolFormatter`, `PlainDecimal`), `StateStore` interface. |
| `core/core-dependencies` | Shared Compose and dependency helpers. |
| `public-api/api-market` | Provider contracts: `Provider`, `ProviderConfig`, `ProviderRegistry`, adapter interfaces (`TradingAdapter`, `SymbolInfoAdapter`, `DomAdapter`, `ChartAdapter`, `TradesAdapter`, `BookTickerAdapter`), models (`SymbolInfo`, trading models), the paper engine (`PaperTrading`, `PaperTradingAdapter`, `effectiveTrading`), trading commands (`MarketOrderCommand`, `LimitOrderCommand`, `BestBidAskCommand`), shared `TradingAdapter.replaceOrder` extension in `adapters/OrderOperations.kt`. |
| `public-api/api-trading`, `public-api/api-ui` | Additional API surfaces. |
| `providers/binance-provider` | Binance USDT-M. Market data is real; `trading` is a stub that returns fake success with `TEST-<timestamp>` ids and tracks nothing — never use it to validate real order behaviour. |
| `providers/bybit-provider` | Bybit V5 USDT linear perpetual market data and trading: public candles, order book, trades, best bid/ask, symbol info, live liquidations, signed private REST, and private account WebSocket. Credentials are `ProviderConfig.apiKey/secretKey` or `BYBIT_API_KEY` / `BYBIT_SECRET_KEY`; historical liquidations are not exposed by the documented public API. Margin mode is account-wide on UTA and must match the order's requested mode; the adapter never switches it implicitly. |
| `providers/mexc-provider` | MEXC USDT-M Futures Contract API v1: signed REST client, rate gate, WS hub (public + personal), adapters, contract-size aware trading adapter. |
| `features/chart` | Chart panel: rendering (candles/footprint), `ChartViewModel`, panel-scoped persistence (`ChartStatePersistor`), chart trading (order/position badges, TP/SL, drag = cancel+replace). JVM UI: `ChartWindow`, `ChartTradingPanel`. |
| `features/dom` | DOM depth ladder: `DomViewModel` (order book, click-to-place, order chips with resize/drag/cancel, position plates, margin chip), `DomWindow` (jvm), `content/LevelRow` + `DomSection`, `footer/OrderPlacementPanel`. |
| `features/trading` | Docked trading panel (tabs Positions/Orders/Balance/History), `TradingViewModel`, `PaperSettingsController`, `PaperPersistenceService` (persists the shared paper account). |
| `features/trades` | Time & sales panel. |
| `features/localstorage` | SQLite-backed `StateStore` — key-value per-panel persistence. |
| `features/settings` | App settings surfaces. |
| `build-logic` | Gradle convention plugins: `conventions.kmp-application`, `conventions.kmp-feature`, `conventions.kmp-library` (classes `Kmp*Convention.kt`). New feature modules apply the feature convention. |

Gotcha: some module source trees use dot-separated folder names for packages,
e.g. `public-api/api-market/src/commonMain/kotlin/com.aandios.nous.api.market/...`.
Mind this when writing paths.

## Trading architecture (invariants — do not break)

### Provider and adapter selection
- `Provider.effectiveTrading(paper: Boolean)` returns the shared paper adapter
  when `paper == true`, otherwise the provider's real `TradingAdapter`.
- Paper mode is **per panel**, never global: chart, DOM and the docked trading
  panel each keep their own flag (the docked panel uses `PaperTrading.STORE_KEY`).
- Real adapters return `null` from `subscribeTo*` when credentials are missing;
  the UI treats `null` as “no live updates”.

### Shared market catalog (explorer + Market Watch)
- `MarketOverviewStore` (platform-core, `core/market`) is the **single source**
  of per-exchange catalog data: SymbolInfo specs + 24h stats
  (`MarketOverviewAdapter` via `bookTicker`), merged per provider; one failed
  source never hides the other, failures land in `state.error`. It is
  preloaded at app start (Koin `createdAtStart`, `start()` is idempotent) and
  refreshes on a 30s REST loop on `Dispatchers.IO`; consumers never fetch
  these lists themselves — do not reintroduce duplicate loads.
- Consumers: the chart explorer (`ChartViewModel.explorerRows` maps
  `MarketOverviewRow.toSymbolExplorerRow()` — `.P` suffix, uppercased exchange
  label) and the Market Watch panel (`SymbolsPanel` maps the same rows the
  same way; both render the shared `SymbolExplorerContent`).
- WS live tickers (Binance `!ticker@arr`, MEXC `sub.tickers`; Bybit has no
  all-market ticker stream) are a possible follow-up — if added, keep them
  inside the store so consumers stay unchanged; Bybit stays REST regardless.

### Per-panel flags and persistence
- Chart: `ChartStatePersistor(stateStore, tradingPrefix)`. `ChartWindow` calls
  `ChartViewModel.attachPanel(workspaceId, panelId)`, which derives the prefix
  `chart_{workspace}_{panel}_...`. Keys cover trading enabled, confirm, qty,
  order type, reduce-only, leverage, margin mode, show-orders, show-positions,
  panel collapsed, and paper mode (`PaperTrading.STORE_KEY` in the same prefix).
  Symbol/timeframe/provider state (`chart_symbol`, `chart_provider_id`, ...) is
  saved globally (no prefix); in the workspace the symbol/timeframe come from
  `PanelConfig`, so `ChartWindow` only restores the provider via
  `ChartViewModel.restoreProvider()`.
- DOM: `dom_paper_{panelId}`, `dom_trading_{panelId}`, `dom_provider_{panelId}`,
  `dom_contract_{panelId}` (USDT_M / COIN_M) and the order panel settings
  persisted per panel: `dom_confirm_`, `dom_reduce_`, `dom_order_type_`,
  `dom_leverage_`, `dom_margin_`, `dom_qty_`, `dom_collapsed_` (+panelId).
- Docked trading panel: `paper_enabled` (`PaperTrading.STORE_KEY`),
  `trading_provider`, `trading_contract_type` (global keys). The window top bar
  Paper switch writes the same `paper_enabled` flag through the shared
  `PaperModeState` singleton (`features/trading`), so header and docked panel
  stay in sync; `TradingViewModel` observes it for external changes. The header
  has a Manage connections button (signal glyph tinted by the worst status)
  opening `ConnectionsMenu` (`TradingConnectionsMenu.kt`): rows REST API /
  Account stream with `TradingViewModel.connections` statuses
  CONNECTED / CONNECTING / ERROR — CONNECTING during restart, REST flips on
  core-call failures in `refreshAll`, stream on `subscribeTo*` availability
  and collector failures; paper mode reports local CONNECTED ("Paper engine").
  ERROR rows open `TerminalContextPopup` with the error detail.
- Trades panel: `trades_provider` (global key).
- Sidebar (tool panel) width: `tool_panel_width` (plain float dp, restore clamped
  to `workspaceToolPanelMin/MaxWidth`, save debounced 400 ms) via
  `ToolPanelPrefsRepository` (platform-core, `core/ui/panel`).
- Market Watch panel: `market_watch_prefs` (JSON `MarketWatchPrefs`: exchangeId
  (null = all), listId, sort name, descending, perpetualsOnly, quoteAsset,
  scrollIndex/scrollOffset) — same repository; legacy payloads ("ALL",
  "SYMBOL" defaults) decode via field defaults, scroll restores after catalog
  rows load, saves debounced 500 ms. The panel list is the shared
  `SymbolExplorerContent` (platform-core): tabs ALL + watchlists, ⌘K search,
  PERPETUALS/quote/exchange chips, funnel sort, `.P` display symbols, per-row
  star (active list or Favourites); the panel adds only a row context menu
  (Apply to current workspace, OPEN <sym> WITH templates, Add to <list name>)
  and a ⚙ menu (New/Rename/Delete list, Close panel). Catalog failures are
  never printed in the body: a red exclamation badge in the panel title opens
  `TerminalContextPopup` with `MarketOverviewStore.state.error`.
- Chart: provider/contract-type state is global (`chart_provider_id`,
  `chart_contract_type`).
- Theme selection is global (`terminal_theme`) and is managed by
  `ThemeSettingsController`; Nous is the default, persisted names must align
  with `TerminalThemes.available`, and load/save errors are surfaced in the theme UI.
- Theme color invariants (guarded by `TerminalThemeTokensTest`): `accent` is
  always the theme's `colorScheme.primary` and `switchTrack` is `primary` at
  65% alpha (both derived in `terminalTokens()`, never overridden per theme);
  `selection` is an opaque neutral elevation per theme (Nous `#1A2940`, Dark
  `#262626`, Night `#1B1B1B`, Light `#DCE4EF`); `watermark` is the faint
  panel-ticker tint (`onBackground` at 7%, Light overrides to `#1B2430` at
  14%); `borderWidth` is 0.5dp except Light (1dp). Light is the light scheme:
  blue accent `#2563EB`, white chart/surfaces on a cool gray workspace;
  window frame background and DWM title bar follow the active theme
  (`applyWindowBackground` / `applyWindowsDarkTitleBar(dark=...)`). Panels
  take colors from
  `LocalTerminalDesignTokens` / `MaterialTheme.colorScheme.primary`; chart
  renderers take them from `ChartConfig` (`accentColor`, `currentPriceColor`,
  ...). Do not reintroduce literal blue `0xFF5B9BD5` / translucent yellow
  selection values.

### Safety invariants (learned the hard way — keep them)
- Trading is **OFF by default** on both chart and DOM panels.
- Any paper↔live switch or provider change **forces Trading OFF** — a stale
  persisted “trading = on” must never re-arm live orders.
- Persisted trading is restored **only together with paper mode**; live trading
  never auto-enables after a restart.
- `showOrders` / `showPositions` persist independently, exactly as chosen. Only
  an explicit chart “Trading ON” auto-enables both.
- Trading OFF blocks placement, but existing orders/positions stay visible
  (view-only mode).

### Units: base asset vs MEXC contracts
- Everywhere in the platform (UI inputs, commands, positions, PnL math, paper
  engine) quantity is in the **base asset** (e.g. SOL).
- MEXC exchange volume is in **contracts**. `SymbolInfo.contractSize` carries
  the conversion factor; MEXC mapping sets `stepSize = volUnit * contractSize`,
  `minQty = minVol * contractSize` (base units).
- `MexcTradingAdapter` converts at the boundary: base qty → contracts on place
  (half-up rounding to the contract step, sub-minimum rejected), and scales
  open orders, positions, trade history and personal WS pushes back to base.
- **Book windows must match `SymbolInfo` units**: `BookWindowLevels.quantity`
  must be in the same unit as `SymbolInfo.stepSize`/`minQty` (base asset for
  linear, contracts for inverse) because `DomViewModel` divides window volume
  by `stepSize`. Providers with contract-based feeds convert at the adapter
  boundary (`MexcDomAdapter` scales by `domQuantityScale`: `contractSize` for
  linear, 1.0 for inverse/unknown) — otherwise volumes render off by the
  contract-size factor (seen as 10x on SOL_USDT).
- Client-side PnL: `(mark - avgPrice) * quantity * dir`; percent =
  `(mark - avgPrice) / avgPrice * 100 * dir`. Never count “ticks” in UI output —
  show price change in base units.

### Order flow
- Chart: `ChartViewModel.buildTradingRequest` builds an `OrderRequest`
  (leverage + marginMode + optional TP/SL), submitted via the active adapter.
- Chart trading panel (`ChartTradingPanel`): order type is one dropdown
  (LIMIT/POST_ONLY/IOC/FOK/MARKET); the Price field (hidden for MARKET) feeds
  Buy Limit / Sell Limit — empty falls back to best bid/ask (previous
  behaviour); Qty/Price inputs show base/quote currency suffixes; primary
  CTAs are `BUY / LONG` / `SELL / SHORT` (no hotkey hints); RO + Confirm
  chips stay on one row.
- DOM: `OrderIntent` → `MarketOrderCommand` / `LimitOrderCommand` /
  `BestBidAskCommand`, which build and execute `OrderRequest`s.
- Both panels share `TradingAdapter.replaceOrder(order, price, quantity,
  leverage, marginMode)` (cancel + place) for badge drag and inline qty edits.
- Paper engine notices (`TradingAdapter.notices()`) and real-adapter
  `OrderResponse.message` surface as panel snackbars (chart and DOM).

### MEXC provider specifics
- Symbols: platform `BTCUSDT` ↔ native `BTC_USDT`
  (`toMexcSymbol`/`fromMexcSymbol` in `MexcEndpoints.kt`).
- Order sides: 1 open long, 2 close short, 3 open short, 4 close long
  (reduce-only BUY → 2, SELL → 4). Types: 1 LIMIT, 2 POST_ONLY, 3 IOC,
  4 FOK, 5 MARKET. `openType`: 1 isolated, 2 cross (same values as the UI
  margin mode).
- REST signing: `HMAC_SHA256(secret, apiKey + reqTime + body)` (GET: sorted,
  URL-encoded params) with headers `ApiKey`, `Request-Time`, `Signature`.
  WS login: `HMAC(secret, apiKey + reqTime)`.
- All REST calls go through `MexcRestGate` (per-provider weight budget,
  in-flight dedupe, 429/418 cooldown). One gate instance is shared by all
  adapters of the provider.
- Personal WS channels: `push.personal.order`, `push.personal.position`,
  `push.personal.asset`; the hub re-logs in on reconnect.
- DOM depth: incremental `sub.depth` with `compress:false` (~hundreds of
  diffs/s) is maintained by `MexcDepthBook` (absolute quantities, 0 removes,
  version must be exactly prev+1, gap → REST `contract/depth` resync); the
  adapter emits full top-N `BookWindowLevels` windows to the UI at most every
  100ms (like Binance `depth20@100ms`), so the DOM is UI-agnostic of the
  increment. `sub.depth.full` (~3 pushes/s) is not used anymore.
- Fees: `getFeeRates` via `account/tiered_fee_rate/v2`; paper engine consumes
  these rates through `TradingAdapter.getFeeRates`.
- MEXC has no public liquidation stream — `Provider.liquidation` is `null`.
- Keys: `ProviderConfig.apiKey/secretKey` or env `MEXC_API_KEY` /
  `MEXC_SECRET_KEY` (JVM `System.getenv`). There is no key-entry UI yet.

### Number formatting
- Prices: always `SymbolFormatter` (`formatPrice`, `roundPrice`).
- Exchange payloads and qty inputs must be plain decimal (never `1.0E-4`):
  use `plainDecimalString` (platform-core) — `SymbolFormatter` is built on it.
- Volume stats in symbol lists: `SymbolFormatter.formatCompactVolume`
  (K/M/B, three significant digits: `3.84B`, `486M`, `94.0M`; suffix promoted
  on the rounding boundary, `999.6e6 → 1.00B`).
- Color/face constants used across trading UI: buy `0xFF26A69A`,
  sell `0xFFEF5350`, ok `0xFF00C853`, warn `0xFFE0A95B`,
  label `0xFF8A97A5`, dark PnL plate `0xFF1B222B`; accent is not a fixed
  color — it is the active theme's `colorScheme.primary`.

## Build / test commands (Windows, PowerShell)

Repo root: `C:\Users\skillzor\IdeaProjects\nous\nous-platform`

- Compile app: `:composeApp:compileKotlinJvm`
- Module compile: `:features:dom:compileKotlinJvm`, `:features:chart:compileKotlinJvm`, etc.
- Standard test set:
  `:platform-core:jvmTest :public-api:api-market:jvmTest :features:chart:jvmTest :features:trading:jvmTest :features:dom:jvmTest`
- Provider tests: `:providers:mexc-provider:jvmTest`, `:providers:binance-provider:jvmTest`, `:providers:bybit-provider:jvmTest`

### IMPORTANT: never run `gradlew` synchronously in the shell tool

A direct/pipe invocation like `.\gradlew.bat ... 2>&1 | Select-String ...` hangs
the shell until the daemon is killed (the daemon starts inside the tool's job
object / holds output handles, so EOF never arrives). **Always run Gradle
detached via WMI and poll the log file**, using a unique log per run:

```powershell
$repo = "C:\Users\skillzor\IdeaProjects\nous\nous-platform"
$log  = "C:\Temp\opencode\gw-<task>.log"   # unique per run
Remove-Item $log -ErrorAction SilentlyContinue
$cmd = "cmd.exe /c cd /d $repo && gradlew.bat <TASKS> --console=plain > $log 2>&1"
([wmiclass]'Win32_Process').Create($cmd) | Out-Null
"started"
```

Poll in a separate call (keeps within tool timeouts):

```powershell
Start-Sleep -Seconds 80
$log = "C:\Temp\opencode\gw-<task>.log"
try { $txt = Get-Content $log -Raw -ErrorAction Stop } catch { "no log yet"; exit }
if ($txt -match "BUILD SUCCESSFUL|BUILD FAILED") {
    $txt -split "`n" | Select-String "BUILD |FAILED|^e: " | ForEach-Object { $_.Line }
} else { "still running..."; $txt.Length }
```

- If the poll hits the tool timeout, the build keeps running — re-read the log
  in the next call. `Get-Content` handles the shared lock.
- Compile errors: filter `^e: ` lines.
- Test results: read `build/test-results/jvmTest/TEST-*.xml` and check
  `<testsuite tests=... failures=... errors=...>` to confirm the suite really ran.
- Recovery for a corrupted/locked jar:
  `Failed to transform <module>-jvm-0.1.0.jar ... ClasspathEntrySnapshotTransform`
  → delete `features/<module>/build/libs/<module>-jvm-0.1.0.jar` and rerun.

### Run the app the same way (never blocks)

```powershell
$cmd = "cmd.exe /c cd /d $repo && gradlew.bat :composeApp:run --console=plain > C:\Temp\opencode\app-run.log 2>&1"
([wmiclass]'Win32_Process').Create($cmd) | Out-Null
# wait for the window process with MainWindowTitle matching "Nous Platform"
```

## Verification loop

1. Compile every touched module (`:<module>:compileKotlinJvm`).
2. Run the targeted `jvmTest` suites for touched modules.
3. Finish with `:composeApp:compileKotlinJvm` — it depends on the features and
   catches cross-module breaks.
4. Report the exact task list and result to the user (BUILD SUCCESSFUL / errors).

Do not use UI automation (WMI clicking, synthetic input). The user runs the app
from the IDE and tests visually; agents verify via compilation and tests.

## Git workflow

- Check `git status --porcelain` before staging; stage only the files of the
  current logical step.
- The repo owner edits files between agent turns — never commit their
  uncommitted changes silently. Commit them separately only when asked.
- Existing workspace layouts are user data and must not be migrated implicitly.
  The built-in Nous template is the target Chart/Trading + DOM/Trades layout for
  new workspaces; keep existing saved layouts unchanged.
- Commit style: one long single-line English message per logical step,
  subject plus “what and why”. One commit per coherent change; split unrelated
  changes into separate commits.
- Do not push, amend, force-push, or create empty commits unless explicitly
  asked.

## Editing files on Windows

- Prefer the `read` + `edit` tools; files are LF, but some test files are CRLF.
- PowerShell `Get-Content -Raw` + `.Replace("...`n...")` only works on LF files.
  For multi-line edits use the edit tool (it handles line endings); don't guess.
- Avoid PowerShell patterns that break the parser: single-quoted strings with
  backtick escapes, nested `$((...))`, and unquoted paths with spaces.
- `rg` is not installed — use the grep/glob tools, not shell ripgrep.

## Testing guide

### Where tests live
- `platform-core/src/jvmTest` — workspace/format utilities, `MarketOverviewStore`,
  explorer (`SymbolExplorerDropdownTest`), menu rows (`TerminalMenuItemTest`).
- `public-api/api-market/src/jvmTest` — paper engine, adapters, commands.
- `features/dom/src/commonTest` — DOM view model (fakes), including
  explorer selection (provider switch + pending symbol, contract-type sync).
- `features/chart/src/jvmTest` — chart state/persistence/rendering +
  explorer selection (`ChartExplorerRowsTest`); no commonTest.
- `providers/mexc-provider/src/jvmTest`, `providers/binance-provider/src/jvmTest`.
- `features/trading`, `features/trades`, `features/settings` have no tests
  (`:features:trading:jvmTest` exists but is empty).

### Frameworks and patterns
- `kotlin.test` (JUnit4 on JVM) + `kotlinx-coroutines-test`:
  `runTest`, `TestScope`, `StandardTestDispatcher`, `advanceUntilIdle`,
  `runCurrent`.
- Providers: Ktor `MockEngine` with request capture (path/body/headers).
  `MexcTradingAdapter` fetches contract specs; intercept
  `contract/detail` responses in the mock (default contract size 1.0) and use
  a custom `detailJson` when testing contract-size conversions.
- DOM: fakes (`FakeDomProvider`, `FakeDomAdapter`, `FakeBookTickerAdapter`,
  `FakeSymbolInfoAdapter`), `DomViewModel(providerRegistry, coroutineDispatcher
  = testDispatcher)`. Paper tests reset the shared engine
  (`PaperTrading.adapter.reset()`) and feed `setMarkPrice`.
- Paper engine: order types, reduce-only, TP/SL, balance ops, snapshot/restore,
  `effectiveTrading` — see `PaperTradingAdapterTest`.

### Rules
- Prefer fakes over mocks; assert observable view-model state or engine effects.
- Add a regression test for every behavioural fix (guards, unit conversion,
  mapping, persistence).
- When a default changes (e.g. trading now OFF), update every dependent test and
  add an explicit test for the new default — order-placing tests enable trading
  explicitly.
- Numeric assertions should be exact where conversion math is involved
  (e.g. 0.3 SOL @ contractSize 0.1 → `"vol":"3"`).

### Known test noise (pre-existing, unrelated)
- `FootprintLiveSmokeTest` calls live APIs and can fail/timeout on the network.
- `MexcProviderDiagnostics` prints only (no asserts) — CI-safe.
- `:platform-core:compileKotlinWasmJs` fails (Kotlin/Wasm plugin vs compiler
  version mismatch) — unrelated to JVM work.

## UI conventions

- Shared visual values belong in `TerminalDesignTokens` and
  `TradingTerminalTheme`; use `TerminalFontFamily` for explicit text styles so
  the active terminal font is respected. Desktop font resources live in
  `platform-core/src/jvmMain/resources/fonts` and must stay on that module's
  runtime classpath; do not replace explicit themed fonts with the platform's
  generic `FontFamily.Monospace`.
- Chart panel surfaces: DrawingToolPanel style — border `ChartColors.gridLine`,
  panel background, 8dp rounding.
- App chart surfaces derive renderer colors through `ChartConfig.withTheme`
  from `LocalTerminalDesignTokens`; `ChartColors` remains a static compatibility
  facade for callers that are not hosted inside the app theme.
- Prices: always via `SymbolFormatter` (`formatPrice`, `roundPrice`).
- Paper vs real data source: `Provider.effectiveTrading(paper)`; per-panel flag.
- `TerminalSwitch`: OFF = grey; ON = grey thumb on a terminal-green track
  (`0xFF00C853`, 65% alpha). In compact headers disable the M3 touch target
  (`LocalMinimumInteractiveComponentSize = Dp.Unspecified`) and use `scale(0.7f)`.
- Internal panel headers use `TerminalPanelHeader` for the shared surface,
  spacing and bottom divider; keep panel-specific controls and actions inside it.
- Panel top titles are `PanelHeader` (platform-core): drag grip, per-panel
  controls (`LayoutRenderer.panelTitleContent` slot), then ⚙ (menu via
  `panelSettingsContent`), + (dark 20dp circle, `surfaceVariant` fill +
  `outlineVariant` ring + accent plus drawn in Canvas; split menu; SPLIT RIGHT →
  `onSplitH` → Row, SPLIT DOWN → `onSplitV` → Column) and a large × close. The
  chart title hosts `ChartTitleToolbar`: mode rounded box dropdown
  (`TerminalBoxStyle.ACCENT` — `surfaceVariant` fill, accent text), symbol
  explorer box (see below; its closed trigger shows the exchange label in
  small accent text before the symbol — provider switching happens by
  picking a row of another exchange, there is no separate exchange
  dropdown in the chart title), solid-accent timeframe chip
  (`TerminalBoxStyle.SELECTED`, dark
  text, no chevron), Trading on/off button and AI mock; chart options (Type,
  aggregation, Paper, Show orders/positions) live in the ⚙ menu
  (`ChartSettingsMenu`). DOM/Trades/Trading titles are plain
  `PanelTitleLabel`s (DOM shows a `STREAM` dot; the Trades glyph is accent);
  their ⚙ menu closes the panel. In workspace tiles `ChartWindow` hides its own
  toolbar (standalone previews keep `TerminalPanelHeader`-wrapped version).
- Chart title symbol control is `SymbolExplorerDropdown` (platform-core):
  closed trigger shows the current exchange in `primary` accent at
  `microSize` before the unchanged symbol text;
  its popup body is the shared `SymbolExplorerContent` (hoisted state params,
  optional `rowMenu` per-row context menu hook) — Market Watch renders the
  same composable inline, so keep new explorer features inside the content;
  tabs ALL + watchlist names uppercased (stars write into an auto-created
  `Favourites` list), search field with hit highlight and ⌘K / Ctrl+K focus,
  chips PERPETUALS / quote / exchange, funnel sort (VOLUME / 24H % / PRICE /
  SYMBOL, active direction ↓/↑), 5-row scroll list (LazyColumn — keep it lazy,
  the catalog holds ~2k rows, and pin it to fixed width `menuWidth` × height
  `5 × 46dp`: the hosting M3 `DropdownMenu` measures content with
  `IntrinsicSize.Max`, and a SubcomposeLayout cannot answer intrinsic queries
  unless it has a specified size — never `fillMaxWidth()` there; star,
  `SYMBOL.P` for
  perpetuals, `EXCHANGE · PERP · VOL $3.84B`, last price, 24h %), footer
  `N CONTRACTS · SORTED BY <sort>` + MANAGE WATCHLIST, which opens the Market
  Watch tool panel (`ToolPanelState` is hoisted in `TerminalWindowContent`;
  standalone `ChartWindow` passes null and hides the action). Rows come from
  the shared `MarketOverviewStore` catalog (see Trading architecture), so the
  list is preloaded at app start and opens instantly.
  A pick from another exchange switches the chart provider (Trading OFF by
  invariant) only after that provider's symbols load (`pendingExplorerSymbol`
  in `applyPendingExplorerSymbol`); same-exchange picks sync `contractType`
  from the row's `SymbolInfo` without a double chart load.
  Search uses the single shared matcher (`compileSearchRegex`/`matchSymbol` in
  `SymbolExplorerDropdown.kt`):
  symbol first (highlight range; score exact → prefix → substring → empty),
  then meta fields (exchange/PERP/quote, no highlight). With a query typed and
  the SYMBOL sort active the explorer ranks by relevance (↓/↑ inverts, like
  Market Watch); other sort chips keep their field order.
  DOM and Trades headers use the same explorer (`SymbolExplorerDropdown` in the
  first header row next to the contract-type button; their old `Ex`
  inline-select and `DomSymbolDropdown` / `TradesSymbolDropdown` are gone):
  the VMs expose `explorerRows` from the
  shared `MarketOverviewStore`, `selectExplorerRow(row)` switches the provider
  (DOM: Trading OFF; Trades: `selectProvider`) and applies the symbol via
  `pendingExplorerSymbol` after `loadSymbols()`, syncing `contractType` from
  the row's `SymbolInfo`. Header layout: none of the headers has a separate
  contract-type button — the type is implied by the symbol (row picks sync
  `contractType`); DOM row 1 = symbol explorer + collapse, row 2 = depth + agg
  (agg wraps to a third row on narrow panels); Trades is a single row =
  symbol explorer + size filter.
  Trades size filter is one box button: funnel glyph (`TerminalFunnelGlyph`,
  shared with the explorer sort funnel) + current mode in `primary` text
  ("ALL" / "≥ …").
- Left sidebar is `WorkspaceNavigator` (platform-core, replaced `ProjectTree`):
  workspace tree grouped by `WorkspaceConfig.group` folders ("Unsorted" =
  empty group, pinned first, `folderOrder` then alphabetical), folder
  create/rename/delete and workspace rename/make-template/delete via theme
  island menus (`NavigatorMenuItem`), inline rename, search via the shared
  matcher (folder-name match keeps all children; invalid regex shows an error
  and hides the tree while searching), drag & drop for workspaces
  (into/between folders, auto-expands collapsed target) and folders, Quick
  Access templates section; pure model + DnD resolution live in
  `WorkspaceNavigatorModel.kt` (`buildNavigatorTree`,
  `visibleNavigatorItems`, `resolveNavigatorDropTarget`,
  `applyWorkspaceDrop`/`applyFolderDrop` — unit-tested). Workspace rows use
  the Market Watch row style: 46dp, full-height accent bar, full-height
  mosaic column (16dp glyph, like the explorer star), two lines (name +
  `navigatorWorkspaceSubline`
  "Exchange · Symbol", always visible), thin bottom divider; folder rows stay
  compact 26dp. Folder order and
  collapsed set persist via `NavigatorStateRepository` (`navigator_state` in
  StateStore); structural moves persist user workspaces through
  `WorkspaceRepository.update(..., touchUpdatedAt = false)` so "recent"
  ordering is not polluted. Sidebar icon (mosaic) is
  `composeResources/drawable/workspaces.svg`; other navigator glyphs are
  Canvas-drawn in platform-core (no composeResources there).
- The shared rounded dropdown shell is `TerminalBoxDropdown` (platform-core,
  compact MW-LIST style: 11sp mono text, smallCard radius, 2dp/6dp padding,
  12dp chevron; styles `NORMAL` (45% `panelHeaderSurface` fill, white text),
  `ACCENT` (`surfaceVariant` fill, accent text — provider/mode) and `SELECTED`
  (solid accent fill, dark text, no chevron — timeframe)); the Market Watch
  LIST dropdown renders through it —
  keep new title dropdowns on it instead of hand-rolled boxes. Dropdown menu
  popups (the shell, explorer chip/funnel submenus, panel ⚙/split menus,
  DOM/Trades selects, size filter, depth, navigator menus) render through the
  shared `TerminalDropdownMenu` wrapper — theme islands like workspace panels:
  `workspacePanel` radius, `panelSurface` fill, `panelBorder` border at
  `borderWidth`, zero tonal/shadow elevation. Never call M3 `DropdownMenu`
  directly (bidasker-web is a separate web prototype, exempt).
- All menu/dropdown options render through the shared `TerminalMenuItem`
  (platform-core): 26dp row (like workspaces list rows), 2dp accent left bar +
  `selection` background on the selected option (explorer style),
  `panelHeaderSurface` hover, `indication = null`; plain mono text
  `compactSize`/`controlLineHeight`. It is a plain Row, not M3
  `DropdownMenuItem`, because M3 enforces a 48dp minimum item height. Used by
  `TerminalBoxDropdown`/`TerminalDropdown`/`TerminalInlineSelect`, explorer
  chips/funnel, chart settings rows, panel/⚙ menus and trades size filter;
  guarded by `TerminalMenuItemTest`. Do not reintroduce `DropdownMenuItem`
  for option rows (bidasker-web is a separate web prototype, exempt).
- Texts inside compact controls must set `lineHeight` explicitly
  (`controlLineHeight` for box triggers): `Text` merges with the inherited
  Material `LocalTextStyle`, so a small `fontSize` alone still lays out the
  inherited (bodyLarge, 24sp) line height and the control grows (explorer
  exchange label 9sp without lineHeight made the trigger 28px instead of
  20px, taller than the neighbouring Candles box; guarded by
  `TerminalBoxDropdownTriggerHeightTest`).
- Workspace panel island spacing is controlled by
  `TerminalDesignTokens.dimensions.workspacePanelGap`; `LayoutRenderer` must
  reserve it around leaves and include it in fixed-panel width calculations so
  DOM/Trades retain their configured content widths.
- Split, sidebar, and bottom-panel resize affordances use the shared
  `TerminalGripDots` component. Keep its vertical/horizontal dot orientation
  and token-sized hit region in sync with `splitHandleSize` layout accounting.
  Workspace split handles and the bottom-panel handle keep a `splitHandleSize`
  (2dp) layout slot but their hover/drag hit box spans the whole visual gap
  (`workspacePanelGap + splitHandleSize`) via a centered
  `requiredWidth`/`requiredHeight` overflow child (dots stay centered) —
  never shrink the hit region back to the 2dp strip.
- DOM ladder and order-entry colors should come from
  `LocalTerminalDesignTokens.current`; preserve strong white-on-side-color and
  dark-on-quantity-input contrast.
- Market Watch and other migrated panel content should use active semantic
  colors and centralized typography, spacing, shape, and dimension tokens.
- Compact selects use `TerminalDropdown`; panel headers (DOM/Trades) use
  `TerminalInlineSelect` — a single-layer mono `label value ▾` row (~18dp).
- Panel watermarks: `PanelSymbolWatermark` (platform-core) draws the big ticker
  with the exchange and `X-M Perp` contract label stacked in a column at
  7% alpha (chart keeps its own inline version); used by DOM and Trades.
  `PanelTextWatermark` is the generic form (big text + sub lines) and backs the
  docked trading panel watermark (exchange name + USDT-M/COIN-M, center of the
  content area).
- Trades panel width is a setting, not a drag: the panel ⚙ menu (title bar;
  `TradesWidthMenuContent`, features/trades jvmMain) opens a width slider
  (min = measured trade-row text width from `TradesWidget` + 4dp column gaps +
  8dp outer padding, pushed into `TradesViewModel.measuredMinWidthDp`, max =
  `TradesRecommendedWidth`); `TradesViewModel.attachPanel` persists it under
  `trades_width_{panelId}` and `WorkspaceView` feeds it into
  `LayoutRenderer.fixedPanelWidths`. DOM stays rigid `fixedPanelWidths`.
- Trading badges (chart and DOM share the look): colored rounded rect
  (long/short color), white mono text ~10sp with `lineHeight = 11.sp`, inline
  qty field (white background, dark centered text) that commits via
  cancel+replace, drag to move (cancel+replace, threshold ~0.5 row), double
  click on the label cancels, label shortens in stages when space is tight.
  Reduce-only badges name what they close: `Close Long` / `Close Short`
  (compact buttons may shorten to `CLong` / `CShort`).
- Position rendering: chart badge `Long 10 SOL 120.75` with a dark PnL plate
  (`#1B222B`) showing price change and USDT (green/red); DOM ladder position row
  is a full-width plate in the side color, left `Long 10 SOL` in white, price
  stays centered, right side shows PnL (price change + %) on a dark mini-plate.
- PnL lines: first value is price change in base units (never tick counts),
  then percent of price change, then USDT; on the DOM order panel the three
  values spread evenly across the panel width, `-/-` placeholders when flat.

## Known issues / gotchas

- Binance `trading` is a stub (fake ids) — use paper or MEXC for order flows.
- MEXC keys are env-only (`MEXC_API_KEY` / `MEXC_SECRET_KEY`); no settings UI.
- Market-data-server 15m footprint aggregates can be empty while the
  `trade-collector` deployment is pending (separate repo, out of scope here).
- `share-files/` holds user-provided reference screenshots; it is gitignored —
  never commit it, but check it for UI references when the user mentions it.
- Git may warn about LF→CRLF conversion on commit; that is expected on Windows.
