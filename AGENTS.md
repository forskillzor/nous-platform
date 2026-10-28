# AGENTS.md — nous-platform

Operating guide for AI agents working in this repository. Keep it up to date:
when a workflow discovery is made (a command pattern, a pitfall, a project
invariant), add it here.

## Tooling: JetBrains Index MCP is the primary code-intelligence tool

Use the `jetbrains-index` MCP server **first** for anything that requires
understanding code structure — before `grep`/`glob`/manual reading:

- `ide_find_references` — all usages of a symbol (including call sites in
  generated/other modules);
- `ide_find_definition`, `ide_find_implementations`, `ide_call_hierarchy`,
  `ide_type_hierarchy` — declarations, implementors, callers/callees;
- `ide_find_class`, `ide_find_file`, `ide_search_text` — symbol/file/text lookup;
- `ide_diagnostics`, `ide_project_diagnostics` — compiler/IDE errors and quick
  fixes;
- `ide_refactor_rename`, `ide_safe_delete`, `ide_move_file` — semantic edits.

The project must be open in the IDE and indexing finished
(`ide_index_status` → `isDumbMode=false`); if a call times out, retry once,
then narrow the target (position instead of class, or add `paths`). Only fall
back to the text search tools when the MCP server is unavailable or the query
is genuinely textual — and say so when you do. Do not quote URLs or rely on
stale line numbers: resolve symbols through the index.

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
| `features/chart` | Chart panel: rendering (candles/footprint), `ChartViewModel`, panel-scoped persistence (`ChartStatePersistor`), chart trading (order/position badges, TP/SL, drag = cancel+replace), indicators (off-main compute, feed source, TABS/STACKED panes, per-line colors, global favorites/presets). JVM UI: `ChartWindow`, `ChartTradingPanel`. |
| `features/dom` | DOM depth ladder: `DomViewModel` (order book, click-to-place, order chips with resize/drag/cancel, position plates, margin chip), `DomWindow` (jvm), `content/LevelRow` + `DomSection`, `footer/OrderPlacementPanel`. |
| `features/trading` | Docked trading panel (tabs Positions/Orders/Balance/History), `TradingViewModel`, `PaperSettingsController`, `PaperPersistenceService` (persists the shared paper account). |
| `features/trades` | Time & sales panel. |
| `features/localstorage` | SQLite-backed `StateStore` — key-value per-panel persistence. |
| `features/settings` | App settings surfaces. |
| `features/research` | TA4J-backed indicator engine: common `IndicatorType`/`IndicatorState`/`IndicatorOutput`/`IndicatorEngine`/`IndicatorCalculator`/`IndicatorInputSource`/`IndicatorDefinition` contracts, `DefaultIndicatorEngine` + `IndicatorOutputBuilder`, and the JVM-only `IndicatorRegistry` calculators (SMA, EMA, Bollinger, VWAP, Keltner, RSI, MACD, ATR, Stochastic, Volume, ADX, Liquidations). Chart consumes only the common API. |
| `build-logic` | Gradle convention plugins: `conventions.kmp-application`, `conventions.kmp-feature`, `conventions.kmp-library` (classes `Kmp*Convention.kt`). New feature modules apply the feature convention. All JVM targets compile with `jvmToolchain(25)`. |

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
  Symbol/timeframe/provider/contract-type and footprint state (`chart_symbol`,
  `chart_provider_id`, `chart_mode`, `chart_contract_type`, `fp_aggregation`,
  `fp_profile_scale`, `fp_ring_width`, `fp_ring_diameter`, ...) is also
  **panel-scoped** under the same prefix; there is no global fallback. In the
  workspace the symbol/timeframe come from `PanelConfig`, and `ChartWindow`
  restores the provider plus footprint settings via
  `ChartViewModel.restoreProvider()` (standalone restores them through
  `RestoreState`). `saveState()` is gated on `providerRestored` and snapshots
  the active provider, so an early panel save can never overwrite the persisted
  choice.
- **Providers are per panel.** `PanelConfig.providerId/providerName` is the
  panel's own exchange (null = restore from the panel-scoped StateStore key).
  `providerIdForPanel` no longer reads the workspace-level `ProviderRef`; the
  chart panel mirrors later user picks back into its own `PanelConfig`, and
  Market Watch "Apply to current workspace" is the only action that writes the
  provider into every panel at once. `navigatorExchangeLabel` prefers the first
  chart panel's `providerName` and falls back to `providers.first().name` for
  legacy configs.
- DOM: all panel settings are **workspace-scoped** like chart:
  `dom_paper_{workspaceId}_{panelId}`, `dom_trading_...`, `dom_provider_...`,
  `dom_contract_...` (USDT_M / COIN_M), `dom_confirm_`, `dom_reduce_`,
  `dom_order_type_`, `dom_leverage_`, `dom_margin_`, `dom_qty_`,
  `dom_collapsed_`, `dom_freq_` (level-emission frequency 100–300 ms).
  Legacy panel-only keys `dom_{field}_{panelId}` are read once on attach and
  migrated into the scoped key, so duplicated workspaces that share panel ids
  no longer share DOM state. DOM restore is gated: `DomPanel` must wait for
  `providerRestored` before applying symbol/depth/aggregation, and the VM is the
  single source of truth for the provider. On the first attach a stale default
  `PanelConfig.providerId` is healed from the panel store
  (`resolveRestoredProvider`); the restored provider is then mirrored back into
  `PanelConfig` by the one atomic options collector (never by a second
  provider-only collector, which used to overwrite it with a stale `domPc`).
- DOM depth is expressed in **aggregated levels**: the subscription requests
  `depth × aggregation.multiplier` raw levels (agg 10× + depth 20 → 200 raw →
  20 aggregated; agg 100× + depth 20 → 2000). Changing aggregation therefore
  resubscribes. `DomAdapter` always exposes snapshot-style `BookWindowLevels`
  windows regardless of the provider's internal mode: Binance uses native
  partial streams for raw depth 5/10/20 and a local diff-book
  (`@depth@100ms` + REST `/fapi/v1/depth` snapshot) above that; Bybit folds
  snapshot/delta locally; MEXC folds `sub.depth` increments. Binance/Bybit are
  capped at 1000 raw levels (20×100 shows ~10 aggregated levels), MEXC can serve
  the full requested window.
- Docked trading panel: `paper_enabled` (`PaperTrading.STORE_KEY`, shared),
  `trading_provider_{panelId}`, `trading_contract_{panelId}` (per panel). The window top bar
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
- Trades panel: `trades_provider_{panelId}`, `trades_contract_{panelId}` (per panel).
- Sidebar (tool panel) width: `tool_panel_width` (plain float dp, restore clamped
  to `workspaceToolPanelMin/MaxWidth`, save debounced 400 ms) via
  `ToolPanelPrefsRepository` (platform-core, `core/ui/panel`).
- Market Watch panel: `market_watch_prefs` (JSON `MarketWatchPrefs`: exchangeId
  (null = all), listId, sort name, descending, perpetualsOnly, quoteAsset,
  scrollIndex/scrollOffset) — same repository; legacy payloads ("ALL",
  "SYMBOL" defaults) decode via field defaults, scroll restores after catalog
  rows load, saves debounced 500 ms. The panel list is the shared
  `SymbolExplorerContent` (platform-core): tabs ALL + Favourites (other lists
  in the LISTS dropdown), ⌘K search, PERPETUALS/quote/exchange chips, arrow
  sort, `.P` display symbols, per-row star (active list or Favourites); the
  panel adds only a row context menu
  (Apply to current workspace, OPEN <sym> WITH templates, Add to <list name>)
  and a ⚙ menu (New/Rename/Delete list, Close panel). The scroll-to-top FAB
  lives in the shared content (so the popup has it too). Catalog failures are
  never printed in the body: a red exclamation badge in the panel title opens
  `TerminalContextPopup` with `MarketOverviewStore.state.error`.
- Chart: provider/contract-type state is panel-scoped
  (`chart_{workspace}_{panel}_chart_provider_id`, `..._chart_contract_type`).
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

### Chart indicators
- Feeding a new indicator requires only `IndicatorType`, a JVM calculator in
  `calculators/`, `IndicatorRegistry.all`, and (for a new feed) a field in
  `IndicatorInput`/`ChartIndicatorInputSource`. Renderer, legend, menu, labels,
  pane chrome and engine are definition-driven.
- Contracts live in `features/research` `commonMain`: `IndicatorDefinition`
  (static metadata + `IndicatorLineSpec`), `IndicatorCalculator`
  (`requiredInputs` + `compute`), `IndicatorInput`/`InputKind` (candles,
  liquidations, funding, open interest), `IndicatorInputSource`
  (`snapshot`/`updates`), `IndicatorOutputBuilder`, `DefaultIndicatorEngine`.
  TA4J 0.27 is JVM-only; `IndicatorRegistry` is `expect object` in common with
  the JVM `actual` owning the calculators.
- `DefaultIndicatorEngine` unions `requiredInputs`, takes one snapshot per
  compute, skips unsupported feeds and isolates calculator failures per
  instance. `IndicatorOutputBuilder` requires exact line-id keys, applies
  `titleTemplate`/`labelTemplate`, line colors and range precedence
  (`computation.range` → `fixedRange` → auto-range with `includeZero`).
- Chart feed source: `ChartIndicatorInputSource` (chart common) owns the
  liquidation `TimeSeriesController`/`LiquidationSeriesSource`; `ChartWindow`
  calls `setMarket(symbol, provider.liquidation, exchange = provider display
  name)` on symbol/provider changes and closes it on dispose. `snapshot` is
  lock-free; `setMarket` is suspend + mutex + generation-guarded. Feed updates
  emit `updates()`; `ChartWindow` collects them into
  `IndicatorComputePipeline.refresh()`.
- Liquidation history comes from market-data-server `GET /api/liquidations`
  (`exchange/symbol/from/to/limit`, `isLong=true → SELL`, ASC-sorted after the
  server's DESC response); live updates stay on the provider stream
  (`@forceOrder`/`allLiquidation`). `LiquidationSeriesSource` reconciles with
  the server on every closed minute (from last known event minus overlap),
  dedups by `timestamp+price+quantity+side`, and keeps local history if the
  server is unavailable. Binance `allForceOrders` is dead and must not be
  called; `liveAdapter` is optional so history-only feeds (no MEXC live) work.
- Indicator config is per chart panel: `ChartStatePersistor` stores JSON under
  `chart_{workspace}_{panel}_chart_indicators` (`KEY_INDICATORS`).
  Missing/invalid payload → `defaultIndicatorState()`; an explicitly saved
  empty state stays empty. Default is one Liquidations tab; Liquidations is a
  normal removable pane indicator with one signed `liq` histogram line
  (`SELL > 0`, `BUY < 0`), `SIGN` colors and `includeZero`.
- Overlay set: SMA, EMA, Bollinger Bands, VWAP, Keltner Channels. Pane set:
  RSI, MACD, ATR, Stochastic, Volume, ADX, Liquidations.
- Pane layout is global per chart panel (`IndicatorState.paneLayout`: `TABS`
  default / `STACKED`). TABS renders only the active pane at one shared
  `IndicatorState.tabsHeightDp`; switching tabs never changes the height.
  STACKED renders every pane at its own `IndicatorPaneState.heightDp`. Both
  are clamped to 40..240dp (`tabsHeightDp == null` is legacy state and follows
  the active pane until the first tab switch/resize). `ChartInteraction`
  reserves an 18dp tabs strip plus an 18dp legend header per pane and
  proportionally shrinks pane plots when the requested heights do not fit,
  keeping ~80dp for the main chart. `ChartCanvas` overlays `IndicatorPaneTabs`
  (drag reorder via measured tab centers) and `IndicatorPaneHeader`; drag the
  pane's top boundary (12dp invisible hit strip, vertical-resize cursor on
  hover, accent line while active) to resize; dragging up grows the pane,
  updating the shared height in TABS or that pane's height in STACKED, persisted
  on drag end. There are no visible resize handles.
- `ChartInteraction` draws `overlayRenderers` after the candle/footprint
  series inside `chartMainArea`; `indicatorRenderers` is the list of visible
  pane slots. `overlayIndicatorRenderer` / `paneIndicatorRenderer` in
  `IndicatorRenderer.kt` draw line/band/histogram/level outputs. Legend chips
  come from `output.lines` and use the same colors/roles; instances without
  output get a neutral `definition.label` fallback chip (no LONG/SHORT
  special case).
- Each visible pane has a right-side value axis. `ChartWindow` stores
  `ChartPaneVisual.valueRange` from `indicatorPaneRange(outputs)`; an empty
  pane gets null. `ChartInteraction` draws it through
  `drawIndicatorValueScale` in that pane's slice of `priceScaleArea`.
  `formatIndicatorValue` adapts decimals to the pane range and uses compact
  volume for large magnitudes. Crosshair vertical line spans the main chart
  and all panes; hovering a pane draws its horizontal line and value badge
  from the same range. Keep ranges aligned with `indicatorAreas`.
- Indicator computation runs off the UI thread through
  `IndicatorComputePipeline`: `Channel.CONFLATED`, one lazy worker on
  `Dispatchers.Default`, `StateFlow` outputs; the newest request wins and
  errors keep the last good output. `submit` remembers the last request;
  `refresh()` (feed update) re-enqueues it and is a no-op before the first
  submit. `ChartWindow` keys the pipeline by series identity and submits once
  per candle/instance change, never per redraw.
- `?x` (`ChartIndicatorButton` / `ChartIndicatorMenu`) lives in `ChartToolbar`
  (so `ChartTitleToolbar` gets it too): `ALL` / `FAVOURITES` tabs plus a
  `LISTS` preset dropdown, search (case-insensitive label/type filter),
  overlay/pane/on-chart sections separated by horizontal dividers, `NEW TAB`,
  and the active instances. It uses the shared `TerminalExplorerPopupContent`
  shell (the same as symbol explorer) with its own tabs/header/body slots.
  Definition rows lead with the explorer-style star. Stars are global
  through `IndicatorPresetRepository`
  (StateStore key `indicator_library`); presets store overlays, pane grouping,
  params, line colors and active pane index, not heights/layout. Applying a
  preset keeps the current pane heights by index, the shared tabs height and
  the current `paneLayout`. Parameter
  values are editable (Enter/blur commits, Esc/invalid reverts,
  `parseIndicatorParam` clamps to the spec). Labels use
  `indicatorInstanceTitle` / `indicatorPaneTitle` via definition
  `titleTemplate` (`MACD {shortPeriod}/{longPeriod}/{signalPeriod}`) and
  `formatTemplateValue` / `plainDecimalFixed` (no scientific notation).
- Per-line colors: `IndicatorInstance.lineColors` stores ARGB overrides keyed
  by output line id and ships with presets; `IndicatorOutputBuilder` applies
  them to `IndicatorLine.colorArgb`, which wins in renderer and legend, absent
  keys fall back to `IndicatorColorRole`. Expanded active rows expose
  `definition.lines` with `colorLabel`: `IndicatorColorRow` opens an 18-color
  theme palette, `THEME DEFAULT`, and custom RGB sliders plus hex `APPLY`.
  Changes go through `ChartIntent.SetIndicatorLineColor` (null resets) and
  persist with the panel.

### Chart/footprint performance invariants (JFR-guided)
- `ChartTradingPanel` owns the best bid/ask subscription (limit price
  fallback); it must not live in `ChartWindowContent`, otherwise every book
  ticker tick recomposes/redraws the whole chart. Subscribe only while the
  panel is expanded; paper/live trading does not change this.
- The footprint merge (`allFp`) and the visible autoscale range are memoized
  (`remember(series, startIdx, endIdx)`); keep range calculation out of every
  redraw.
- Footprint live accumulation runs on `Dispatchers.Default` and publishes a
  `liveCandle`/`currentPrice` snapshot at most every 100 ms. The candle series
  keeps running in footprint mode but must not publish `chartState` /
  `hasMoreHistory`; `latestCandleState` is applied when returning to
  CANDLESTICK. `chartLastPrice()` uses `footprintCurrentPrice` in footprint
  mode, and the candle mark price must not overwrite the footprint mark.
- Footprint forming is hybrid for every display timeframe: `liveMinute` is the
  current unclosed 1m built from trades (live price tail), `closedPrefix` is the
  server-accurate closed part assembled from forming sources (`5m` uses closed
  `1m`), and `liveCandle = closedPrefix + liveMinute`. A 1s tick refreshes on a
  new minute; the server 1m replaces the local completed 1m when it arrives
  (local fallback keeps the tail gap-free meanwhile). Never merge a partial
  source into the closed part: `assembleFormingCandle` takes `availableUntil`.
- `FootprintLevel`/`FootprintCandle` numeric caches are body `val`s (not
  getters); price-range scans are single-pass. `CandleSeriesSource.mergeItem`
  treats provider live volume as cumulative (`volume = update.volume`, never
  add it to the previous candle).
- `FootprintSeries` caches `aggregateLevels` per candle; `LocalStorage` DB work
  is serialized on `Dispatchers.IO` through a `Mutex` (`withDb`).
- Profile bar width is normalized by a user-selected percentile
  (`FootprintProfileScale` P90/P95/P99/MAX, default P99) over aggregated
  `max(bid, ask)` volumes in the visible range, cached per visible range in
  `FootprintSeries`. Levels above the percentile clamp to full half-candle
  width and get a side-colored ring whose width (0.5-5dp in 0.5dp steps,
  default 2dp) and diameter (1x-10x in 0.5x steps, default 3x) are dropdown
  settings in the chart gear menu; never normalize aggregated bars against
  unaggregated `candle.maxVolume`.
- Chart text uses `rememberTextMeasurer(cacheSize = 128)` — the default 8-slot
  cache thrashes on price/time-scale labels. Trades UI samples `state` and
  `filteredBuffer` at 50 ms (20 Hz), memoizes `TradeRow` formatted strings, and
  measures the width-slider minimum only when sample string lengths change.
- Trades retention has three levels in `TradesHistoryBuffer`: 20k raw trades
  (never rendered) used to reseed filtering, 500 feed trades for ALL, and 5k
  filtered trades (retention applied AFTER filtering). Changing the size filter
  reseeds `filtered` from raw instead of the 500-trade feed, so high filters do
  not lose older matches; symbol/provider change clears all three. Adapters have
  no retention and remain WS-only.
- `FootprintCandle.toSkeletonCandle()` must use the model's cached
  `open/high/low/close` body vals; only fall back to scanning `levels` when the
  min/max strings are invalid. `ChartWindowContent` caches skeleton candles per
  candle identity (completed history is not remapped on every live tick), and
  `drawFootprintCandle` skips levels whose price span is outside the visible
  range — heavy BTCUSDT footprints otherwise emit thousands of offscreen
  `drawRect`s while scrolling.

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
- Research indicator tests: `:features:research:jvmTest` (TA4J 0.27 requires Java 25).
- Provider tests: `:providers:mexc-provider:jvmTest`, `:providers:binance-provider:jvmTest`, `:providers:bybit-provider:jvmTest`
- JVM toolchain is **25** for every module (`jvmToolchain(25)` in
  `build-logic` conventions and `composeApp`; the root `Test` launcher also
  pins JDK 25). TA4J 0.27 is compiled for class-file 69, so JDK 22 tests fail
  with `UnsupportedClassVersionError`. `composeApp` pins
  `compose.desktop.application.javaHome` to the JDK 25 toolchain so
  `:composeApp:run` and `createDistributable` use the same runtime.

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
  explorer (`SymbolExplorerDropdownTest`), shared search
  (`SearchMatchingTest`), panel/workspace config serialization
  (`PanelConfigSerializationTest`), menu rows (`TerminalMenuItemTest`).
- `public-api/api-market/src/jvmTest` — paper engine, adapters, commands.
- `features/dom/src/commonTest` — DOM view model (fakes), including
  explorer selection (provider switch + pending symbol, contract-type sync).
- `features/chart/src/jvmTest` — chart state/persistence/rendering +
  explorer selection (`ChartExplorerRowsTest`), indicator labels/pane steps
  (`IndicatorLabelsTest`), indicator search (`IndicatorMenuSearchTest`),
  indicator view-model intents
  (`ChartViewModelCacheTest`), color parsing (`IndicatorColorPickerTest`),
  pane value mapping/formatting (`IndicatorValueScaleTest`), conflated compute
  (`IndicatorComputePipelineTest`) and global favorites/presets
  (`IndicatorPresetRepositoryTest`); no commonTest.
- `features/research/src/jvmTest` — TA4J calculators/registry through
  `DefaultIndicatorEngineTest`, `IndicatorRegistryTest`,
  `IndicatorOutputBuilderTest`, `CandlesExtTest`; chart feed source lives in
  `features/chart/src/jvmTest` (`ChartIndicatorInputSourceTest`).
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
- Left tool panels (Workspaces navigator, Market Watch, legacy tool types)
  share `ToolPanelTitleBar` (platform-core): 38dp row, leading glyph slot,
  uppercase bold mono title + optional count, trailing actions — the "airy"
  title style, no grip dots or lowercase titles.
- Panel top titles are `PanelHeader` (platform-core): drag grip, per-panel
  controls (`LayoutRenderer.panelTitleContent` slot), then ⚙ (menu via
  `panelSettingsContent`), + (dark 20dp circle, `surfaceVariant` fill +
  `outlineVariant` ring + accent plus drawn in Canvas; split menu; SPLIT RIGHT →
  `onSplitH` → Row, SPLIT DOWN → `onSplitV` → Column) and a 20dp Canvas ×
  glyph close matching the plus button. The
  chart title hosts `ChartTitleToolbar`: symbol explorer box (see below; its
  closed trigger shows the exchange label in small accent text before the
  symbol — provider switching happens by picking a row of another exchange,
  there is no separate exchange dropdown in the chart title), then the
  solid-accent timeframe chip (`TerminalBoxStyle.SELECTED`, dark text, no
  chevron) immediately after it, mode rounded box dropdown
  (`TerminalBoxStyle.ACCENT`; the closed trigger is a 14dp Canvas glyph for
  candles/footprint instead of text, the menu shows glyph + label),
  footprint-only aggregation (1x/10x/100x) and Profile percentile
  (P90/P95/P99/MAX) dropdowns, Trading on/off button; chart options
  (Type, Paper, Show orders/positions) and footprint-only ring width/diameter
  dropdowns live in the gear menu (`ChartSettingsMenu`). DOM/Trades/Trading
  titles are plain
  `PanelTitleLabel`s (DOM shows a `STREAM` dot; the Trades glyph is accent);
  their ⚙ menu closes the panel. In workspace tiles `ChartWindow` hides its own
  toolbar (standalone previews keep `TerminalPanelHeader`-wrapped version).
- Chart title symbol control is `SymbolExplorerDropdown` (platform-core):
  closed trigger shows the current exchange in `primary` accent at
  `microSize` before the unchanged symbol text;
  its popup body is the shared `SymbolExplorerContent` (hoisted state params,
  optional `rowMenu` per-row context menu hook) — Market Watch renders the
  same composable inline, so keep new explorer features inside the content;
  both explorer and indicator menus build on `TerminalExplorerPopupContent`
  (tabs/search/filters/list-header/footer slots), so keep shared popup chrome
  there instead of duplicating it;
  tabs ALL + Favourites uppercased; other watchlists live in the uppercased
  LISTS dropdown trigger showing the active list (stars write into an
  auto-created `Favourites` list), search field with hit highlight and
  ⌘K / Ctrl+K focus,
  chips PERPETUALS / quote / exchange, up/down-arrow sort trigger
  (`TerminalSortGlyph`; VOLUME / 24H % / PRICE / SYMBOL options, active
  direction ↓/↑ in the menu), 5-row scroll list (LazyColumn — keep it lazy,
  the catalog holds ~2k rows, and pin it to fixed width `menuWidth` × height
  `5 × 46dp`: the hosting M3 `DropdownMenu` measures content with
  `IntrinsicSize.Max`, and a SubcomposeLayout cannot answer intrinsic queries
  unless it has a specified size — never `fillMaxWidth()` there; the height is
  resizable via `TerminalResizeGrip` in the footer (vertical drag, right of
  MANAGE WATCHLIST; min 2 rows; max = app window height minus popup chrome, so
  it can grow almost to the whole window); scroll-to-top FAB over the list;
  star, `SYMBOL.P` for
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
  `contractType`); DOM row 1 = symbol explorer + collapse, row 2 =
  depth + agg + freq; FREQ throttles the level-structure emissions from the
  DOM model (100/150/200/250/300 ms, persisted per panel). Trades is a single
  row = symbol explorer + size filter.
  Trades size filter is one box button: funnel glyph (`TerminalFunnelGlyph` —
  a filter, unlike the explorer's `TerminalSortGlyph` arrows) + current mode
  in `primary` text ("ALL" / "≥ …").
- Left sidebar is `WorkspaceNavigator` (platform-core, replaced `ProjectTree`):
  workspace tree grouped by `WorkspaceConfig.group` folders ("Unsorted" =
  empty group, pinned first, `folderOrder` then alphabetical), folder
  create/rename/delete and workspace rename/duplicate/make-template/delete via
  theme island menus (`NavigatorMenuItem`), inline rename, search via the
  shared matcher (folder-name match keeps all children; invalid regex shows an
  error and hides the tree while searching), drag & drop for workspaces
  (into/between folders, auto-expands collapsed target) and folders, Quick
  Access templates section; pure model + DnD resolution live in
  `WorkspaceNavigatorModel.kt` (`buildNavigatorTree`,
  `visibleNavigatorItems`, `resolveNavigatorDropTarget`,
  `applyWorkspaceDrop`/`applyFolderDrop` — unit-tested). Header: mosaic +
  WORKSPACES + total count, explicit `+ workspace` / `+ folder` actions (no
  "…" menu anywhere; Expand/Collapse All and New Workspace/New Folder live in
  the empty-list right-click menu), New Folder is an inline
  `NavigatorNewFolderRow` (ENTER creates, ESC cancels), search focuses on
  Ctrl/⌘K and shows a clear × only when non-empty, matching also symbol.
  Workspace rows: selection background and active accent indicator are inset
  to the tree-guide line (x = 10.5dp, not the panel edge), full-height 24dp
  icon column with
  `NavigatorMiniMapGlyph` (16dp miniature of the actual panel layout in the
  same semantic type colors as `WorkspaceLayoutThumbnail` on WelcomeScreen,
  no inner labels; shares `collectRects` from `WelcomeScreen.kt`), inactive =
  32dp one line (name + exchange right, preview italic), active = 48dp two
  lines (name + exchange, then raw chart symbol only), thin bottom divider,
  tree indent guide (1dp vertical line at x = 10.5dp — centered between the
  panel edge and the child glyph, neutral `label` tint);
  folder rows 28dp uppercase, count at 60% alpha, gray outline folder glyph
  (`FolderGlyph(open = !collapsed)` — closed/open flap), active-folder accent
  tint, no status dot, no row "…" buttons — all actions via right-click
  context menus (`NavigatorContextMenu` title + shared `TerminalMenuItem`
  rows with pencil/copy/layers/trash glyphs). Quick Access is one header row
  (count + chevron) with a height-capped scroller of `NavigatorTemplateRow`s
  (miniature per template). New workspaces are
  created in Unsorted (`Templates.nous().copy(group = "")`). Folder order and
  collapsed set persist via `NavigatorStateRepository` (`navigator_state` in
  StateStore); structural moves persist user workspaces through
  `WorkspaceRepository.update(..., touchUpdatedAt = false)` so "recent"
  ordering is not polluted. Sidebar icon (mosaic) is
  `composeResources/drawable/workspaces.svg`; other navigator glyphs are
  Canvas-drawn in platform-core (no composeResources there).
- Workspace tabs follow IDEA preview semantics (do not break): a window holds
  at most one preview tab and tabs never duplicate. Single click in the
  navigator (or any non-drag tap) opens/reuses the preview via
  `WindowManager.openWorkspacePreviewIn`; double click pins
  (`openWorkspaceIn` → `openPinned`), instantly — double-tap is detected with
  a ~400 ms timestamp window inside `clickable` (never `combinedClickable`,
  it would delay the preview; TabBar does the same, nested ×/menu clickables
  consume their taps). The preview tab renders italic in `TabBar`
  (`previewWorkspaceId`), double click on a tab promotes it
  (`WindowManager.promotePreview`). A different pinned open keeps the current
  preview; the preview slot is replaced in place; replacing/closing/moving/
  detaching disposes the workspace VM only when no window holds it
  (`disposeIfUnused`). `previewWorkspaceId` persists with the session and
  survives restart via `WindowSessionState`; `restoreSessions` drops stale
  ids, deduplicates tabs and falls back to the last tab when active is stale.
  `duplicateWorkspaceConfig` (WorkspaceConfig.kt) makes "`{name} copy`" in the
  same folder; the Duplicate menu item opens it pinned. Pure tab transitions
  live in `PreviewTabs.kt` (`openPreview`/`openPinned`/`promotePreview`/
  `withoutWorkspace`) and are covered by `PreviewTabsTest` +
  `WindowManagerTest` (inject `StandardTestDispatcher`).
- The shared rounded dropdown shell is `TerminalBoxDropdown` (platform-core,
  compact MW-LIST style: 11sp mono text, smallCard radius, 2dp/6dp padding,
  12dp chevron; styles `NORMAL` (45% `panelHeaderSurface` fill, white text),
  `ACCENT` (`surfaceVariant` fill, accent text — provider/mode) and `SELECTED`
  (solid accent fill, dark text, no chevron — timeframe)); the Market Watch
  LIST dropdown renders through it —
  keep new title dropdowns on it instead of hand-rolled boxes. Dropdown menu
  popups (the shell, explorer chip/sort submenus, panel ⚙/split menus,
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
  chips/sort, chart settings rows, panel/⚙ menus and trades size filter;
  guarded by `TerminalMenuItemTest`. Do not reintroduce `DropdownMenuItem`
  for option rows (bidasker-web is a separate web prototype, exempt).
- Search-match highlighting is the shared `highlightedMatch` (platform-core
  `SearchHighlight.kt`): accent 24% fill + accent bold text. Use it for every
  regex-match label (explorer, Market Watch, Workspaces navigator, indicator
  menu) instead of ad-hoc SpanStyles. Matching and the highlight range come
  from the shared `SearchMatching.kt` (`compileSearchRegex`, `matchSearchFields`,
  `searchItems`), so filtering and highlighting cannot diverge. An invalid
  regex is an error in all three searches (explorer, navigator, indicator
  menu); there is no literal fallback.
- Search inputs are the shared `TerminalSearchField` (platform-core): 28dp
  bordered box with search glyph, placeholder, CTRL K hint and × clear (Esc
  clears too). Used by the Workspaces navigator and the explorer/Market Watch
  search; reuse it instead of hand-rolling search rows.
- Explorer/Market Watch tab row is fixed: ALL + Favourites (matched by name,
  case-insensitive); every other list lives in a `TerminalBoxDropdown` whose
  trigger shows the active list (or LISTS). The menu is built on the shared
  `TerminalDropdownMenu` / `TerminalMenuItem` (hover/selected styling like
  every other menu, `ExplorerTabs.kt` in platform-core) and supports
  drag-reorder inside the dropdown only; order persists through
  `SymbolWatchlistRepository.reorder(ids)` with Favourites keeping its slot
  (`explorerMergedWatchlistOrder`).
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
- Corner resize affordances (Market Watch tool-panel width, explorer popup list
  height) use the shared `TerminalResizeGrip` (diagonal 45° two-way arrow,
  `TerminalResizeOrientation.HORIZONTAL`/`VERTICAL`). `LayoutRenderer` leaves
  publish their size via `LocalPanelSize` (IntSize; zero outside a workspace
  panel); explorer popups cap their height to the app window via
  `LocalWindowInfo` instead of the host panel.
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
- Docked trading panel `Cancel All` / `Close All` are borderless compact text
  actions (no outlined frame, tight padding) with a `selection` hover fill
  (same as the active Market Watch row); DOM keeps outlined `SideButton`
  actions.

## Known issues / gotchas

- Binance `trading` is a stub (fake ids) — use paper or MEXC for order flows.
- MEXC keys are env-only (`MEXC_API_KEY` / `MEXC_SECRET_KEY`); no settings UI.
- Market-data-server 15m footprint aggregates can be empty while the
  `trade-collector` deployment is pending (separate repo, out of scope here).
- `share-files/` holds user-provided reference screenshots; it is gitignored —
  never commit it, but check it for UI references when the user mentions it.
- Git may warn about LF→CRLF conversion on commit; that is expected on Windows.
