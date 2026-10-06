# AGENTS.md — nous-platform

Operating guide for AI agents working in this repository. Keep it up to date:
when a workflow discovery is made (a command pattern, a pitfall, a project
invariant), add it here.

## What this project is

Desktop Kotlin Multiplatform (Compose) trading terminal. The workspace is a
tile grid with panels: chart (candlestick + footprint), DOM depth ladder,
time & sales, docked trading panel. Market data and trading come from
providers (Binance, MEXC). A shared paper-trading engine mirrors real trading
so the whole UI can be exercised without exchange credentials. Per-panel
state (trading flags, paper mode, order settings) is persisted in a local
SQLite state store.

## Module map

| Module | Responsibility |
| --- | --- |
| `composeApp` | Desktop entry point, Koin DI (`di/AppModule.kt`), workspace UI, paper settings overlay. This is the executable app (`:composeApp:run`). |
| `platform-core` | Workspace/tile/window system, theme (`TradingTerminalTheme`), shared UI components (`TerminalSwitch`, `TerminalDropdown`), number formatting (`SymbolFormatter`, `PlainDecimal`), `StateStore` interface. |
| `core/core-dependencies` | Shared Compose and dependency helpers. |
| `public-api/api-market` | Provider contracts: `Provider`, `ProviderConfig`, `ProviderRegistry`, adapter interfaces (`TradingAdapter`, `SymbolInfoAdapter`, `DomAdapter`, `ChartAdapter`, `TradesAdapter`, `BookTickerAdapter`), models (`SymbolInfo`, trading models), the paper engine (`PaperTrading`, `PaperTradingAdapter`, `effectiveTrading`), trading commands (`MarketOrderCommand`, `LimitOrderCommand`, `BestBidAskCommand`), shared `TradingAdapter.replaceOrder` extension in `adapters/OrderOperations.kt`. |
| `public-api/api-trading`, `public-api/api-ui` | Additional API surfaces. |
| `providers/binance-provider` | Binance USDT-M. Market data is real; `trading` is a stub that returns fake success with `TEST-<timestamp>` ids and tracks nothing — never use it to validate real order behaviour. |
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
  `dom_contract_{panelId}` (USDT_M / COIN_M).
- Docked trading panel: `paper_enabled` (`PaperTrading.STORE_KEY`),
  `trading_provider`, `trading_contract_type` (global keys).
- Trades panel: `trades_provider` (global key).
- Chart: provider/contract-type state is global (`chart_provider_id`,
  `chart_contract_type`).

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
- Color/face constants used across trading UI: buy `0xFF26A69A`,
  sell `0xFFEF5350`, ok `0xFF00C853`, warn `0xFFE0A95B`,
  accent `0xFF5B9BD5`, label `0xFF8A97A5`, dark PnL plate `0xFF1B222B`.

## Build / test commands (Windows, PowerShell)

Repo root: `C:\Users\skillzor\IdeaProjects\nous\nous-platform`

- Compile app: `:composeApp:compileKotlinJvm`
- Module compile: `:features:dom:compileKotlinJvm`, `:features:chart:compileKotlinJvm`, etc.
- Standard test set:
  `:platform-core:jvmTest :public-api:api-market:jvmTest :features:chart:jvmTest :features:trading:jvmTest :features:dom:jvmTest`
- Provider tests: `:providers:mexc-provider:jvmTest`, `:providers:binance-provider:jvmTest`

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
- `platform-core/src/jvmTest` — workspace/format utilities.
- `public-api/api-market/src/jvmTest` — paper engine, adapters, commands.
- `features/dom/src/commonTest` — DOM view model (fakes).
- `features/chart/src/jvmTest` — chart state/persistence/rendering; no commonTest.
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

- Chart panel surfaces: DrawingToolPanel style — border `ChartColors.gridLine`,
  panel background, 8dp rounding.
- Prices: always via `SymbolFormatter` (`formatPrice`, `roundPrice`).
- Paper vs real data source: `Provider.effectiveTrading(paper)`; per-panel flag.
- `TerminalSwitch`: OFF = grey; ON = grey thumb on a terminal-green track
  (`0xFF00C853`, 65% alpha). In compact headers disable the M3 touch target
  (`LocalMinimumInteractiveComponentSize = Dp.Unspecified`) and use `scale(0.7f)`.
- Compact selects use `TerminalDropdown`; panel headers (DOM/Trades) use
  `TerminalInlineSelect` — a single-layer mono `label value ▾` row (~18dp).
- Panel watermarks: `PanelSymbolWatermark` (platform-core) draws the big ticker
  with the exchange and `X-M Perp` contract label stacked in a column at
  7% alpha (chart keeps its own inline version); used by DOM and Trades.
- Trades panel width is a setting, not a drag: the header gear
  (`TradesSettingsGear`) opens a width slider (min = measured trade-row text
  width from `TradesWidget` + 4dp column gaps + 8dp outer padding, max =
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
