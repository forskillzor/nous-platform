# Техническая книга модуля `feature-dom`

## Разработка стакана заявок (DOM) на Kotlin + Compose Multiplatform

**Уровень:** Junior → Middle  
**Технологии:** Kotlin, Compose Multiplatform, Koin DI, LazyColumn, Canvas, Ktor, WebSocket  
**Версия продукта:** Nous Platform 1.0  
**Автор:** Команда Nous

---

# Оглавление

1. [Введение: Что такое DOM (Depth of Market)](#1-введение-что-такое-dom-depth-of-market)
2. [Архитектура модуля](#2-архитектура-модуля)
3. [Точка входа: DomWindow и main()](#3-точка-входа-domwindow-и-main)
4. [Dependency Injection: Как Koin собирает DOM](#4-dependency-injection-как-koin-собирает-dom)
5. [DomViewModel: Сердце управления данными](#5-domviewmodel-сердце-управления-данными)
6. [Инкрементальные данные: SnapshotStateMap](#6-инкрементальные-данные-snapshotstatemap)
7. [Обработка событий DomEvent](#7-обработка-событий-domevent)
8. [DomRepositoryImpl: Синхронизация стакана](#8-domrepositoryimpl-синхронизация-стакана)
9. [OrderBook: Модель стакана](#9-orderbook-модель-стакана)
10. [DomAggregator: Агрегация ценовых уровней](#10-domaggregator-агрегация-ценовых-уровней)
11. [AggregationLevel: Уровни агрегации](#11-aggregationlevel-уровни-агрегации)
12. [DomOptions: Единый стейт настроек](#12-domoptions-единый-стейт-настроек)
13. [TradingProvider и TradingSymbol](#13-tradingprovider-и-tradingsymbol)
14. [DepthLimit: Ограничение глубины](#14-depthlimit-ограничение-глубины)
15. [OrderIntent: Намерение разместить ордер](#15-orderintent-намерение-разместить-ордер)
16. [DomWindow: Сборка UI](#16-domwindow-сборка-ui)
17. [DomHeader: Шапка с настройками](#17-domheader-шапка-с-настройками)
18. [DomContent и DomSection: Отображение стакана](#18-domcontent-и-domsection-отображение-стакана)
19. [LevelRow: Одна строка стакана](#19-levelrow-одна-строка-стакана)
20. [OrderPlacementPanel: Панель размещения ордеров](#20-orderplacementpanel-панель-размещения-ордеров)
21. [Автоматический scroll-to-best-price](#21-автоматический-scroll-to-best-price)
22. [Утилиты форматирования](#22-утилиты-форматирования)
23. [Заключение: Как всё работает вместе](#23-заключение-как-всё-работает-вместе)
24. [Приложение: Глоссарий](#24-приложение-глоссарий)

---

# 1. Введение: Что такое DOM (Depth of Market)

## 1.1. Контекст и назначение

**DOM** (Depth of Market) или **стакан заявок** — это таблица всех активных ордеров на покупку (bid) и продажу (ask) для конкретного торгового инструмента. Каждая строка показывает цену и объём заявок на этой цене.

Визуально DOM выглядит так:

```
Bid Vol  │  Price  │  Ask Vol
──────────────────────────────
         │  67050  │  1.234
         │  67049  │  0.567
  2.100  │  67048  │
  1.500  │  67047  │
  0.800  │  67046  │  0.100
```

Где:
- **Bid (BID)** — заявки на покупку (слева, синий/зелёный цвет)
- **Ask (ASK)** — заявки на продажу (справа, красный цвет)
- **Цена** — посередине
- **Спред (Spread)** — разница между лучшим bid и лучшим ask

## 1.2. Что делает модуль feature-dom

Модуль отображает стакан заявок в реальном времени с возможностью:

- Просмотра bid/ask уровней с объёмами
- Визуализации объёмов (горизонтальные бары)
- Агрегации уровней (группировка по шагам цены)
- Выбора провайдера данных (Binance Spot, Coin-M Futures, Bybit, Kraken)
- Выбора торговой пары
- Настройки глубины стакана (20-1000 уровней)
- Клика по цене для выбора
- Размещения рыночных/лимитных ордеров
- Автоматического скролла к лучшей цене
- Локального выключения/включения торговли

## 1.3. Архитектура: Поток данных

```
Binance WebSocket (REST Snapshot + WS @depth + WS @bookTicker)
       │
       ▼
DomAdapter (в binance-provider)
       │
       ▼
DomRepositoryImpl (синхронизация снапшота и инкрементов)
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

## 1.4. Структура файлов модуля

```
features/feature-dom/
├── build.gradle.kts
└── src/
    └── commonMain/
        └── kotlin/com/aandios/nous/feature/dom/
            ├── data/repository/
            │   └── DomRepositoryImpl.kt        # Синхронизация стакана
            ├── di/
            │   └── FeatureDomModule.kt          # Koin DI
            ├── domain/
            │   ├── DomAggregator.kt             # Агрегация уровней
            │   ├── DomOptions.kt                # Единый стейт настроек
            │   ├── OrderBook.kt                 # Модель стакана
            │   ├── TradingProvider.kt           # Провайдеры (Binance, Bybit...)
            │   ├── TradingSymbol.kt             # Торговые пары
            │   └── model/
            │       ├── AggregationLevel.kt      # Уровни агрегации (1×, 10×, 100×)
            │       ├── DepthLimit.kt            # Глубина стакана
            │       └── OrderIntent.kt           # Намерение ордера
            └── ui/
                ├── DomUtils.kt                  # Утилиты
                ├── DomViewModel.kt              # ViewModel
                ├── DomWindow.kt                 # Точка входа + сборка UI
                ├── content/
                │   ├── DomContent.kt            # Контент стакана
                │   ├── DomSection.kt            # Секция с LazyColumn
                │   └── LevelRow.kt              # Одна строка DOM
                ├── footer/
                │   └── OrderPlacementPanel.kt   # Панель ордеров
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

# 2. Архитектура модуля

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

### 2.1.1. Зависимость от `:composeApp`

В отличие от `feature-chart`, этот модуль зависит от `:composeApp`. Это legacy — `composeApp` содержит общие команды (TradingCommand, BuyMarketCommand и т.д.), которые используются в панели ордеров.

**Для Junior**: `project(":composeApp")` — это ссылка на другой модуль в том же многомодульном (multimodule) Gradle проекте. Мы можем использовать классы из `composeApp` как если бы они были в нашем модуле.

---

# 3. Точка входа: DomWindow и main()

## 3.1. Функция main()

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

**Ключевые отличия от ChartWindow**:
- Размер окна: 300×800 вместо 800×600 (DOM — узкий высокий)
- Использует `initKoinForPreview()` из `FeatureDomModule`
- title = "DOM Preview"

## 3.2. Функция DomWindow()

```kotlin
@Composable
fun DomWindow() {
    val domViewModel: DomViewModel = koinInject()
    val domOptions by domViewModel.domOptions.collectAsState()
    val orderQuantity by domViewModel.orderQuantity.collectAsState()
    val isTradingEnabled by domViewModel.isTradingEnabled.collectAsState()
    val symbolTickSize by domViewModel.symbolTickSize.collectAsState()
    val selectedPrice by domViewModel.selectedPrice.collectAsState()

    // SnapshotStateMap — читается напрямую
    val incrementalBids = domViewModel.incrementalBids
    val incrementalAsks = domViewModel.incrementalAsks

    val incrementalBestBid by domViewModel.incrementalBestBid.collectAsState()
    val incrementalBestAsk by domViewModel.incrementalBestAsk.collectAsState()
    // ...

    // Вычисляем отображаемый unified order book с агрегацией
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

### 3.2.1. Компоновка экрана

```
┌──────────────────────┐
│     DomHeader         │  ← Провайдер, символ, глубина, агрегация
├──────────────────────┤
│                      │
│     DomContent        │  ← LazyColumn с уровнями стакана
│     (weight=1f)      │
│                      │
├──────────────────────┤
│  OrderPlacementPanel  │  ← Кнопки ордеров, Qty, Trade Off
│  (height=180.dp)     │
└──────────────────────┘
```

---

# 4. Dependency Injection: Как Koin собирает DOM

## 4.1. Модуль `featureDomModule`

```kotlin
val featureDomModule = module {
    // 1. Конфигурация провайдера
    single<ProviderConfig> { ProviderConfig(apiKey = null, secretKey = null, ...) }

    // 2. Создаём Provider через фабрику
    single<Provider> {
        val config = get<ProviderConfig>()
        val networkManager = get<NetworkManager>()
        BinanceProviderFactory().createProvider(config, networkManager)
    }

    // 3. Адаптеры из провайдера
    single<DomAdapter> { get<Provider>().dom ?: error("DOM adapter not available") }
    single<BookTickerAdapter> { get<Provider>().bookTicker ?: error("BookTicker adapter not available") }
    single<SymbolInfoAdapter> { get<Provider>().symbolInfo ?: error("SymbolInfo adapter not available") }

    // 4. Репозитории
    single<DomRepository> { DomRepositoryImpl(domAdapter = get(), bookTickerAdapter = get()) }
    single<BookTickerRepository> { BookTickerRepositoryImpl(bookTicker = get()) }
    single<SymbolInfoRepository> { SymbolInfoRepositoryImpl(symbolInfoAdapter = get()) }

    // 5. ViewModel
    factory { DomViewModel(domRepository = get(), symbolInfoRepository = get()) }
}
```

### 4.1.1. Три адаптера

В отличие от feature-chart (только ChartAdapter), DOM использует три адаптера:

1. **`DomAdapter`** — для depth stream (снапшоты + инкрементальные обновления)
2. **`BookTickerAdapter`** — для best bid/ask (лучшие цены)
3. **`SymbolInfoAdapter`** — для информации о символе (tickSize)

### 4.1.2. `initKoinForPreview()`

```kotlin
fun initKoinForPreview() {
    stopKoin()
    startKoin {
        modules(coreModule, featureDomModule)
    }
}
```

Останавливает старый Koin и запускает новый только с core + DOM модулями.

---

# 5. DomViewModel: Сердце управления данными

## 5.1. Конструктор и Dispatcher

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

### 5.1.1. Отдельный thread pool

```kotlin
Executors.newSingleThreadExecutor().asCoroutineDispatcher()
```

ViewModel использует **однопоточный executor** вместо `Dispatchers.Main`. Это сделано для того, чтобы вся обработка DOM-событий (парсинг, обновление SnapshotStateMap) происходила в выделенном фоновом потоке, не блокируя UI.

### 5.1.2. `coroutineDispatcher` как параметр

```kotlin
private val coroutineDispatcher: CoroutineDispatcher? = null
```

Параметр для **тестирования** — в тестах можно передать `Dispatchers.Unconfined` или `TestDispatcher`.

## 5.2. Состояния (StateFlows)

```kotlin
private val _domOptions = MutableStateFlow(DomOptions.default())
val domOptions: StateFlow<DomOptions> = _domOptions.asStateFlow()

private val _selectedPrice = MutableStateFlow<Double?>(null)
private val _orderQuantity = MutableStateFlow("0.01")
private val _isTradingEnabled = MutableStateFlow(true)
private val _lastCommandResult = MutableStateFlow<CommandResult?>(null)
private val _symbolTickSize = MutableStateFlow<Double?>(null)
```

### 5.2.1. Загрузка tickSize

```kotlin
init {
    viewModelScope.launch {
        delay(500) // небольшая задержка, чтобы не блокировать старт
        val defaultSymbol = _domOptions.value.symbol.symbol
        fetchSymbolTickSize(defaultSymbol)
    }
    restartSubscription(_domOptions.value)
}
```

При инициализации ViewModel:
1. Загружает tickSize (шаг цены) для дефолтного символа
2. Запускает WebSocket-подписку

---

# 6. Инкрементальные данные: SnapshotStateMap

## 6.1. Проблема

Стакан заявок обновляется очень часто — сотни обновлений в секунду. Если каждый раз копировать всю карту уровней, это вызовет:
- Много аллокаций (мусора для GC)
- Задержки в UI
- Лишние рекомпозиции

## 6.2. Решение: SnapshotStateMap

```kotlin
private val _incrementalBids = mutableStateMapOf<Double, Double>()
val incrementalBids: Map<Double, Double> = _incrementalBids

private val _incrementalAsks = mutableStateMapOf<Double, Double>()
val incrementalAsks: Map<Double, Double> = _incrementalAsks
```

### 6.2.1. Что такое SnapshotStateMap?

`mutableStateMapOf()` создаёт мутабельную мапу, за которой Compose "следит". 

**Ключевая особенность**: Compose способен отслеживать **изменения отдельных entry** (ключ-значение), а не всей мапы. Если изменить один элемент, Compose перерисует только те Composable'ы, которые читают этот конкретный ключ.

### 6.2.2. Преимущества

```kotlin
// ❌ Без SnapshotStateMap — копируем всю мапу
private val _bids = MutableStateFlow<Map<Double, Double>>(emptyMap())
_bids.value = _bids.value + (price to newQty) // O(N) копия всей мапы!

// ✅ С SnapshotStateMap — in-place мутация
_incrementalBids[price] = newQty // O(1), без копий
```

### 6.2.3. Сравнение с StateFlow

| | StateFlow<Map<>> | SnapshotStateMap |
|---|---|---|
| Мутация | Новая копия мапы | In-place |
| Рекомпозиция | Весь список | Только entry |
| GC pressure | Высокий | Низкий |
| Сложность | Проще | Требует `derivedStateOf` |

## 6.3. Как читается в UI

```kotlin
// В DomWindow.kt — напрямую, через ссылку на Map
val incrementalBids = domViewModel.incrementalBids  // Выдаёт Map<Double, Double>
val incrementalAsks = domViewModel.incrementalAsks

// А StateFlow требует collectAsState()
val incrementalBestBid by domViewModel.incrementalBestBid.collectAsState()
```

**Почему так?** `SnapshotStateMap` — это compose-стейт. Compose отслеживает его чтение автоматически. Не нужно `collectAsState()`.

## 6.4. Best prices отдельно

```kotlin
private val _incrementalBestBid = MutableStateFlow<Double?>(null)
private val _incrementalBestAsk = MutableStateFlow<Double?>(null)
private val _incrementalBestBidQuantity = MutableStateFlow<Double?>(null)
private val _incrementalBestAskQuantity = MutableStateFlow<Double?>(null)
```

Лучшие цены (best bid/ask) хранятся **не в картах**, а в отдельных StateFlow. Это данные из отдельного WebSocket-потока (`@bookTicker`), а не из depth.

---

# 7. Обработка событий DomEvent

## 7.1. Подписка на события

```kotlin
private fun restartSubscription(options: DomOptions) {
    subscriptionJob?.cancel()
    subscriptionJob = viewModelScope.launch {
        subscribeToIncrementalDom(options)
    }
}

private suspend fun subscribeToIncrementalDom(options: DomOptions) {
    // Сброс данных
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

### 7.1.1. Переподписка при изменении настроек

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
        // Если изменился символ — обновляем tickSize
        if (oldOptions.symbol != newOptions.symbol) {
            fetchSymbolTickSize(newOptions.symbol.symbol)
        }
    }
}
```

Только изменение provider, symbol или depth вызывает переподписку. Изменение агрегации — нет (агрегация применяется локально на уровне UI).

## 7.2. processDomEvent()

```kotlin
private fun processDomEvent(event: DomEvent) {
    when (event) {
        is DomEvent.Snapshot -> {
            _incrementalBids.clear()
            _incrementalAsks.clear()

            event.snapshot.bids.forEach { (priceStr, qtyStr) ->
                val price = priceStr.toDoubleOrNull()
                val quantity = qtyStr.toDoubleOrNull()
                if (price != null && quantity != null && quantity > 0.0) {
                    _incrementalBids[price] = quantity
                }
            }

            event.snapshot.asks.forEach { (priceStr, qtyStr) ->
                val price = priceStr.toDoubleOrNull()
                val quantity = qtyStr.toDoubleOrNull()
                if (price != null && quantity != null && quantity > 0.0) {
                    _incrementalAsks[price] = quantity
                }
            }
        }

        is DomEvent.UpdateBid -> {
            if (event.quantity == 0.0) _incrementalBids.remove(event.price)
            else _incrementalBids[event.price] = event.quantity
        }

        is DomEvent.UpdateAsk -> {
            if (event.quantity == 0.0) _incrementalAsks.remove(event.price)
            else _incrementalAsks[event.price] = event.quantity
        }

        is DomEvent.BestPrices -> {
            _incrementalBestBid.value = event.bestBid
            _incrementalBestAsk.value = event.bestAsk
            _incrementalBestBidQuantity.value = event.bestBidQuantity
            _incrementalBestAskQuantity.value = event.bestAskQuantity
        }

        DomEvent.Reset -> {
            _incrementalBids.clear()
            _incrementalAsks.clear()
            _incrementalBestBid.value = null
            _incrementalBestAsk.value = null
            _incrementalBestBidQuantity.value = null
            _incrementalBestAskQuantity.value = null
        }
    }
}
```

### 7.2.1. Типы событий

| Событие | Источник | Описание |
|---|---|---|
| `Snapshot` | REST API | Полный слепок стакана (начальная загрузка) |
| `UpdateBid` | WebSocket | Изменение объёма на конкретной цене покупки |
| `UpdateAsk` | WebSocket | Изменение объёма на конкретной цене продажи |
| `BestPrices` | WebSocket (@bookTicker) | Лучшие bid/ask цены |
| `Reset` | Repository | Сбой синхронизации, требуется переинициализация |

### 7.2.2. SnapshotStateMap.clear()

```kotlin
_incrementalBids.clear()
```

`clear()` на SnapshotStateMap — атомарная операция. Compose увидит изменения всех entry как одно изменение и перерисует UI один раз.

## 7.3. Команды (TradingCommand)

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

Sealed class `OrderIntent` конвертируется в `TradingCommand` (из composeApp). Это разделение: UI знает только об `OrderIntent`, а ViewModel создаёт команду.

---

# 8. DomRepositoryImpl: Синхронизация стакана

Это самый сложный файл модуля. Он реализует протокол синхронизации Binance WebSocket.

## 8.1. Протокол Binance Depth Stream

Binance использует следующий протокол для синхронизации стакана:

```
1. Открыть WebSocket @depth стрим — буферизировать все события в очередь
2. Получить снапшот через REST API
3. Отбросить события где u < lastUpdateId
4. Первое обработанное: U <= lastUpdateId+1 AND u >= lastUpdateId+1
5. Каждое следующее: pu == предыдущее u
6. Если pu != previous u — сброс, повтор с шага 1
```

Где:
- `lastUpdateId` — ID последнего обновления в снапшоте
- `U` — first update ID в событии
- `u` — final update ID в событии
- `pu` — previous update ID (только в stream)

## 8.2. Реализация в DomRepositoryImpl

```kotlin
override suspend fun subscribeToDomEvents(symbol: String, depth: Int): Flow<DomEvent> = callbackFlow {
    var reconnectAttempts = 0
    val maxReconnectAttempts = 5

    while (true) {
        try {
            val state = OrderBookState()

            // Шаг 1: Запускаем depth WebSocket и буферизируем события
            val depthJob = launch {
                domAdapter.subscribeToDepthUpdates(symbol, depth)
                    .catch { e -> println("⚠️ Depth updates error: ${e.message}") }
                    .collect { depthUpdate ->
                        state.bufferEvent(depthUpdate)
                    }
            }

            // Даём время WebSocket подключиться
            delay(500)

            // Шаг 2: Получаем снапшот через REST
            val snapshot = domAdapter.getOrderBookSnapshot(symbol, depth)
            state.updateFromSnapshot(snapshot)

            // Отправляем Snapshot
            trySend(DomEvent.fromSnapshot(snapshot, symbol))

            // Шаг 3: Применяем буферизированные события
            if (!state.flushPendingEvents()) {
                depthJob.cancel()
                trySend(DomEvent.Reset)
                continue
            }

            // Шаг 4+5: Продолжаем слушать depth и bookTicker
            val bookTickerJob = launch {
                bookTickerAdapter.subscribeToBookTicker(symbol)
                    .collect { bookTicker ->
                        trySend(DomEvent.fromBookTicker(bookTicker, symbol))
                    }
            }

            // Переключаем depth на прямую валидацию
            depthJob.cancel()
            val depthDirectJob = launch {
                domAdapter.subscribeToDepthUpdates(symbol, depth)
                    .collect { depthUpdate ->
                        if (!state.applyUpdateWithValidation(depthUpdate)) {
                            trySend(DomEvent.Reset)
                            reinitRequested = true
                            return@collect
                        }
                        DomEvent.emitDepthUpdates(depthUpdate, symbol) { event ->
                            trySend(event)
                        }
                    }
            }

            bookTickerJob.join()
            depthDirectJob.join()
            // ...
        }
    }
}
```

### 8.2.1. `callbackFlow` — ручной Flow

```kotlin
callbackFlow {
    // ...
    trySend(event)  // ← отправляем событие в Flow
    close()         // ← закрываем Flow
}
```

`callbackFlow` — это builder для Flow, который позволяет отправлять события вручную через `trySend()`. Используется для интеграции callback-based API (WebSocket).

### 8.2.2. Reconnection с exponential backoff

```kotlin
catch (e: Exception) {
    reconnectAttempts++
    if (reconnectAttempts > maxReconnectAttempts) {
        close(e)
        break
    }
    val delayMs = (1000 * 2.0.pow(reconnectAttempts - 1.0)).toLong()
    delay(delayMs)
    trySend(DomEvent.Reset)
}
```

При ошибке:
1. Увеличиваем счётчик попыток
2. Если больше 5 — закрываем Flow
3. Иначе: `delay(1000 * 2^(attempt-1))` — 1с, 2с, 4с, 8с, 16с (exponential backoff)
4. Отправляем `DomEvent.Reset`

### 8.2.3. Custom exception для переинициализации

```kotlin
private class ReinitializationException(message: String) : Exception(message)
```

Приватное исключение для управления потоком — когда синхронизация сбивается, `continue` перезапускает цикл.

---

# 9. OrderBook: Модель стакана

## 9.1. Класс OrderBook

```kotlin
data class OrderBook(
    val symbol: String,
    val levels: List<OrderBookLevel>,
    val timestamp: Long,
    val bestBid: Double?,
    val bestAsk: Double?,
    val spread: Double?,
    val spreadPercent: Double?
) {
    fun maxVolume(): Double {
        return levels.maxOfOrNull { level ->
            maxOf(
                level.bidQty.toDoubleOrNull() ?: 0.0,
                level.askQty.toDoubleOrNull() ?: 0.0
            )
        } ?: 1.0
    }

    fun aggregate(aggregationLevel: AggregationLevel, baseTickSize: Double): OrderBook {
        // ...
    }
}
```

### 9.1.1. maxVolume()

Находит максимальный объём среди всех уровней для масштабирования визуализации (ширины горизонтальных баров).

### 9.1.2. aggregate()

```kotlin
fun aggregate(aggregationLevel: AggregationLevel, baseTickSize: Double): OrderBook {
    val aggregatedLevels = DomAggregator.aggregateUnifiedLevels(
        levels, aggregationLevel, baseTickSize
    )
    val aggregatedBestBid = bestBid?.let {
        aggregationLevel.roundDown(it, baseTickSize)
    }
    val aggregatedBestAsk = bestAsk?.let {
        aggregationLevel.roundDown(it, baseTickSize)
    }
    // ...
    return copy(levels = aggregatedLevels, bestBid = aggregatedBestBid, ...)
}
```

Создаёт новый `OrderBook` с агрегированными уровнями. Исходный объект не меняется (data class — immutable).

---

# 10. DomAggregator: Агрегация ценовых уровней

## 10.1. Зачем нужна агрегация?

Когда цены имеют маленький шаг (tickSize = 0.01), стакан содержит сотни уровней. Агрегация группирует их:

```
Без агрегации:         С агрегацией (10×):
67000.01  0.5          
67000.02  0.3          
67000.03  1.2          67000.0  1.5
67000.04  0.8          
67000.05  0.2          
67000.06  0.9          67000.1  0.9
```

## 10.2. Single-pass агрегация

```kotlin
object DomAggregator {
    fun aggregateLevels(
        levels: List<OrderBookLevel>,
        aggregationLevel: AggregationLevel,
        baseTickSize: Double
    ): List<OrderBookLevel> {
        val aggregated = linkedMapOf<String, AggregatedBucket>()
        
        for (level in levels) {
            val key = aggregationLevel.aggregationKey(level.price, baseTickSize)
            val bucket = aggregated.getOrPut(key) { AggregatedBucket() }
            bucket.totalQty += level.quantity.toDoubleOrNull() ?: 0.0
            bucket.totalBidQty += level.bidQty.toDoubleOrNull() ?: 0.0
            bucket.totalAskQty += level.askQty.toDoubleOrNull() ?: 0.0
        }

        return aggregated.map { (aggregatedPrice, bucket) ->
            OrderBookLevel(price = aggregatedPrice, quantity = bucket.totalQty.toString(), ...)
        }.sortedBy { it.price.toDoubleOrNull() ?: 0.0 }
    }
}
```

### 10.2.1. `linkedMapOf` — сохранение порядка

`LinkedHashMap` сохраняет порядок вставки. Это важно, потому что исходный список отсортирован, и мы хотим сохранить сортировку в агрегированном результате.

### 10.2.2. `getOrPut` — паттерн "get or create"

```kotlin
val bucket = aggregated.getOrPut(key) { AggregatedBucket() }
```

Эквивалентно:
```kotlin
val bucket = aggregated[key]
if (bucket == null) {
    val newBucket = AggregatedBucket()
    aggregated[key] = newBucket
    newBucket
} else {
    bucket
}
```

### 10.2.3. Внутренние классы

```kotlin
private class AggregatedBucket {
    var totalQty: Double = 0.0
    var totalBidQty: Double = 0.0
    var totalAskQty: Double = 0.0
}
```

Мутабельные классы для аккумуляции — без них пришлось бы создавать новый объект для каждого уровня.

---

# 11. AggregationLevel: Уровни агрегации

## 11.1. Sealed class

```kotlin
sealed class AggregationLevel(val multiplier: Double) {
    object BaseTick : AggregationLevel(1.0)      // 1× — без агрегации
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

### 11.1.1. Sealed class vs sealed interface

Используется `sealed class`, а не `sealed interface`, потому что у всех наследников есть общее поле `multiplier`. sealed class позволяет хранить состояние в родителе.

## 11.2. Ключевые методы

**effectiveTickSize**: `baseTickSize * multiplier`

```kotlin
fun effectiveTickSize(baseTickSize: Double): Double = baseTickSize * multiplier
```

Пример: tickSize=0.01, TenTick → 0.01 * 10 = 0.1

**roundDown**: округление цены вниз

```kotlin
fun roundDown(price: Double, baseTickSize: Double): Double {
    val tick = effectiveTickSize(baseTickSize)
    if (tick <= 0.0) return price
    return (price / tick).toInt() * tick
}
```

Пример: price=67000.05, tick=0.1 → (67000.05/0.1).toInt()*0.1 = 67000.0

**aggregationKey**: строковый ключ для группировки

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

Используется в `DomAggregator` для группировки уровней.

---

# 12. DomOptions: Единый стейт настроек

## 12.1. Класс

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

### 12.1.1. Зачем единый стейт?

Все настройки DOM хранятся в одном `StateFlow`. Это упрощает:
- Отслеживание изменений (один `collectAsState()` вместо пяти)
- Валидацию переходов
- Сохранение/восстановление

### 12.1.2. subscriptionKey

```kotlin
val subscriptionKey: String get() = "${provider.name}:${symbol.symbol}:${depth.value}"
```

Вычисляемый ключ для определения необходимости переподписки. Если провайдер, символ или глубина не изменились — подписка не перезапускается.

---

# 13. TradingProvider и TradingSymbol

## 13.1. TradingProvider — enum провайдеров

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

### 13.1.1. Параметры enum

Каждый enum может иметь свойства:
```kotlin
BINANCE("Binance", true)
//        ↑ displayName   ↑ supportsFutures
```

## 13.2. TradingSymbol — data class пары

```kotlin
data class TradingSymbol(
    val symbol: String,       // "BTCUSD_PERP"
    val displayName: String,  // "BTCUSD Perp"
    val provider: TradingProvider
)
```

### 13.2.1. Статические списки символов

Каждый провайдер имеет предопределённый список символов:
```kotlin
val BINANCE_COIN_M_FUTURES_SYMBOLS = listOf(
    TradingSymbol("BTCUSD_PERP", "BTCUSD Perp", TradingProvider.BINANCE_COIN_M),
    TradingSymbol("ETHUSD_PERP", "ETHUSD Perp", TradingProvider.BINANCE_COIN_M),
    // ...
)
```

### 13.2.2. Поиск по провайдеру

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

# 14. DepthLimit: Ограничение глубины

## 14.1. Класс

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

### 14.1.1. `init` блок — валидация

```kotlin
init {
    require(value in MIN_VALUE..MAX_VALUE) { ... }
}
```

`require` выбрасывает `IllegalArgumentException` если условие не выполнено. Это защита от некорректных значений.

### 14.1.2. `create()` vs конструктор

- `DepthLimit(50)` — может выбросить исключение
- `DepthLimit.create(50)` — безопасно зажмёт в диапазон

---

# 15. OrderIntent: Намерение разместить ордер

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

### 15.1.1. Почему `sealed class`, а не `sealed interface`?

`OrderIntent` использует `sealed class`, так как есть метод `toOrderData()` с общей логикой конвертации. Но sealed class тоже подходит — разницы в функциональности нет.

### 15.1.2. `toOrderData()`

```kotlin
fun toOrderData(): OrderData? = when (this) {
    is MarketBuy -> OrderData(symbol, OrderSide.BUY, OrderType.MARKET, quantity = quantity)
    is LimitBuy -> OrderData(symbol, OrderSide.BUY, OrderType.LIMIT, price = price, quantity = quantity)
    ToggleTrading -> null
    // ...
}
```

Конвертирует намерение в `OrderData` из `composeApp` для выполнения через `TradingCommand`.

---

# 16. DomWindow: Сборка UI

## 16.1. Оптимизация: derivedStateOf

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

### 16.1.1. Что такое derivedStateOf?

`derivedStateOf` — функция Compose, создающая **производное состояние**, которое пересчитывается только когда изменяются прочитанные им стейты.

**Ключевая особенность:** `remember` с ключами определяет, когда пересоздавать `derivedStateOf`, но сам `derivedStateOf` внутри "лениво" реагирует на изменения прочитанных стейтов.

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

    // Добавляем bids, фильтруя по bestBid
    bids.forEach { (price, quantity) ->
        if (bestBid != null && price > bestBid) return@forEach
        priceMap[price.toString()] = OrderBookLevel(
            price = price.toString(), quantity = quantity.toString(),
            bidQty = quantity.toString(), askQty = ""
        )
    }

    // Добавляем asks, фильтруя по bestAsk
    asks.forEach { (price, quantity) ->
        if (bestAsk != null && price < bestAsk) return@forEach
        // объединяем с существующим bid-уровнем
    }

    // Сортируем по убыванию цены
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

### 16.2.1. Фильтрация по bestBid/bestAsk

```kotlin
if (bestBid != null && price > bestBid) return@forEach
```

Это важно: отображаются только уровни **ниже** лучшего bid и **выше** лучшего ask. Всё, что "пересекает" спред, игнорируется.

### 16.2.2. Unification bid/ask

Каждый уровень может содержать и bid, и ask объём для одной цены. Это называется **unified order book**:

```
Price    Bid Qty    Ask Qty
67000.0  1.500
67000.1  0.800      0.300  ← одна цена, оба объёма
67000.2             1.200
```

## 16.3. Сборка BookTicker

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

Собирается из инкрементальных данных, без отдельного репозитория.

---

# 17. DomHeader: Шапка с настройками

## 17.1. Структура

```
┌──────────────────────────────┐
│ [Provider ▼]      ● LIVE  ▲ │
│                              │
│ [Symbol ▼]  [Depth Limit ▼]  │
│                              │
│ [Aggregation ▼]              │
└──────────────────────────────┘
```

### 17.1.1. Два режима

```kotlin
@Composable
fun DomHeader(...) {
    if (domOptions.collapsed) {
        DomHeaderCompact(...)  // Только provider + symbol
    } else {
        ExpandedDomHeader(...) // Все настройки
    }
}
```

`collapsed` — булево поле в `DomOptions`. Позволяет свернуть шапку, освобождая место для стакана.

### 17.1.2. ExpandedDomHeader

Три строки:
1. **Provider + Live indicator + Collapse button**
2. **Symbol + Depth Limit**
3. **Aggregation Level**

## 17.2. Компактный режим (DomHeaderCompact)

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
    // Только provider имя + символ
    Row {
        Text(tradingProvider.displayName)
        Text(tradingSymbol.displayName)
        IconButton(onClick = onToggleExpand) {
            Icon(Icons.Default.ArrowDropDown, ...) // стрелка вниз (развернуть)
        }
    }
}
```

---

# 18. DomContent и DomSection: Отображение стакана

## 18.1. DomContent — точка входа для контента

```kotlin
@Composable
fun DomContent(
    orderBook: OrderBook,
    aggregationLevel: AggregationLevel = AggregationLevel.BaseTick,
    baseTickSize: Double? = null,
    selectedPrice: Double? = null,
    onPriceSelected: (Double?) -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxSize()) {
        DomSection(
            orderBook = orderBook,
            selectedPrice = selectedPrice,
            onPriceSelected = { price -> onPriceSelected(price) },
            aggregationLevel = aggregationLevel,
            baseTickSize = baseTickSize,
            modifier = Modifier.weight(1f)
        )
    }
}
```

Простая обёртка над `DomSection`. Может быть расширена в будущем (например, для добавления графика поверх DOM).

## 18.2. DomSection — LazyColumn со стаканом

### 18.2.1. Заголовок

```kotlin
Row {
    Text("Bid Vol", modifier = Modifier.weight(0.8f))
    Text("Price", modifier = Modifier.weight(0.6f))
    Text("Ask Vol", modifier = Modifier.weight(0.8f))
}
```

Заголовок колонок: Bid Vol / Price / Ask Vol.

### 18.2.2. LazyColumn с LevelRow

```kotlin
LazyColumn(state = lazyListState, modifier = Modifier.weight(1f)) {
    items(
        items = levels,
        key = { "level-${it.price}" }
    ) { level ->
        LevelRow(
            level = level,
            maxVolume = maxVolume,
            selectedPrice = selectedPrice,
            bestBid = aggregatedBestBid,
            bestAsk = aggregatedBestAsk,
            aggregationLevel = aggregationLevel,
            baseTickSize = baseTickSize,
            onPriceClick = onPriceSelected
        )
    }
}
```

### 18.2.3. `key` для items

```kotlin
key = { "level-${it.price}" }
```

Ключи помогают LazyColumn эффективно переиспользовать элементы при обновлении данных. Без ключей весь список перерисовывался бы при любом изменении.

---

# 19. LevelRow: Одна строка стакана

## 19.1. Визуальная структура строки

```
┌────────────────────────────────────────────┐
│ ████ 1.500  │ 67000.0 │  0.300 ████       │
│    bid vol    price        ask vol          │
└────────────────────────────────────────────┘
```

## 19.2. Компонент

```kotlin
@Composable
fun LevelRow(
    level: OrderBookLevel,
    maxVolume: Double,
    selectedPrice: Double?,
    bestBid: Double?,
    bestAsk: Double?,
    aggregationLevel: AggregationLevel,
    baseTickSize: Double? = null,
    onPriceClick: (Double) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val price = level.price.toDoubleOrNull() ?: return
    val bidQty = level.bidQty.toDoubleOrNull() ?: 0.0
    val askQty = level.askQty.toDoubleOrNull() ?: 0.0

    // Определяем: это лучшая цена? выбранная цена? ховер?
    val isBestBid = bestBid?.let { comparePrices(it, price) } ?: false
    val isBestAsk = bestAsk?.let { comparePrices(it, price) } ?: false
    val isSelected = selectedPrice?.let { comparePrices(it, price) } ?: false

    // Цвета
    val backgroundColor = when {
        isSelected -> Color.Yellow.copy(alpha = 0.3f)
        isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    Row(modifier = Modifier
        .fillMaxWidth()
        .hoverable(interactionSource)
        .clickable(interactionSource = interactionSource, indication = null) {
            onPriceClick(price)
        }
        .background(backgroundColor)
        .border(if (isBestPrice) 1.dp else 0.dp, borderColor)
        .padding(horizontal = 8.dp, vertical = 1.dp)
    ) {
        // Bid Volume (слева) — горизонтальный бар
        Box(Modifier.weight(0.8f).height(20.dp)) {
            if (bidQty > 0) {
                val volumeWidth = (bidQty / maxVolume).coerceIn(0.0, 1.0)
                Box(Modifier.fillMaxHeight()
                    .fillMaxWidth(volumeWidth.toFloat())
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)))
            }
            if (bidQty > 0) {
                Text(formatVolume(bidQty), ...)  // ← текст поверх бара
            }
        }

        // Price (центр)
        Text(formatPrice(price), ...)

        // Ask Volume (справа) — горизонтальный бар
        Box(Modifier.weight(0.8f).height(20.dp).align(CenterEnd)) {
            if (askQty > 0) {
                val volumeWidth = (askQty / maxVolume).coerceIn(0.0, 1.0)
                Box(Modifier.fillMaxHeight()
                    .fillMaxWidth(volumeWidth.toFloat())
                    .align(Alignment.CenterEnd)
                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f)))
            }
            if (askQty > 0) {
                Text(formatVolume(askQty), ..., align(CenterEnd))
            }
        }
    }
}
```

## 19.3. Визуализация объёмов

### 19.3.1. Bid Volume (слева)

```kotlin
val volumeWidth = (bidQty / maxVolume).coerceIn(0.0, 1.0)
Box(Modifier.fillMaxWidth(volumeWidth.toFloat()).background(bidColor))
```

Горизонтальный бар, ширина которого пропорциональна объёму. `maxVolume` — максимальный объём среди всех уровней (из `OrderBook.maxVolume()`).

### 19.3.2. Ask Volume (справа)

Аналогично, но выровнен по правому краю:
```kotlin
Box(Modifier.fillMaxWidth(volumeWidth.toFloat()).align(Alignment.CenterEnd))
```

### 19.3.3. Подсветка лучших цен

```kotlin
val borderColor = when {
    isBestBid -> MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)    // Синий
    isBestAsk -> MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)  // Красный
    else -> Color.Transparent
}
```

Лучший bid подсвечивается синей границей, лучший ask — красной.

### 19.3.4. Подсветка выбранной цены

```kotlin
val backgroundColor = when {
    isSelected -> Color.Yellow.copy(alpha = 0.3f)  // Жёлтый фон
    isHovered -> surfaceVariant
    else -> Transparent
}
```

Когда пользователь кликает на цену, строка подсвечивается жёлтым.

---

# 20. OrderPlacementPanel: Панель размещения ордеров

## 20.1. Структура панели

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

## 20.2. Market ордера

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

**Рыночный ордер (Market Order)** — исполняется немедленно по текущей рыночной цене.

## 20.3. Limit ордера (по выбранной цене)

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

Кнопка неактивна (текст серый), пока не выбрана цена.

## 20.4. Best Bid/Ask ордера

```kotlin
TerminalButton(onClick = {
    if (bestBidPrice != null && bestBidPrice > 0) {
        onOrderIntent(OrderIntent.BestBidBuy(symbol, bestBidPrice, quantity))
    }
}) {
    Text(if (bestBidPrice != null) "Best Bid" else "Best Bid (waiting...)")
}
```

## 20.5. Trade Off кнопка

```kotlin
TerminalButton(onClick = { onOrderIntent(OrderIntent.ToggleTrading) },
    isActive = !isTradingEnabled  // Активна когда торговля ВЫКЛЮЧЕНА
) {
    Text(if (isTradingEnabled) "⚠️ TRADE OFF" else "✅ TRADE ON",
         color = if (isTradingEnabled) Color.Red else Color.Green)
}
```

Локальный kill-switch — отключает возможность отправлять ордера, не отключая подписку на данные.

---

# 21. Автоматический scroll-to-best-price

## 21.1. Проблема

Лучшая цена bid постоянно меняется. Если она уходит за пределы видимой области, пользователь теряет ориентир.

## 21.2. Решение

```kotlin
val scrollTargetPrice = remember(orderBook, aggregationLevel, baseTickSize) {
    orderBook?.bestBid?.let { bestBid ->
        if (baseTickSize != null) {
            aggregationLevel.roundDown(bestBid, baseTickSize)
        } else {
            bestBid
        }
    }
}

LaunchedEffect(scrollTargetPrice) {
    if (scrollTargetPrice == null) return@LaunchedEffect
    if (lazyListState.isScrollInProgress) return@LaunchedEffect  // ← НЕ мешаем пользователю

    val targetIndex = levels.indexOfFirst { level ->
        val levelPrice = level.price.toDoubleOrNull() ?: return@indexOfFirst false
        // Сравниваем через агрегацию
        aggregationLevel.aggregationKey(levelPrice.toString(), baseTickSize) ==
            aggregationLevel.aggregationKey(scrollTargetPrice.toString(), baseTickSize)
    }.takeIf { it >= 0 } ?: return@LaunchedEffect

    // Проверяем, видна ли уже целевая цена
    val visibleItems = lazyListState.layoutInfo.visibleItemsInfo
    val isTargetVisible = visibleItems.any { visibleItem ->
        val visibleIndex = visibleItem.index
        if (visibleIndex in levels.indices) {
            val levelPrice = levels[visibleIndex].price.toDoubleOrNull() ?: return@any false
            aggregationLevel.aggregationKey(levelPrice.toString(), baseTickSize) ==
                aggregationLevel.aggregationKey(scrollTargetPrice.toString(), baseTickSize)
        } else false
    }

    if (!isTargetVisible) {
        lazyListState.animateScrollToItem(targetIndex, 0)
    }
}
```

### 21.2.1. Ключевые моменты

1. **Только если пользователь НЕ скроллит сам**
   ```kotlin
   if (lazyListState.isScrollInProgress) return@LaunchedEffect
   ```

2. **Не скроллим если цена уже видна**
   ```kotlin
   val isTargetVisible = visibleItems.any { ... }
   if (!isTargetVisible) { animateScrollToItem(...) }
   ```

3. **Сравнение через агрегацию**
   ```kotlin
   aggregationLevel.aggregationKey(price1, baseTickSize) ==
       aggregationLevel.aggregationKey(price2, baseTickSize)
   ```

### 21.2.2. `remember` для scrollTargetPrice

```kotlin
val scrollTargetPrice = remember(orderBook, aggregationLevel, baseTickSize) { ... }
```

Пересчитывается только при изменении стакана, уровня агрегации или tickSize.

---

# 22. Утилиты форматирования

## 22.1. formatPrice (для DomSection)

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

Адаптивная точность в зависимости от цены. Для BTC (~67000) — 2 знака, для мелких альткоинов — до 6 знаков.

## 22.2. formatVolume (для DomSection)

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

## 22.3. formatDomPrice (для OrderPlacementPanel)

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

Аналогична `formatPrice`, но в файле `DomUtils.kt` и используется в футере.

---

# 23. Заключение: Как всё работает вместе

## 23.1. Последовательность запуска

```
1. main() в DomWindow.kt
   │
2. stopKoin() → initKoinForPreview()
   │   Создаёт DI: FeatureDomModule + coreModule
   │
3. Window(...) { DomWindow() }
   │
4. DomWindow():
   │
   ├── koinInject() → DomViewModel
   │   │
   │   └── init():
   │       ├── fetchSymbolTickSize("BTCUSD_PERP")    ←загрузка tickSize
   │       └── restartSubscription(options)           ←запуск WebSocket
   │           │
   │           └── subscribeToIncrementalDom():
   │               │
   │               └── domRepository.subscribeToDomEvents(symbol, depth)
   │                   │
   │                   ├── WebSocket @depth → буфер
   │                   ├── REST snapshot → OrderBookState.updateFromSnapshot()
   │                   ├── flush буфера → валидация
   │                   ├── WebSocket @depth (direct) → applyUpdateWithValidation()
   │                   └── WebSocket @bookTicker → BestPrices
   │
   ├── collectAsState() → domOptions, selectedPrice, symbolTickSize, etc.
   │
   ├── derivedStateOf → buildDisplayOrderBook()
   │   │
   │   └── Сливает incrementalBids + incrementalAsks → OrderBook
   │       └── Применяет aggregation → агрегированный OrderBook
   │
   └── Column:
       ├── DomHeader (выбор провайдера, символа, глубины, агрегации)
       ├── DomSection (LazyColumn с LevelRow для каждого уровня)
       └── OrderPlacementPanel (кнопки ордеров)
```

## 23.2. Цикл обновления данных

```
WebSocket @depth event
    │
    ▼
DomAdapter → DomRepositoryImpl (валидация) → callbackFlow
    │
    ▼ trySend(DomEvent.UpdateBid)
DomViewModel.processDomEvent()
    │
    ├── _incrementalBids[price] = quantity  ← in-place мутация
    │
    ▼ Compose отслеживает изменение Entry в SnapshotStateMap
derivedStateOf { buildDisplayOrderBook(...) }
    │
    ▼ Новый OrderBook
DomSection → LazyColumn recomposition
    │
    ▼ Compose сравнивает ключи и обновляет только изменившиеся строки
LevelRow перерисовка (только для изменившихся уровней)
```

## 23.3. Взаимодействие компонентов при клике

```
Пользователь кликает на LevelRow с ценой 67000.0
    │
    ▼
onPriceClick(67000.0)
    │
    ▼
DomViewModel.selectPrice(67000.0)
    │
    ├── _selectedPrice.value = 67000.0
    │
    ▼ Compose перерисовка
OrderPlacementPanel: кнопки "Buy Limit" и "Sell Limit" активируются
DomContent: LevelRow с ценой 67000.0 подсвечивается жёлтым

Пользователь нажимает "Buy Limit"
    │
    ▼
onOrderIntent(OrderIntent.LimitBuy("BTCUSD_PERP", 67000.0, 0.01))
    │
    ▼
DomViewModel.handleOrderIntent(intent)
    │
    ├── Создаёт BuyLimitCommand
    ├── executeCommand(command)
    │   ├── Проверка isTradingEnabled
    │   ├── Проверка command.canExecute()
    │   └── command.execute() → отправка ордера на биржу
```

## 23.4. Ключевые архитектурные решения

### 23.4.1. SnapshotStateMap для производительности
Вместо копирования карт уровней при каждом обновлении (сотни раз в секунду), DOM использует `mutableStateMapOf()` с in-place мутацией.

### 23.4.2. derivedStateOf вместо collectAsState
`buildDisplayOrderBook` вызывается только когда реально изменились данные, а не при каждой рекомпозиции.

### 23.4.3. Выделенный thread pool
`Executors.newSingleThreadExecutor()` для ViewModel — вся обработка DOM-событий происходит вне UI-потока.

### 23.4.4. Протокол синхронизации Binance
Снапшот + инкрементальные обновления с валидацией. При ошибке — переинициализация с exponential backoff.

### 23.4.5. LazyColumn с ключами
Эффективная перерисовка только изменившихся строк. Ключи `"level-${price}"` помогают Compose понять, какие строки обновлять.

### 23.4.6. Автоматический скролл без конфликтов
Scroll-to-best-price не мешает пользователю (проверка `isScrollInProgress`).

---

# 24. Приложение: Глоссарий

| Термин | Значение |
|---|---|
| **DOM** | Depth of Market — стакан заявок |
| **Bid** | Заявка на покупку |
| **Ask** | Заявка на продажу |
| **Spread** | Разница между лучшим bid и лучшим ask |
| **Order Book** | Книга заявок — таблица всех активных ордеров |
| **Depth** | Количество уровней стакана (глубина) |
| **Snapshot** | Полный слепок стакана на момент времени |
| **Incremental update** | Инкрементальное обновление (изменение одного уровня) |
| **Tick Size** | Минимальный шаг цены инструмента |
| **Level** | Один уровень стакана (цена + объём) |
| **Unified level** | Уровень, содержащий и bid, и ask объём |
| **Aggregation** | Группировка уровней по шагу цены |
| **Best bid** | Самая высокая цена покупки |
| **Best ask** | Самая низкая цена продажи |
| **BookTicker** | Поток лучших цен (best bid/ask) в реальном времени |
| **Market order** | Рыночный ордер — исполняется немедленно |
| **Limit order** | Лимитный ордер — исполняется по указанной цене |
| **Exponential backoff** | Стратегия повторных попыток с увеличивающейся задержкой |
| **SnapshotStateMap** | Compose-отслеживаемая мапа с in-place мутацией |
| **derivedStateOf** | Производное состояние, пересчитываемое лениво |
| **callbackFlow** | Flow builder для callback-based API |
| **LazyColumn** | Виртуализированный список в Compose (переиспользует элементы) |
| **WebSocket** | Двунаправленный протокол реального времени |
| **Reconnection** | Автоматическое переподключение при обрыве связи |
| **REST** | HTTP API для получения снапшота стакана |
| **lastUpdateId** | ID последнего обновления в снапшоте (для синхронизации) |
| **Coin-M Futures** | Фьючерсы с обеспечением в монете (BTC, ETH) |
| **USD-M Futures** | Фьючерсы с обеспечением в USDT/USDC |
| **Spot** | Спотовая торговля (реальная монета) |
