# Technical Book of the `feature-dom` Module

## Developing a Depth of Market (DOM) Component with Kotlin + Compose Multiplatform

**Level:** Junior → Middle  
**Technologies:** Kotlin, Compose Multiplatform, Koin DI, LazyColumn, Canvas, Ktor, WebSocket  
**Product version:** Nous Platform 1.0  
**Author:** Nous Team

---

# Table of Contents

1. [Introduction: What Is DOM (Depth of Market)](#1-introduction-what-is-dom-depth-of-market)
2. [Module Architecture](#2-module-architecture)
3. [Entry Point: DomWindow and main()](#3-entry-point-domwindow-and-main)
4. [Dependency Injection: How Koin Assembles the DOM](#4-dependency-injection-how-koin-assembles-the-dom)
5. [DomViewModel: The Heart of Data Management](#5-domviewmodel-the-heart-of-data-management)
6. [Incremental Data: SnapshotStateMap](#6-incremental-data-snapshotstatemap)
7. [Handling DomEvent Events](#7-handling-domevent-events)
8. [DomRepositoryImpl: Order Book Synchronization](#8-domrepositoryimpl-order-book-synchronization)
9. [OrderBook: The Order Book Model](#9-orderbook-the-order-book-model)
10. [DomAggregator: Price Level Aggregation](#10-domaggregator-price-level-aggregation)
11. [AggregationLevel: Aggregation Levels](#11-aggregationlevel-aggregation-levels)
12. [DomOptions: Unified Settings State](#12-domoptions-unified-settings-state)
13. [TradingProvider and TradingSymbol](#13-tradingprovider-and-tradingsymbol)
14. [DepthLimit: Depth Limitation](#14-depthlimit-depth-limitation)
15. [OrderIntent: Intent to Place an Order](#15-orderintent-intent-to-place-an-order)
16. [DomWindow: Building the UI](#16-domwindow-building-the-ui)
17. [DomHeader: Settings Header](#17-domheader-settings-header)
18. [DomContent and DomSection: Rendering the Order Book](#18-domcontent-and-domsection-rendering-the-order-book)
19. [LevelRow: One Order Book Row](#19-levelrow-one-order-book-row)
20. [OrderPlacementPanel: Order Placement Panel](#20-orderplacementpanel-order-placement-panel)
21. [Automatic Scroll to Best Price](#21-automatic-scroll-to-best-price)
22. [Formatting Utilities](#22-formatting-utilities)
23. [Conclusion: How It All Works Together](#23-conclusion-how-it-all-works-together)
24. [Appendix: Glossary](#24-appendix-glossary)

---

# 1. Introduction: What Is DOM (Depth of Market)

## 1.1. Context and Purpose

**DOM** (Depth of Market), or the **order book**, is a table of all active orders to buy (bid) and sell (ask) for a particular trading instrument. Each row shows the price and the order volume at that price.

Visually, a DOM looks like this:

```
Bid Vol  │  Price  │  Ask Vol
──────────────────────────────
         │  67050  │  1.234
         │  67049  │  0.567
  2.100  │  67048  │
  1.500  │  67047  │
  0.800  │  67046  │  0.100
```

Where:
- **Bid (BID)** — buy orders (left, blue/green color)
- **Ask (ASK)** — sell orders (right, red color)
- **Price** — in the middle
- **Spread** — the difference between the best bid and the best ask

## 1.2. What the feature-dom Module Does

The module displays the order book in real time with support for:

- Viewing bid/ask levels with volumes
- Visualizing volumes (horizontal bars)
- Aggregating levels (grouping by price steps)
- Selecting a data provider (Binance Spot, Coin-M Futures, Bybit, Kraken)
- Selecting a trading pair
- Configuring order book depth (20-1000 levels)
- Clicking a price to select it
- Placing market/limit orders
- Automatic scrolling to the best price
- Locally disabling/enabling trading

## 1.3. Architecture: Data Flow

```
Binance WebSocket (REST Snapshot + WS @depth + WS @bookTicker)
       │
       ▼
DomAdapter (in binance-provider)
       │
       ▼
DomRepositoryImpl (synchronizes snapshots and increments)
       │
       ▼  (Flow<DomEvent>)
DomViewModel (SnapshotStateMap, processDomEvent)
       │
       ▼  (SnapshotStateMap + StateFlow)
DomWindow (derivedStateOf → buildDisplayOrderBook)
       │
       ▼
DomSection → LazyColumn → LevelRow
```

## 1.4. Module File Structure

```
features/feature-dom/
├── build.gradle.kts
└── src/
    └── commonMain/
        └── kotlin/com/aandios/nous/feature/dom/
            ├── data/repository/
            │   └── DomRepositoryImpl.kt        # Order book synchronization
            ├── di/
            │   └── FeatureDomModule.kt          # Koin DI
            ├── domain/
            │   ├── DomAggregator.kt             # Level aggregation
            │   ├── DomOptions.kt                # Unified settings state
            │   ├── OrderBook.kt                 # Order book model
            │   ├── TradingProvider.kt           # Providers (Binance, Bybit...)
            │   ├── TradingSymbol.kt             # Trading pairs
            │   └── model/
            │       ├── AggregationLevel.kt      # Aggregation levels (1×, 10×, 100×)
            │       ├── DepthLimit.kt            # Order book depth
            │       └── OrderIntent.kt           # Order intent
            └── ui/
                ├── DomUtils.kt                  # Utilities
                ├── DomViewModel.kt              # ViewModel
                ├── DomWindow.kt                 # Entry point + UI assembly
                ├── content/
                │   ├── DomContent.kt            # Order book content
                │   ├── DomSection.kt            # Section with LazyColumn
                │   └── LevelRow.kt              # A single DOM row
                ├── footer/
                │   └── OrderPlacementPanel.kt   # Order panel
                └── header/
                    ├── AggregationLevelDropdown.kt
                    ├── CompactProviderSymbol.kt
                    ├── DepthLimitDropdown.kt
                    ├── DomHeader.kt
                    ├── DomHeaderCompact.kt
                    ├── SymbolDropdown.kt
                    └── TradingProviderDropdown.kt
```

---

# 2. Module Architecture

## 2.1. build.gradle.kts

```kotlin
plugins {
    id("conventions.kmp-feature")
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    sourceSets {
        commonMain.dependencies {
            implementation(project(":platform-core"))
            implementation(project(":public-api:api-market"))
            implementation(project(":providers:binance-provider"))
            implementation(project(":composeApp"))

            implementation(libs.koin.core)
            implementation(libs.koin.compose)
            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.compose.material3)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit.jupiter)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}
```

### 2.1.1. Dependency on `:composeApp`

Unlike `feature-chart`, this module depends on `:composeApp`. This is legacy — `composeApp` contains the shared commands (TradingCommand, BuyMarketCommand, etc.) used in the order panel.

**For Juniors**: `project(":composeApp")` is a reference to another module in the same multimodule Gradle project. We can use classes from `composeApp` as if they were in our module.

---

# 3. Entry Point: DomWindow and main()

## 3.1. The main() Function

```kotlin
fun main() = application {
    stopKoin()
    initKoinForPreview()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Nous Platform • DOM Preview",
        state = rememberWindowState(width = 300.dp, height = 800.dp)
    ) {
        KoinContext {
            TradingTerminalTheme {
                DomWindow()
            }
        }
    }
}
```

**Key differences from ChartWindow**:
- Window size: 300×800 instead of 800×600 (a DOM is narrow and tall)
- Uses `initKoinForPreview()` from `FeatureDomModule`
- title = "DOM Preview"

## 3.2. The DomWindow() Function

```kotlin
@Composable
fun DomWindow() {
    val domViewModel: DomViewModel = koinInject()
    val domOptions by domViewModel.domOptions.collectAsState()
    val orderQuantity by domViewModel.orderQuantity.collectAsState()
    val isTradingEnabled by domViewModel.isTradingEnabled.collectAsState()
    val symbolTickSize by domViewModel.symbolTickSize.collectAsState()
    val selectedPrice by domViewModel.selectedPrice.collectAsState()

    // SnapshotStateMap — read directly
    val incrementalBids = domViewModel.incrementalBids
    val incrementalAsks = domViewModel.incrementalAsks

    val incrementalBestBid by domViewModel.incrementalBestBid.collectAsState()
    val incrementalBestAsk by domViewModel.incrementalBestAsk.collectAsState()
    // ...

    // Compute the displayed unified order book with aggregation
    val displayUnifiedOrderBook by remember(domOptions.aggregation, symbolTickSize) {
        derivedStateOf {
            buildDisplayOrderBook(
                bids = incrementalBids,
                asks = incrementalAsks,
                bestBid = incrementalBestBid,
                bestAsk = incrementalBestAsk,
                symbol = domOptions.symbol.symbol,
                aggregation = domOptions.aggregation,
                symbolTickSize = symbolTickSize
            )
        }
    }

    Column(Modifier.fillMaxSize()) {
        DomHeader(...)
        Box(Modifier.weight(1f)) {
            DomContent(orderBook = displayUnifiedOrderBook, ...)
        }
        OrderPlacementPanel(
            selectedPrice = selectedPrice,
            orderQuantity = orderQuantity,
            bestBidPrice = displayBookTicker.bestBid,
            bestAskPrice = displayBookTicker.bestAsk,
            // ...
        )
    }
}
```

### 3.2.1. Screen Layout

```
┌──────────────────────┐
│     DomHeader         │  ← Provider, symbol, depth, aggregation
├──────────────────────┤
│                      │
│     DomContent        │  ← LazyColumn with order book levels
│     (weight=1f)      │
│                      │
├──────────────────────┤
│  OrderPlacementPanel  │  ← Order buttons, Qty, Trade Off
│  (height=180.dp)     │
└──────────────────────┘
```

---

# 4. Dependency Injection: How Koin Assembles the DOM

## 4.1. The `featureDomModule` Module

```kotlin
val featureDomModule = module {
    // 1. Provider configuration
    single<ProviderConfig> { ProviderConfig(apiKey = null, secretKey = null, ...) }

    // 2. Create the Provider via the factory
    single<Provider> {
        val config = get<ProviderConfig>()
        val networkManager = get<NetworkManager>()
        BinanceProviderFactory().createProvider(config, networkManager)
    }

    // 3. Adapters from the provider
    single<DomAdapter> { get<Provider>().dom ?: error("DOM adapter not available") }
    single<BookTickerAdapter> { get<Provider>().bookTicker ?: error("BookTicker adapter not available") }
    single<SymbolInfoAdapter> { get<Provider>().symbolInfo ?: error("SymbolInfo adapter not available") }

    // 4. Repositories
    single<DomRepository> { DomRepositoryImpl(domAdapter = get(), bookTickerAdapter = get()) }
    single<BookTickerRepository> { BookTickerRepositoryImpl(bookTicker = get()) }
    single<SymbolInfoRepository> { SymbolInfoRepositoryImpl(symbolInfoAdapter = get()) }

    // 5. ViewModel
    factory { DomViewModel(domRepository = get(), symbolInfoRepository = get()) }
}
```

### 4.1.1. Three Adapters

Unlike feature-chart (which only has ChartAdapter), the DOM uses three adapters:

1. **`DomAdapter`** — for the depth stream (snapshots + incremental updates)
2. **`BookTickerAdapter`** — for the best bid/ask (best prices)
3. **`SymbolInfoAdapter`** — for symbol information (tickSize)

### 4.1.2. `initKoinForPreview()`

```kotlin
fun initKoinForPreview() {
    stopKoin()
    startKoin {
        modules(coreModule, featureDomModule)
    }
}
```

Stops the old Koin and starts a new one with only the core + DOM modules.

---

# 5. DomViewModel: The Heart of Data Management

## 5.1. Constructor and Dispatcher

```kotlin
class DomViewModel(
    private val domRepository: DomRepository,
    private val symbolInfoRepository: SymbolInfoRepository? = null,
    private val coroutineDispatcher: CoroutineDispatcher? = null,
) {
    private val dispatcher = coroutineDispatcher
        ?: Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val viewModelScope = CoroutineScope(dispatcher + SupervisorJob())
    private var subscriptionJob: Job? = null
```

### 5.1.1. A Dedicated Thread Pool

```kotlin
Executors.newSingleThreadExecutor().asCoroutineDispatcher()
```

The ViewModel uses a **single-threaded executor** instead of `Dispatchers.Main`. This ensures that all DOM event processing (parsing, SnapshotStateMap updates) happens on a dedicated background thread without blocking the UI.

### 5.1.2. `coroutineDispatcher` as a Parameter

```kotlin
private val coroutineDispatcher: CoroutineDispatcher? = null
```

A parameter for **testing** — tests can pass `Dispatchers.Unconfined` or a `TestDispatcher`.

## 5.2. State (StateFlows)

```kotlin
private val _domOptions = MutableStateFlow(DomOptions.default())
val domOptions: StateFlow<DomOptions> = _domOptions.asStateFlow()

private val _selectedPrice = MutableStateFlow<Double?>(null)
private val _orderQuantity = MutableStateFlow("0.01")
private val _isTradingEnabled = MutableStateFlow(true)
private val _lastCommandResult = MutableStateFlow<CommandResult?>(null)
private val _symbolTickSize = MutableStateFlow<Double?>(null)
```

### 5.2.1. Loading tickSize

```kotlin
init {
    viewModelScope.launch {
        delay(500) // a short delay so as not to block startup
        val defaultSymbol = _domOptions.value.symbol.symbol
        fetchSymbolTickSize(defaultSymbol)
    }
    restartSubscription(_domOptions.value)
}
```

On ViewModel initialization:
1. Loads the tickSize (price step) for the default symbol
2. Starts the WebSocket subscription

---

# 6. Одна таблица уровней: SnapshotStateMap

## 6.1. The Problem

Книга обновляется с максимальной частотой partial-стрима — 10 раз в секунду
(100мс), каждое окно содержит до `depth*2` уровней. Копирование карты уровней
на каждое окно давало бы аллокации и лишние рекомпозиции.

## 6.2. Solution: SnapshotStateMap + derivedStateOf

```kotlin
// ЕДИНСТВЕННАЯ таблица уровней: сюда окна применяются целиком, её читает UI
private val levels = mutableStateMapOf<Long, DomLevel>()

// Отсортированный вид — пересчитывается только при изменении levels
private val sortedLevelsState = derivedStateOf {
    levels.values.sortedByDescending { it.priceTicks }
}
val sortedLevels: List<DomLevel> get() = sortedLevelsState.value
```

### 6.2.1. What Is SnapshotStateMap?

`mutableStateMapOf()` creates a mutable map that Compose "observes". 

**Key feature**: Compose can track **changes to individual entries** (key-value pairs), not the map as a whole. If one element changes, Compose redraws only the composables that read that specific key.

### 6.2.2. Advantages

```kotlin
// ❌ Без SnapshotStateMap — карта копируется целиком
private val _bids = MutableStateFlow<Map<Double, Double>>(emptyMap())
_bids.value = _bids.value + (price to newQty) // O(N) copy of the whole map!

// ✅ SnapshotStateMap — точечная инвалидация
levels[key] = DomLevel(key, bidSteps = qs) // O(1), без копий
```

### 6.2.3. Comparison with StateFlow

| | StateFlow<Map<>> | SnapshotStateMap |
|---|---|---|
| Mutation | New map copy | In-place |
| Recomposition | Entire list | Entry only |
| GC pressure | High | Low |
| Complexity | Simpler | Requires `derivedStateOf` |

## 6.3. Обработка окна: полная замена

```kotlin
private fun handleBookWindow(event: DomEvent.BookWindow) {
    bidsByPrice.clear()
    asksByPrice.clear()
    event.bids.forEach { u -> bidsByPrice[u.price] = u.quantity }
    event.asks.forEach { u -> asksByPrice[u.price] = u.quantity }
    if (scaleReady) rebuildLevelsFromWindow()
}
```

`levels.clear()` в SnapshotStateMap атомарен — Compose увидит замену всех
записей как одно изменение и перерисует UI один раз.

## 6.4. Best Prices Stored Separately

```kotlin
private val _bestPrices = MutableStateFlow(BestPricesState())
```

Лучшие цены хранятся отдельно от таблицы уровней: они приходят из отдельного
стрима (`@bookTicker`) и используются для обрезки уровней по глубине.

---

# 7. Handling DomEvent Events

## 7.1. Subscribing to Events

```kotlin
private fun restartSubscription(options: DomOptions) {
    subscriptionJob?.cancel()
    subscriptionJob = viewModelScope.launch {
        subscribeToIncrementalDom(options)
    }
}

private suspend fun subscribeToIncrementalDom(options: DomOptions) {
    // Reset data
    _incrementalBids.clear()
    _incrementalAsks.clear()
    _incrementalBestBid.value = null
    _incrementalBestAsk.value = null
    _incrementalBestBidQuantity.value = null
    _incrementalBestAskQuantity.value = null

    domRepository.subscribeToDomEvents(
        symbol = options.symbol.symbol,
        depth = options.depth.value
    ).catch { e ->
        println("❌ DOM Events Error: ${e.message}")
    }.collect { event ->
        processDomEvent(event)
    }
}
```

### 7.1.1. Resubscribing on Settings Changes

```kotlin
fun updateDomOptions(newOptions: DomOptions) {
    val oldOptions = _domOptions.value
    if (oldOptions != newOptions) {
        _domOptions.value = newOptions

        val subscriptionChanged =
            oldOptions.provider != newOptions.provider ||
            oldOptions.symbol != newOptions.symbol ||
            oldOptions.depth != newOptions.depth

        if (subscriptionChanged) {
            restartSubscription(newOptions)
        }
        // If the symbol changed — update tickSize
        if (oldOptions.symbol != newOptions.symbol) {
            fetchSymbolTickSize(newOptions.symbol.symbol)
        }
    }
}
```

Only a change to provider, symbol, or depth triggers a resubscription. A change to aggregation does not (aggregation is applied locally at the UI level).

## 7.2. processDomEvent()

```kotlin
private fun processDomEvent(event: DomEvent) {
    when (event) {
        is DomEvent.BookWindow -> handleBookWindow(event)
        is DomEvent.BestPrices -> handleBestPrices(event)
    }
}

private fun handleBookWindow(event: DomEvent.BookWindow) {
    bidsByPrice.clear()
    asksByPrice.clear()
    event.bids.forEach { u -> bidsByPrice[u.price] = u.quantity }
    event.asks.forEach { u -> asksByPrice[u.price] = u.quantity }

    // Метаданные (tickSize/stepSize) могли ещё не прийти — окно пропускаем,
    // следующее (через 100мс) отрисует книгу целиком
    if (scaleReady) rebuildLevelsFromWindow()
}
```

Каждое окно **полностью заменяет** книгу: `levels.clear()` → заполнение из окна
(bucket-агрегация, правило «ask вытесняет bid»), затем `trimLevelsIfNeeded()`.

### 7.2.1. Event Types

| Event | Source | Description |
|---|---|---|
| `BookWindow` | WebSocket (partial depth) | Топ-N уровней с абсолютными объёмами; заменяет книгу целиком |
| `BestPrices` | WebSocket (@bookTicker) | Best bid/ask prices |

### 7.2.2. SnapshotStateMap.clear()

```kotlin
levels.clear()
```

`clear()` on a SnapshotStateMap is an atomic operation. Compose will see the changes to all entries as a single change and redraw the UI once.

## 7.3. Commands (TradingCommand)

```kotlin
fun executeCommand(command: TradingCommand?) {
    if (command != null) {
        viewModelScope.launch {
            if (!_isTradingEnabled.value && command !is TradeOffCommand) {
                _lastCommandResult.value = CommandResult.TradingDisabled
                return@launch
            }
            if (!command.canExecute()) {
                _lastCommandResult.value = CommandResult.Error("Cannot execute")
                return@launch
            }
            command.execute()
        }
    }
}
```

### 7.3.1. handleOrderIntent()

```kotlin
fun handleOrderIntent(intent: OrderIntent) {
    val command = when (intent) {
        is OrderIntent.MarketBuy -> BuyMarketCommand(...)
        is OrderIntent.MarketSell -> SellMarketCommand(...)
        is OrderIntent.LimitBuy -> BuyLimitCommand(...)
        is OrderIntent.LimitSell -> SellLimitCommand(...)
        is OrderIntent.BestBidBuy -> BuyBestBidCommand(...)
        is OrderIntent.BestAskSell -> SellBestAskCommand(...)
        OrderIntent.ToggleTrading -> TradeOffCommand(...)
    }
    executeCommand(command)
}
```

The `OrderIntent` sealed class is converted into a `TradingCommand` (from composeApp). This is a separation of concerns: the UI only knows about `OrderIntent`, while the ViewModel creates the command.

---

# 8. DomRepositoryImpl: Book Window Subscription

Простой репозиторий на partial-стриме: инкрементальной синхронизации больше нет.

## 8.1. Partial Book Depth Stream (Binance Futures)

Стрим `<symbol>@depth<levels>@100ms` шлёт **самодостаточные окна книги**:

- каждое сообщение содержит топ-N уровней с **абсолютными объёмами**;
- уровни, выпавшие из топ-N, в следующем сообщении просто отсутствуют;
- синхронизация с REST-снапшотом, валидация U/u/pu — **не нужны**.

Важно: на Binance Futures частичные окна существуют **только для уровней
5/10/20** (стримы `depth50/100/500/1000@...` сервер отвергает). Поэтому
`DepthLimit` ограничен диапазоном 5..20, значения — {5, 10, 20}, дефолт 20.
Частота — 100мс (максимальная).

## 8.2. Implementation in DomRepositoryImpl

```kotlin
override suspend fun subscribeToDomEvents(symbol: String, depth: Int): Flow<DomEvent> = callbackFlow {
    var attempt = 0
    while (true) {
        try {
            val windowJob = launch {
                domAdapter.subscribeToBookWindow(symbol, depth)
                    .catch { e -> println("⚠️ Book window stream error: ${e.message}") }
                    .collect { window -> trySend(DomEvent.fromWindow(window)) }
            }
            val tickerJob = launch {
                bookTickerAdapter.subscribeToBookTicker(symbol)
                    .catch { e -> println("⚠️ BestPrices stream error: ${e.message}") }
                    .collect { bookTicker -> trySend(DomEvent.fromBookTicker(bookTicker, symbol)) }
            }

            select {
                windowJob.onJoin { }
                tickerJob.onJoin { }
            }
            windowJob.cancel()
            tickerJob.cancel()

            attempt++
            val delayMs = (250L * 2.0.pow((attempt - 1).coerceAtMost(5))).toLong().coerceAtMost(8000L)
            delay(delayMs)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            close(e)
            break
        }
    }
    close()
}
```

### 8.2.1. `callbackFlow` — a Manual Flow

```kotlin
callbackFlow {
    // ...
    trySend(event)  // ← send the event to the Flow
    close()         // ← close the Flow
}
```

`callbackFlow` is a Flow builder that allows sending events manually via `trySend()`. It is used to integrate callback-based APIs (WebSocket).

### 8.2.2. Reconnection with Exponential Backoff

Если один из стримов завершился (обрыв/ошибка) — оба джоба отменяются, и после
`delay(250 * 2^(attempt-1))` (250мс → 8с, потолок) оба стрима переподписываются.
Отдельный `Reset` не нужен: при реконнекте следующее окно само восстановит книгу.

---

# 9. DomLevel: Одна таблица уровней

Классы `OrderBook` и `DomAggregator` **удалены** вместе с инкрементальным
протоколом. Агрегация и модель уровня упрощены:

## 9.1. DomLevel

```kotlin
/**
 * Один уровень книги. Правило «одна сторона на цене»:
 * уровень либо bid, либо ask (объём другой стороны null).
 */
data class DomLevel(
    val priceTicks: Long,
    val bidSteps: Long? = null,
    val askSteps: Long? = null
)
```

Ключ карты — `bucketKey(priceTicks) = priceTicks / aggMultiplier * aggMultiplier`
(цена, округлённая вниз до корзины агрегации).

## 9.2. rebuildLevelsFromWindow()

```kotlin
private fun rebuildLevelsFromWindow() {
    levels.clear()
    bidsByPrice.forEach { (price, qty) ->
        val pt = toPriceTicks(price)
        val qs = toQtySteps(qty)
        if (qs <= 0L) return@forEach
        val key = bucketKey(pt)
        val cur = levels[key]?.bidSteps ?: 0L
        levels[key] = DomLevel(key, bidSteps = cur + qs)
    }
    // ask вытесняет bid при совпадении ключа (правило одной стороны)
    asksByPrice.forEach { (price, qty) -> /* ...askSteps... */ }
}
```

Вызывается на каждое окно и при смене агрегации. Агрегация — просто
суммирование шагов объёма в корзину (`bucketKey`), без отдельного агрегатора.

Обрезки по глубине **нет**: окно partial-стрима уже ограничено (≤ depth×2
уровней, depth ≤ 20), а для ценовой лесенки уровни должны оставаться на своих
ценовых строках — скролл по цене видит всю книгу.

---

# 11. AggregationLevel: Aggregation Levels

## 11.1. Sealed Class

```kotlin
sealed class AggregationLevel(val multiplier: Double) {
    object BaseTick : AggregationLevel(1.0)      // 1× — no aggregation
    object TenTick : AggregationLevel(10.0)       // 10×
    object HundredTick : AggregationLevel(100.0)  // 100×

    companion object {
        fun all(): List<AggregationLevel> = listOf(BaseTick, TenTick, HundredTick)
        fun fromString(value: String): AggregationLevel = when (value) {
            "BaseTick", "1×", "1x" -> BaseTick
            "TenTick", "10×", "10x" -> TenTick
            "HundredTick", "100×", "100x" -> HundredTick
            else -> throw IllegalArgumentException("Unknown: $value")
        }
    }
}
```

### 11.1.1. Sealed Class vs. Sealed Interface

A `sealed class` is used rather than a `sealed interface` because all subclasses share the `multiplier` field. A sealed class allows storing state in the parent.

## 11.2. Key Methods

**effectiveTickSize**: `baseTickSize * multiplier`

```kotlin
fun effectiveTickSize(baseTickSize: Double): Double = baseTickSize * multiplier
```

Example: tickSize=0.01, TenTick → 0.01 * 10 = 0.1

**roundDown**: rounding the price down

```kotlin
fun roundDown(price: Double, baseTickSize: Double): Double {
    val tick = effectiveTickSize(baseTickSize)
    if (tick <= 0.0) return price
    return (price / tick).toInt() * tick
}
```

Example: price=67000.05, tick=0.1 → (67000.05/0.1).toInt()*0.1 = 67000.0

**aggregationKey**: a string key for grouping

```kotlin
fun aggregationKey(price: String, baseTickSize: Double): String {
    val priceDouble = price.toDoubleOrNull() ?: return price
    val rounded = roundDown(priceDouble, baseTickSize)
    return if (rounded == rounded.toInt().toDouble()) {
        rounded.toInt().toString()
    } else {
        rounded.toString().trimEnd('0').trimEnd('.')
    }
}
```

Used in `DomAggregator` to group levels.

---
# 12. DomOptions: Unified Settings State

## 12.1. The Class

```kotlin
data class DomOptions(
    val provider: TradingProvider = TradingProvider.BINANCE,
    val symbol: TradingSymbol = TradingSymbol.defaultForProvider(TradingProvider.BINANCE),
    val depth: DepthLimit = DepthLimit.default(),
    val aggregation: AggregationLevel = AggregationLevel.BaseTick,
    val collapsed: Boolean = false
) {
    companion object {
        fun default() = DomOptions()
    }

    val subscriptionKey: String
        get() = "${provider.name}:${symbol.symbol}:${depth.value}"
}
```

### 12.1.1. Why a unified state?

All DOM settings are stored in a single `StateFlow`. This simplifies:
- Change tracking (one `collectAsState()` instead of five)
- Transition validation
- Save/restore

### 12.1.2. subscriptionKey

```kotlin
val subscriptionKey: String get() = "${provider.name}:${symbol.symbol}:${depth.value}"
```

A computed key used to determine whether resubscription is needed. If the provider, symbol, or depth has not changed, the subscription is not restarted.

---

# 13. TradingProvider and TradingSymbol

## 13.1. TradingProvider — an enum of providers

```kotlin
enum class TradingProvider(
    val displayName: String,
    val supportsFutures: Boolean = true
) {
    BINANCE("Binance", true),
    BINANCE_COIN_M("Binance Coin-M Futures", true),
    BINANCE_USDM("Binance USD-M Futures", true),
    BYBIT("Bybit", true),
    KRAKEN("Kraken", false);

    companion object {
        fun default(): TradingProvider = BINANCE_COIN_M
        fun all(): List<TradingProvider> = values().toList()
        fun futuresProviders(): List<TradingProvider> = values().filter { it.supportsFutures }
    }
}
```

### 13.1.1. Enum parameters

Each enum can have properties:
```kotlin
BINANCE("Binance", true)
//        ↑ displayName   ↑ supportsFutures
```

## 13.2. TradingSymbol — a data class for a pair

```kotlin
data class TradingSymbol(
    val symbol: String,       // "BTCUSD_PERP"
    val displayName: String,  // "BTCUSD Perp"
    val provider: TradingProvider
)
```

### 13.2.1. Static symbol lists

Each provider has a predefined list of symbols:
```kotlin
val BINANCE_COIN_M_FUTURES_SYMBOLS = listOf(
    TradingSymbol("BTCUSD_PERP", "BTCUSD Perp", TradingProvider.BINANCE_COIN_M),
    TradingSymbol("ETHUSD_PERP", "ETHUSD Perp", TradingProvider.BINANCE_COIN_M),
    // ...
)
```

### 13.2.2. Lookup by provider

```kotlin
fun getSymbolsForProvider(provider: TradingProvider): List<TradingSymbol> {
    return when (provider) {
        TradingProvider.BINANCE -> BINANCE_SPOT_SYMBOLS
        TradingProvider.BINANCE_COIN_M -> BINANCE_COIN_M_FUTURES_SYMBOLS
        TradingProvider.BYBIT -> BYBIT_SPOT_SYMBOLS
        // ...
    }
}
```

---

# 14. DepthLimit: Depth Limitation

## 14.1. The Class

```kotlin
data class DepthLimit(val value: Int) {
    init {
        require(value in MIN_VALUE..MAX_VALUE) {
            "Depth limit must be between $MIN_VALUE and $MAX_VALUE, got $value"
        }
    }

    companion object {
        const val MIN_VALUE = 20
        const val MAX_VALUE = 1000
        const val DEFAULT_VALUE = 1000

        fun default(): DepthLimit = DepthLimit(DEFAULT_VALUE)
        fun create(value: Int): DepthLimit = DepthLimit(value.coerceIn(MIN_VALUE, MAX_VALUE))

        val standardValues = listOf(10, 20, 50, 100, 200, 500, 1000)
    }
}
```

### 14.1.1. The `init` block — validation

```kotlin
init {
    require(value in MIN_VALUE..MAX_VALUE) { ... }
}
```

`require` throws an `IllegalArgumentException` if the condition is not met. This protects against invalid values.

### 14.1.2. `create()` vs the constructor

- `DepthLimit(50)` — may throw an exception
- `DepthLimit.create(50)` — safely coerces into the range

---

# 15. OrderIntent: Intent to Place an Order

## 15.1. Sealed class

```kotlin
sealed class OrderIntent {
    data class MarketBuy(val symbol: String, val quantity: Double) : OrderIntent()
    data class MarketSell(val symbol: String, val quantity: Double) : OrderIntent()
    data class LimitBuy(val symbol: String, val price: Double, val quantity: Double) : OrderIntent()
    data class LimitSell(val symbol: String, val price: Double, val quantity: Double) : OrderIntent()
    data class BestBidBuy(val symbol: String, val bestBidPrice: Double, val quantity: Double) : OrderIntent()
    data class BestAskSell(val symbol: String, val bestAskPrice: Double, val quantity: Double) : OrderIntent()
    object ToggleTrading : OrderIntent()
}
```

### 15.1.1. Why `sealed class` and not `sealed interface`?

`OrderIntent` uses a `sealed class` because it has a `toOrderData()` method with shared conversion logic. But a sealed class is fine here either way — there is no functional difference.

### 15.1.2. `toOrderData()`

```kotlin
fun toOrderData(): OrderData? = when (this) {
    is MarketBuy -> OrderData(symbol, OrderSide.BUY, OrderType.MARKET, quantity = quantity)
    is LimitBuy -> OrderData(symbol, OrderSide.BUY, OrderType.LIMIT, price = price, quantity = quantity)
    ToggleTrading -> null
    // ...
}
```

Converts the intent into an `OrderData` from `composeApp` for execution via `TradingCommand`.

---

# 16. DomWindow: Building the UI

## 16.1. Optimization: derivedStateOf

```kotlin
val displayUnifiedOrderBook by remember(domOptions.aggregation, symbolTickSize) {
    derivedStateOf {
        buildDisplayOrderBook(
            bids = incrementalBids,
            asks = incrementalAsks,
            bestBid = incrementalBestBid,
            bestAsk = incrementalBestAsk,
            symbol = domOptions.symbol.symbol,
            aggregation = domOptions.aggregation,
            symbolTickSize = symbolTickSize
        )
    }
}
```

### 16.1.1. What is derivedStateOf?

`derivedStateOf` is a Compose function that creates a **derived state**, which is recalculated only when the states it reads change.

**Key point:** `remember` with keys determines when to recreate the `derivedStateOf`, but the `derivedStateOf` itself "lazily" reacts to changes in the states it reads.

## 16.2. buildDisplayOrderBook()

```kotlin
private fun buildDisplayOrderBook(
    bids: Map<Double, Double>,
    asks: Map<Double, Double>,
    bestBid: Double?,
    bestAsk: Double?,
    symbol: String,
    aggregation: AggregationLevel,
    symbolTickSize: Double?
): OrderBook {
    val priceMap = mutableMapOf<String, OrderBookLevel>()

    // Add bids, filtering by bestBid
    bids.forEach { (price, quantity) ->
        if (bestBid != null && price > bestBid) return@forEach
        priceMap[price.toString()] = OrderBookLevel(
            price = price.toString(), quantity = quantity.toString(),
            bidQty = quantity.toString(), askQty = ""
        )
    }

    // Add asks, filtering by bestAsk
    asks.forEach { (price, quantity) ->
        if (bestAsk != null && price < bestAsk) return@forEach
        // merge with the existing bid level
    }

    // Sort by descending price
    val sortedLevels = priceMap.values.sortedByDescending {
        it.price.toDoubleOrNull() ?: 0.0
    }

    val spread = if (bestBid != null && bestAsk != null) bestAsk - bestBid else null

    val unified = OrderBook(symbol, sortedLevels, ..., bestBid, bestAsk, spread, ...)

    return if (aggregation != AggregationLevel.BaseTick && symbolTickSize != null) {
        unified.aggregate(aggregation, symbolTickSize)
    } else {
        unified
    }
}
```

### 16.2.1. Filtering by bestBid/bestAsk

```kotlin
if (bestBid != null && price > bestBid) return@forEach
```

This is important: only levels **below** the best bid and **above** the best ask are displayed. Anything that "crosses" the spread is ignored.

### 16.2.2. Bid/ask unification

Each level can contain both bid and ask volume for the same price. This is called a **unified order book**:

```
Price    Bid Qty    Ask Qty
67000.0  1.500
67000.1  0.800      0.300  ← same price, both volumes
67000.2             1.200
```

## 16.3. Building the BookTicker

```kotlin
val displayBookTicker = BookTicker(
    symbol = domOptions.symbol.symbol,
    bestBid = incrementalBestBid ?: 0.0,
    bestBidQty = incrementalBestBidQuantity ?: 0.0,
    bestAsk = incrementalBestAsk ?: 0.0,
    bestAskQty = incrementalBestAskQuantity ?: 0.0,
    lastPrice = 0.0,
    timestamp = System.currentTimeMillis()
)
```

Built from incremental data, without a separate repository.

---

# 17. DomHeader: Settings Header

## 17.1. Structure

```
┌──────────────────────────────┐
│ [Provider ▼]      ● LIVE  ▲ │
│                              │
│ [Symbol ▼]  [Depth Limit ▼]  │
│                              │
│ [Aggregation ▼]              │
└──────────────────────────────┘
```

### 17.1.1. Two modes

```kotlin
@Composable
fun DomHeader(...) {
    if (domOptions.collapsed) {
        DomHeaderCompact(...)  // Provider + symbol only
    } else {
        ExpandedDomHeader(...) // All settings
    }
}
```

`collapsed` is a boolean field in `DomOptions`. It allows collapsing the header to free up space for the order book.

### 17.1.2. ExpandedDomHeader

Three rows:
1. **Provider + Live indicator + Collapse button**
2. **Symbol + Depth Limit**
3. **Aggregation Level**

## 17.2. Compact mode (DomHeaderCompact)

```kotlin
@Composable
fun DomHeaderCompact(
    tradingProvider: TradingProvider,
    tradingSymbol: TradingSymbol,
    isLive: Boolean,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Provider name + symbol only
    Row {
        Text(tradingProvider.displayName)
        Text(tradingSymbol.displayName)
        IconButton(onClick = onToggleExpand) {
            Icon(Icons.Default.ArrowDropDown, ...) // downward arrow (expand)
        }
    }
}
```

---

# 18. DomContent and DomSection: Ценовая лесенка

## 18.1. DomContent — the entry point for content

```kotlin
@Composable
fun DomContent(
    symbol: String,
    levelsMap: Map<Long, DomLevel>,
    ladderStepTicks: Long,
    selectedPrice: Double?,
    bestBidDisplayTicks: Long?,
    bestAskDisplayTicks: Long?,
    lastPriceDisplayTicks: Long?,
    tickSize: Double,
    stepSize: Double,
    formatter: SymbolFormatter,
    onPriceSelected: (Double) -> Unit,
    modifier: Modifier = Modifier
) { /* DomSection(...) */ }
```

Тонкая обёртка над `DomSection`.

## 18.2. DomSection — классическая лесенка (price ladder)

Рендерится **сплошная ось цен**: одна строка = одна цена (корзина агрегации),
**включая пустые уровни**. Объёмы привязаны к своим ценовым строкам и при
движении рынка остаются на месте — движение видно по смещению подсветок
(best bid/ask, последняя сделка) и перетеканию объёмов между строками.

Строки генерируются вокруг якоря:

```kotlin
private const val ROWS_ABOVE = 120
private const val ROWS_BELOW = 120

// Якорь: best ask → best bid → верхний уровень книги
val anchorTicks = bestAskDisplayTicks ?: bestBidDisplayTicks
    ?: levelsMap.keys.maxOrNull() ?: 0L

LazyColumn {
    items(
        count = ROWS_ABOVE + 1 + ROWS_BELOW,
        key = { index -> anchorTicks + (ROWS_ABOVE - index) * step }
    ) { index ->
        val key = anchorTicks + (ROWS_ABOVE - index) * step
        LevelRow(priceTicks = key, level = levelsMap[key], ...)
    }
}
```

Ключ LazyColumn — **цена** (`bucketKey`): при перегенерации строк Compose
переиспользует строки по цене, объёмы «перетекают» без полного пересоздания.

## 18.3. Следование за последней ценой

- Якорь лесенки — **последняя сделка** (`lastPriceDisplayTicks`); до её
  прихода — best ask → best bid → верхний уровень книги.
- Строки генерируются вокруг якоря, поэтому строка якоря всегда имеет индекс
  `ROWS_ABOVE`. Если она уходит за край видимой зоны (запас 3 строки),
  список **минимально подтягивается обратно**: якорь ставится на строку
  `MARGIN_ROWS` от края — далеко (>30 строк) мгновенно (`scrollToItem`),
  близко — плавно (`animateScrollToItem`).
- Пока якорь болтается внутри зоны — скролла нет, рынок «дышит» внутри
  книги, двигаются только подсветка и объёмы.

---

# 19. LevelRow: One Order Book Row

## 19.1. Visual structure of a row

```
┌────────────────────────────────────────────┐
│ ████ 1.500  │ 67000.0 │  0.300 ████       │
│    bid vol    price        ask vol          │
└────────────────────────────────────────────┘
```

## 19.2. Component

```kotlin
@Composable
fun LevelRow(
    priceTicks: Long,
    level: DomLevel?,          // null = пустой уровень (только цена)
    maxSteps: Long,
    selectedDisplayTicks: Long?,
    bestBidDisplayTicks: Long?,
    bestAskDisplayTicks: Long?,
    lastPriceDisplayTicks: Long?,
    tickSize: Double,
    stepSize: Double,
    formatter: SymbolFormatter,
    onPriceClick: (Long, Double) -> Unit
) {
    val price = priceTicks * tickSize
    val bidQty = level?.bidSteps?.let { it * stepSize }
    val askQty = level?.askSteps?.let { it * stepSize }
    // ...
}
```

Строка лесенки знает только свою цену и читает уровень по ключу —
точечная реактивность: при обновлении объёма на цене рекомпозится только
эта строка.

## 19.3. Подсветки

- **Последняя сделка** (`lastPriceDisplayTicks`) — фон `tertiary` (14% alpha)
  и цена цветом `tertiary`: маркер прыгает по строкам, показывая поток сделок.
- **Выбранная цена** — жёлтый фон (клик по строке).
- **Пустой уровень** (`level == null`) — цена приглушена (35% alpha).

## 19.4. Visualizing volumes

Горизонтальные бары, ширина пропорциональна объёму:
```kotlin
val volumeWidth = (bidSteps.toFloat() / maxSteps.toFloat()).coerceIn(0f, 1f)
Box(Modifier.fillMaxWidth(volumeWidth).background(primary.copy(alpha = 0.3f)))
```

`maxSteps` — максимум по всей таблице уровней (`derivedStateOf` по `levelsMap`).
Ask-бар выравнивается по правому краю (`align(CenterEnd)`).

---

# 20. OrderPlacementPanel: Order Placement Panel

## 20.1. Panel structure

```
┌──────────────────────────────┐
│ Trading: ON                  │
│ PnL: 67000.0                 │
│ Qty: [   0.01      ]        │
│ [Market Buy] [Market Sell]   │
│ [Buy Limit]  [Sell Limit]    │
│ [Best Bid]   [Best Ask]      │
│ [       ⚠️ TRADE OFF       ] │
└──────────────────────────────┘
```

## 20.2. Market orders

```kotlin
Row {
    TerminalButton(onClick = {
        val quantity = orderQuantity.toDoubleOrNull() ?: 0.0
        onOrderIntent(OrderIntent.MarketBuy(symbol, quantity))
    }) { Text("Market Buy") }

    TerminalButton(onClick = {
        val quantity = orderQuantity.toDoubleOrNull() ?: 0.0
        onOrderIntent(OrderIntent.MarketSell(symbol, quantity))
    }) { Text("Market Sell") }
}
```

**Market Order** — executes immediately at the current market price.

## 20.3. Limit orders (at the selected price)

```kotlin
TerminalButton(onClick = {
    if (selectedPrice != null) {
        val quantity = orderQuantity.toDoubleOrNull() ?: 0.0
        onOrderIntent(OrderIntent.LimitBuy(symbol, selectedPrice, quantity))
    }
}) {
    Text(if (selectedPrice != null) "Buy Limit" else "Buy Limit (select price)")
}
```

The button is inactive (gray text) until a price is selected.

## 20.4. Best Bid/Ask orders

```kotlin
TerminalButton(onClick = {
    if (bestBidPrice != null && bestBidPrice > 0) {
        onOrderIntent(OrderIntent.BestBidBuy(symbol, bestBidPrice, quantity))
    }
}) {
    Text(if (bestBidPrice != null) "Best Bid" else "Best Bid (waiting...)")
}
```

## 20.5. Trade Off button

```kotlin
TerminalButton(onClick = { onOrderIntent(OrderIntent.ToggleTrading) },
    isActive = !isTradingEnabled  // Active when trading is OFF
) {
    Text(if (isTradingEnabled) "⚠️ TRADE OFF" else "✅ TRADE ON",
         color = if (isTradingEnabled) Color.Red else Color.Green)
}
```

A local kill-switch — disables the ability to send orders without disabling the data subscription.

---

# 21. Следование лесенки за последней ценой

## 21.1. The problem

Последняя цена постоянно меняется. Если лесенка неподвижна, рынок «уезжает»
из видимой области, и трейдер теряет точку отсчёта.

## 21.2. The solution

Строки лесенки генерируются вокруг последней сделки, поэтому содержание
вьюпорта автоматически следует за рынком, пока позиция скролла привязана
к строкам по цене. Остаётся только дотягивать строку якоря (индекс
`ROWS_ABOVE` — константа) до края видимой зоны, когда рынок выходит
за неё с запасом в 3 строки:

```kotlin
LaunchedEffect(anchorTicks, step) {
    if (lazyListState.isScrollInProgress) return@LaunchedEffect
    val visible = lazyListState.layoutInfo.visibleItemsInfo
    if (visible.isEmpty()) return@LaunchedEffect

    val first = visible.first().index
    val last = visible.last().index
    val allowedTop = first + MARGIN_ROWS
    val allowedBottom = last - MARGIN_ROWS
    val anchorIndex = ROWS_ABOVE

    val distance = when {
        anchorIndex < allowedTop -> allowedTop - anchorIndex
        anchorIndex > allowedBottom -> anchorIndex - allowedBottom
        else -> return@LaunchedEffect          // в зоне — ничего не делаем
    }

    // Минимальная коррекция: якорь на строку MARGIN_ROWS от края
    val targetIndex = if (anchorIndex < allowedTop) {
        anchorIndex - MARGIN_ROWS
    } else {
        anchorIndex - (visible.size - 1) + MARGIN_ROWS
    }.coerceAtLeast(0)

    if (distance > 30) lazyListState.scrollToItem(targetIndex, 0)
    else lazyListState.animateScrollToItem(targetIndex, 0)
}
```

### 21.2.1. Key points

1. **Запас 3 строки** (`MARGIN_ROWS`): пока последняя цена болтается в видимой
   зоне с запасом — скролла нет, рынок «дышит» внутри книги.
2. **Минимальная коррекция**: якорь подтягивается к краю зоны, а не в центр —
   вид пользователя сохраняется насколько возможно.
3. **Не мешаем жесту**: `isScrollInProgress` → пропуск.
4. **Далеко — мгновенно, близко — плавно**: `distance > 30` → `scrollToItem`,
   иначе `animateScrollToItem` (первое центрирование/смена символа не рвёт глаз).

---

# 22. Formatting Utilities

## 22.1. formatPrice (for DomSection)

```kotlin
fun formatPrice(price: Double): String {
    return when {
        price >= 1000 -> String.format("%.2f", price)
        price >= 100 -> String.format("%.3f", price)
        price >= 10 -> String.format("%.4f", price)
        price >= 1 -> String.format("%.5f", price)
        else -> String.format("%.6f", price)
    }
}
```

Adaptive precision depending on the price. For BTC (~67000) — 2 decimal places, for small altcoins — up to 6.

## 22.2. formatVolume (for DomSection)

```kotlin
fun formatVolume(volume: Double): String {
    return when {
        volume >= 1000 -> String.format("%.1fk", volume / 1000)  // 1.5k
        volume >= 100 -> String.format("%.0f", volume)           // 150
        volume >= 10 -> String.format("%.1f", volume)            // 15.0
        else -> String.format("%.2f", volume)                    // 0.15
    }
}
```

## 22.3. formatDomPrice (for OrderPlacementPanel)

```kotlin
fun formatDomPrice(price: Double): String {
    return when {
        price >= 1000 -> String.format("%.2f", price)
        price >= 100 -> String.format("%.3f", price)
        price >= 10 -> String.format("%.4f", price)
        price >= 1 -> String.format("%.5f", price)
        else -> String.format("%.6f", price)
    }
}
```

Similar to `formatPrice`, but located in the `DomUtils.kt` file and used in the footer.

---

# 23. Conclusion: How It All Works Together

## 23.1. Startup sequence

```
1. main() in DomWindow.kt
   │
2. stopKoin() → initKoinForPreview()
   │   Creates DI: FeatureDomModule + coreModule
   │
3. Window(...) { DomWindow() }
   │
4. DomWindow():
   │
   ├── koinInject() → DomViewModel
   │   │
   │   └── init():
   │       ├── fetchSymbolMetadata(symbol)             ← loading tickSize/stepSize
   │       └── restartSubscription(options)           ← starting WebSocket
   │           │
   │           └── subscribeToBookWindows():
   │               │
   │               └── domRepository.subscribeToDomEvents(symbol, depth)
   │                   │
   │                   ├── WebSocket depth<levels>@100ms → BookWindow (замена книги)
   │                   └── WebSocket @bookTicker → BestPrices
   │
   ├── collectAsState() → domOptions, selectedPrice, symbolTickSize, etc.
   │
   ├── derivedStateOf → sortedLevels
   │   │
   │   └── levels (SnapshotStateMap<Long, DomLevel>) sorted desc
   │       └── bucket-агрегация уже применена при записи в levels
   │
   └── Column:
       ├── DomHeader (provider, symbol, depth, aggregation selection)
       ├── DomSection (LazyColumn with a LevelRow for each level)
       └── OrderPlacementPanel (order buttons)
```

## 23.2. Data update cycle

```
WebSocket partial depth event (каждые 100мс)
    │
    ▼
DomAdapter → BookWindowLevels → DomRepositoryImpl → callbackFlow
    │
    ▼ trySend(DomEvent.BookWindow)
DomViewModel.handleBookWindow()
    │
    ├── levels.clear() + заполнение корзин (bucket-агрегация)
    │
    ▼ Compose tracks the changes in SnapshotStateMap
derivedStateOf { levels.values.sortedByDescending { it.priceTicks } }
    │
    ▼ Новый список DomLevel
DomSection → LazyColumn recomposition
    │
    ▼ Compose compares keys and updates only the changed rows
LevelRow redraw (only for the changed levels)
```

## 23.3. Component interaction on click

```
The user clicks a LevelRow at price 67000.0
    │
    ▼
onPriceClick(67000.0)
    │
    ▼
DomViewModel.selectPrice(67000.0)
    │
    ├── _selectedPrice.value = 67000.0
    │
    ▼ Compose redraw
OrderPlacementPanel: "Buy Limit" and "Sell Limit" buttons become active
DomContent: the LevelRow at price 67000.0 is highlighted in yellow

The user presses "Buy Limit"
    │
    ▼
onOrderIntent(OrderIntent.LimitBuy("BTCUSD_PERP", 67000.0, 0.01))
    │
    ▼
DomViewModel.handleOrderIntent(intent)
    │
    ├── Creates BuyLimitCommand
    ├── executeCommand(command)
    │   ├── Checks isTradingEnabled
    │   ├── Checks command.canExecute()
    │   └── command.execute() → sends the order to the exchange
```

## 23.4. Key architectural decisions

### 23.4.1. SnapshotStateMap for performance
Instead of copying level maps on every update (hundreds of times per second), the DOM uses `mutableStateMapOf()` with in-place mutation.

### 23.4.2. derivedStateOf instead of collectAsState
`buildDisplayOrderBook` is called only when the data has actually changed, not on every recomposition.

### 23.4.3. A dedicated thread pool
`Executors.newSingleThreadExecutor()` for the ViewModel — all DOM event processing happens off the UI thread.

### 23.4.4. The Binance synchronization protocol
Snapshot + incremental updates with validation. On error — reinitialization with exponential backoff.

### 23.4.5. LazyColumn with keys
Efficient redrawing of only the changed rows. The `"level-${price}"` keys help Compose understand which rows to update.

### 23.4.6. Automatic scroll without conflicts
Scroll-to-best-price does not interfere with the user (the `isScrollInProgress` check).

---

# 24. Appendix: Glossary

| Term | Meaning |
|---|---|
| **DOM** | Depth of Market — the order book |
| **Bid** | An order to buy |
| **Ask** | An order to sell |
| **Spread** | The difference between the best bid and the best ask |
| **Order Book** | The book of orders — a table of all active orders |
| **Depth** | The number of order book levels |
| **Snapshot** | A full capture of the order book at a moment in time |
| **Incremental update** | An incremental update (a change to a single level) |
| **Tick Size** | The minimum price increment of an instrument |
| **Level** | One order book level (price + volume) |
| **Unified level** | A level containing both bid and ask volume |
| **Aggregation** | Grouping levels by a price step |
| **Best bid** | The highest buy price |
| **Best ask** | The lowest sell price |
| **BookTicker** | A real-time stream of best prices (best bid/ask) |
| **Market order** | A market order — executes immediately |
| **Limit order** | A limit order — executes at the specified price |
| **Exponential backoff** | A retry strategy with an increasing delay |
| **SnapshotStateMap** | A Compose-observable map with in-place mutation |
| **derivedStateOf** | A derived state that is recalculated lazily |
| **callbackFlow** | A Flow builder for callback-based APIs |
| **LazyColumn** | A virtualized list in Compose (reuses items) |
| **WebSocket** | A bidirectional real-time protocol |
| **Reconnection** | Automatic reconnection when the connection drops |
| **REST** | An HTTP API for fetching the order book snapshot |
| **lastUpdateId** | The ID of the last update in the snapshot (for synchronization) |
| **Coin-M Futures** | Futures with coin-margined collateral (BTC, ETH) |
| **USD-M Futures** | Futures with USDT/USDC-margined collateral |
| **Spot** | Spot trading (the actual coin) |
