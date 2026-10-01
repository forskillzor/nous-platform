# Появление свечи на графике: сущности и последовательности

> Диаграммы Mermaid по коду ветки `chart-big-refactoring`.
> Отвечает на вопрос «как и где появляется/обновляется свеча» — от WebSocket
> Binance до отрисовки и кэша. Файлы и ключевые функции указаны рядом.

---

## 1. Сущности (кто участвует)

```mermaid
classDiagram
    direction LR

    class ChartAdapter {
        <<interface>>
        +getCandles(symbol, interval, limit) List
        +getCandlesBefore(symbol, interval, endTime, limit) List
        +subscribeToCandles(symbol, interval) Flow
    }

    class BinanceChartAdapter {
        REST_klines
        WS_kline
    }

    class TimeSeriesSource {
        <<interface>>
        +loadInitial() List
        +loadBefore(beforeTimestamp, limit) List
        +liveUpdates() Flow
        +mergeItem(items, update) List
        +timestampOf(item) Long
    }

    class CandleSeriesSource {
        isSameCandleCheck
    }

    class TimeSeriesController {
        +state StateFlow
        +start()
        +loadMore()
        +stop()
        +dispose()
        generation Int
        liveJob Job
    }

    class TimeSeriesState {
        items List
        loading Boolean
        loadingMore Boolean
        hasMore Boolean
        loadCount Int
        loadGeneration Int
        error String
    }

    class ChartRepository {
        <<interface>>
        +candleSource(ticker, timeframe) TimeSeriesSource
    }

    class ChartViewModel {
        +state StateFlow
        +dispatch(intent)
        +dispose()
        -startCandleSeries(ticker, timeframe)
        -scheduleCacheWrite(symbol, timeframe, candles)
        -flushCache()
        -cacheScope CoroutineScope
    }

    class ChartUiState {
        chartState ChartState
        currentSymbol String
        currentTimeframe String
        historyLoadCount Int
        historyGeneration Int
        hasMoreHistory Boolean
    }

    class ChartState {
        <<sealed>>
    }

    class ChartWindowContent {
        collectAsState_uiState
        CandleStickChart
    }

    class CandleStickChartInteraction {
        +TimeScale timeScale
        +PriceScale priceScale
        +ChartSeries series
        LaunchedEffect_candles_size
        LaunchedEffect_historyGeneration
    }

    class TimeScale {
        scrollOffset Float
        zoomLevel Float
        +maxScroll(count, width)
        +scrollToLatest(count, width)
        +isAtLatest(count, width, tolerance)
        +offsetAfterPrepend(added, count, width)
    }

    class PriceScale {
        +fit(provider)
        +range(verticalScroll, height)
    }

    class ChartSeries {
        <<interface>>
        +skeleton List
        +priceRange(start, end) PriceRange
        +draw(scope, canvas)
    }

    class CandlestickSeries
    class FootprintSeries

    class CandleCacheStore {
        <<interface>>
        +saveCandles(exchange, symbol, timeframe, candles)
        +getCandles(exchange, symbol, timeframe, limit)
    }

    class LocalStorage {
        SQLite_candles_cache
    }

    BinanceChartAdapter ..|> ChartAdapter
    CandleSeriesSource ..|> TimeSeriesSource
    ChartRepository ..> CandleSeriesSource : candleSource()
    ChartViewModel --> ChartRepository
    ChartViewModel --> TimeSeriesController : создаётся на каждый load
    TimeSeriesController --> TimeSeriesSource
    TimeSeriesController --> TimeSeriesState
    CandleSeriesSource --> ChartAdapter
    ChartViewModel --> ChartUiState
    ChartUiState --> ChartState
    ChartViewModel --> CandleCacheStore : preload / write / flush
    LocalStorage ..|> CandleCacheStore
    ChartWindowContent --> ChartViewModel : state
    ChartWindowContent --> CandleStickChartInteraction : candles, footprintCandles
    CandleStickChartInteraction --> TimeScale
    CandleStickChartInteraction --> PriceScale
    CandleStickChartInteraction --> ChartSeries
    CandlestickSeries ..|> ChartSeries
    FootprintSeries ..|> ChartSeries
```

`TimeSeriesSource<T>`, `TimeSeriesController<T>`, `TimeSeriesState<T>` —
generic'и; на диаграмме параметр типа опущен для читаемости.

---

## 2. Общий поток: от биржи до пикселя

```mermaid
flowchart LR
    WS["Binance WS kline_1m"] --> AD["BinanceChartAdapter.subscribeToCandles"]
    REST["Binance REST klines"] --> AD2["BinanceChartAdapter.getCandles/getCandlesBefore"]
    AD --> SRC["CandleSeriesSource.liveUpdates"]
    AD2 --> SRC2["CandleSeriesSource.loadInitial/loadBefore"]
    SRC --> CTRL["TimeSeriesController liveJob"]
    SRC2 --> CTRL
    CTRL -->|"mergeItem"| ST["TimeSeriesState.items"]
    ST --> VM["ChartViewModel collector"]
    VM --> UI["ChartUiState.chartState Success"]
    UI --> WC["ChartWindowContent recompose"]
    WC --> ENG["CandleStickChartInteraction"]
    ENG -->|"series.draw"| DRAW["drawChart / drawCandle"]
    VM -.->|"throttled 30s"| CACHE["CandleCacheStore LocalStorage"]
    CACHE -.->|"preload при открытии"| VM
```

---

## 3. Последовательность: холодный старт (история + кэш)

```mermaid
sequenceDiagram
    autonumber
    participant U as Пользователь
    participant W as ChartWindow
    participant VM as ChartViewModel
    participant C as CandleCacheStore
    participant R as ChartRepository
    participant TC as TimeSeriesController
    participant S as CandleSeriesSource
    participant A as BinanceChartAdapter
    participant UI as ChartInteraction

    U->>W: открыть панель графика
    W->>VM: dispatch(LoadChart)
    VM->>VM: loadChart -> startCandleSeries
    VM->>VM: flushCache предыдущего снапшота
    VM->>VM: chartState = Loading
    par preload из кэша
        VM->>C: getCandles(Binance, symbol, tf, 500)
        C-->>VM: cached list
        VM->>VM: если всё ещё Loading -> Success(cached)
        VM-->>UI: показать кэш
    and сеть
        VM->>R: candleSource(ticker, timeframe)
        R-->>VM: CandleSeriesSource
        VM->>TC: TimeSeriesController(source, viewModelScope)
        VM->>TC: start()
        TC->>S: loadInitial()
        S->>A: getCandles(interval, limit=500)
        A-->>S: List Candle ASC
        S-->>TC: initial items
        TC->>TC: items = initial, hasMore = non-empty
        TC->>S: liveUpdates()
        S->>A: subscribeToCandles (WebSocket)
    end
    TC-->>VM: state emission items
    VM->>VM: chartState = Success(items, last.close)
    VM->>VM: scheduleCacheWrite (первая запись без троттла)
    VM-->>UI: recompose
    UI->>UI: PriceScale.fit, Canvas redraw
```

---

## 4. Последовательность: новая realtime-свеча (главный кейс)

```mermaid
sequenceDiagram
    autonumber
    participant A as BinanceChartAdapter
    participant S as CandleSeriesSource
    participant TC as TimeSeriesController
    participant VM as ChartViewModel
    participant UI as ChartInteraction
    participant TS as TimeScale
    participant C as CandleCacheStore

    A-->>S: Candle из WS
    S-->>TC: liveUpdates emit update
    TC->>TC: mergeItem(items, update)

    alt Та же свеча (timestamp в том же бакете)
        TC->>TC: dropLast + update.copy high low close volume
        Note over TC: размер списка НЕ меняется
    else Новая свеча (новый бакет времени)
        TC->>TC: items + update
        Note over TC: размер списка +1
    end

    TC-->>VM: state emission items
    VM->>VM: chartState = Success(items, items.last.close)
    VM-->>UI: recompose candles = items

    alt Размер изменился (новая свеча)
        UI->>TS: LaunchedEffect(candles.size)
        alt historyGeneration == 0 и пользователь у правого края
            UI->>TS: scrollToLatest(count, width)
        end
    end

    UI->>UI: PriceScale.fit series.priceRange(visible)
    UI->>UI: Canvas series.draw -> drawChart -> drawCandle
    VM->>C: scheduleCacheWrite throttle 30s
```

Ключевые места:

- решение «новая свеча или обновление последней» — `CandleSeriesSource.mergeItem`,
  ветка `isSameCandle` (`CandleSeriesSource.kt:35-52`);
- публикация состояния — `TimeSeriesController.start` (`TimeSeries.kt:82-83`);
- маппинг в `ChartState.Success` — `ChartViewModel.startCandleSeries`
  (`ChartViewModel.kt:266-282`);
- хук на изменение размера — `ChartInteraction.kt:409-418`;
- запись в кэш — `ChartViewModel.scheduleCacheWrite` (`ChartViewModel.kt:290-305`).

---

## 5. Решения: merge и автоскролл

```mermaid
flowchart TD
    E["liveUpdates: пришла Candle"] --> Q{"update.timestamp и last.timestamp<br/>в одном бакете timeframeMs?"}
    Q -->|да| S1["mergeItem: заменить последнюю<br/>high=max, low=min, close, volume+="]
    Q -->|нет| S2["mergeItem: items + update<br/>НОВАЯ свеча"]
    S1 --> EMIT["state emission, size без изменений"]
    S2 --> EMIT2["state emission, size +1"]
    EMIT2 --> SC{"historyGeneration == 0?"}
    SC -->|нет| SKIP["не автоскроллим - идёт история"]
    SC -->|да| SC2{"scrollOffset == 0<br/>или isAtLatest tolerance=totalW?"}
    SC2 -->|нет| KEEP["оставить позицию пользователя"]
    SC2 -->|да| JUMP["timeScale.scrollToLatest"]
```

---

## 6. Последовательность: подгрузка истории (loadMore)

```mermaid
sequenceDiagram
    autonumber
    participant U as Пользователь
    participant UI as ChartInteraction
    participant VM as ChartViewModel
    participant TC as TimeSeriesController
    participant S as CandleSeriesSource

    U->>UI: скролл влево (clampedOffset < 0)
    UI->>VM: onNeedMoreHistory -> dispatch(LoadMoreHistory)
    VM->>TC: loadMore()
    TC->>S: loadBefore(oldest - 1, pageSize=200)
    S-->>TC: older list
    alt older пусто
        TC->>TC: hasMore = false
    else older непусто
        TC->>TC: generation++ loadGeneration
        TC->>TC: items = older + items distinctBy timestamp
        TC-->>VM: state loadCount loadGeneration
        VM->>VM: historyGeneration = loadGeneration
        VM-->>UI: recompose
        UI->>UI: LaunchedEffect(historyGeneration) -> offsetAfterPrepend
        Note over UI: коррекция ровно один раз, не на каждый tick
    end
```

Плюс автозаполнение вьюпорта: если `maxScroll == 0` и `hasMoreHistory`,
движок сам вызывает `onNeedMoreHistory` (`ChartInteraction.kt:420-428`).

---

## 7. Жизненный цикл состояний

`ChartState` (то, что видит UI):

```mermaid
stateDiagram-v2
    [*] --> Loading
    Loading --> Success : items непусто (кэш или сеть)
    Loading --> Error : error и items пусто
    Success --> Loading : смена символа/ТФ (startCandleSeries)
    Success --> Success : realtime merge / loadMore
    Success --> Error : error и items пусто
    Error --> Loading : повторная загрузка
```

`TimeSeriesState` (что поставляет контроллер):

```mermaid
stateDiagram-v2
    [*] --> Init
    Init --> LoadingInitial : start()
    LoadingInitial --> Live : loadInitial вернул items
    LoadingInitial --> Error : исключение
    Live --> LoadingMore : loadMore()
    LoadingMore --> Live : older добавлены, loadGeneration++
    LoadingMore --> Live : older пусто, hasMore=false
    Live --> Stopped : stop / dispose
```

---

## 8. Для сравнения: footprint (новый бакет)

```mermaid
sequenceDiagram
    autonumber
    participant T as TradesAdapter 1m/5m
    participant FC as FootprintController
    participant VM as ChartViewModel
    participant UI as ChartInteraction

    T-->>FC: trade
    FC->>FC: candleStart = timestamp / displayMs * displayMs
    alt candleStart != lastCandleStart
        FC->>FC: завершить liveCandle, добавить в candles size +1
        FC-->>VM: state emission
        VM-->>UI: recompose
        UI->>UI: LaunchedEffect(candles.size) -> follow если у края
    else та же минута
        FC->>FC: liveCandle.addTrade, currentPrice = lastPrice
        FC-->>VM: state emission size без изменений
    end
```

Для 15m+ вместо лент сделок — `updateFormingCandle` (текущий бакет в `liveCandle`)
и polling закрытых бакетов (`FootprintController.start`).

---

## 9. Кто за что отвечает

| Шаг | Ответственный | Файл |
|---|---|---|
| WebSocket-свечи | `BinanceChartAdapter` | `providers/binance-provider/.../adapter/BinanceChartAdapter.kt` |
| HTTP-история кэндлов | `BinanceChartAdapter.getCandles/getCandlesBefore` | там же |
| Слияние realtime/истории | `CandleSeriesSource.mergeItem` | `platform-core/.../data/timeseries/CandleSeriesSource.kt:35` |
| Пагинация и состояние ряда | `TimeSeriesController` | `platform-core/.../domain/timeseries/TimeSeries.kt:68-123` |
| Маппинг в UI-состояние | `ChartViewModel.startCandleSeries` | `features/chart/.../ui/ChartViewModel.kt:226-283` |
| Кэш (запись/чтение/flush) | `ChartViewModel` + `LocalStorage` | `ChartViewModel.kt:290-320`, `features/localstorage/.../LocalStorage.kt` |
| Единый движок | `CandleStickChartInteraction` | `features/chart/.../ui/chart/ChartInteraction.kt` |
| Скролл/зум | `TimeScale` | `features/chart/.../scale/TimeScale.kt` |
| Autoscale диапазона | `PriceScale` + `ChartSeries.priceRange` | `scale/PriceScale.kt`, `ui/chart/ChartSeries.kt` |
| Отрисовка свечи | `drawChart` -> `drawCandle` | `features/chart/.../rendering/CandleRenderer.kt` |

---

## 10. Важные инварианты

- **Свеча появляется в `items` только через `mergeItem`**: тот же бакет →
  замена последней, новый бакет → `items + update`.
- **`candles.size` растёт** ровно в двух случаях: новая realtime-свеча и
  подгрузка истории. Движок различает их по `historyGeneration`.
- **Коррекция скролла** после истории применяется один раз на
  `loadGeneration` (иначе график «дёргался» на каждом тике).
- **Автоскролл** следует за ценой только если пользователь у правого края
  (`isAtLatest(tolerance = totalW)`) или это первичная загрузка.
- **Кэш пишется** throttled (30 c) + принудительно при смене символа/ТФ и
  `dispose`; читается на открытии (preload только при `ChartState.Loading`).
