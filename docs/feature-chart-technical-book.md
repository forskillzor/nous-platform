# Техническая книга модуля `features:chart`

## Разработка биржевого графика на Kotlin + Compose Multiplatform

**Уровень:** Junior → Middle
**Технологии:** Kotlin, Compose Multiplatform, Koin DI, Canvas 2D, Ktor, kotlinx.coroutines, kotlinx.serialization, SQLite
**Версия продукта:** Nous Platform 1.0
**Статус документа:** актуализировано после рефакторинга (ветка `char-big-refactoring`, фазы 0–D)

---

> **Как читать эту книгу.**
> Книга написана так, чтобы человек, который впервые видит проект, мог пройти путь
> «что это → из чего состоит → как течёт data → как это рисуется → как это менять».
> Код в книге — это **реальные выдержки из репозитория** (иногда сокращённые, чтобы
> убрать шум). Пометка **«В коде сейчас»** означает текущее поведение. Пометка
> **«Технический долг»** — известное проблемное место. Врезки **«Для Junior»**
> объясняют базовые понятия простыми словами.
>
> После рефакторинга (фазы 0–D) модуль получил:
> единое MVI-состояние, обобщённый `TimeSeriesController` в `platform-core`,
> выделенные `FootprintController` и `FootprintAggregator`, дисковый кэш свечей
> и footprint с разделением по биржам, а также полноценную подсистему рисования
> с линейкой и персистентом на диск.

---

# Оглавление

**Часть I. Введение**
1. [Что такое feature-chart и что он умеет](#1)
2. [Карта модуля: файлы и пакеты](#2)
3. [Сборка, source sets и запуск модуля отдельно](#3)
4. [Dependency Injection: Koin, appModule и featureChartModule](#4)

**Часть II. Модели, данные и кэш**
5. [Модели из api-market: Candle, Footprint, Liquidation, SymbolInfo](#5)
6. [Внутренние модели модуля: PriceRange, CandleMetrics, ChartLayout, ChartConfig](#6)
7. [Путь данных свечей: Binance → TimeSeriesController](#7)
8. [Путь данных footprint, ликвидаций и дисковый кэш](#8)

**Часть III. Состояние и ViewModel**
9. [MVI: ChartIntent, ChartUiState и dispatch](#9)
10. [Свечи: загрузка, realtime и ленивая история](#10)
11. [FootprintController: история, live-накопление, polling](#11)
12. [LiquidationViewModel, кэш и жизненный цикл](#12)

**Часть IV. UI-слой**
13. [ChartWindow и ChartWindowContent](#13)
14. [ChartInteraction: layout, состояния, Canvas-конвейер](#14)
15. [Скролл, зум, crosshair и ленивая загрузка — подробно](#15)
16. [ChartToolbar: символ, режим, агрегация, рисование](#16)
17. [FootprintChart: отдельный график для bidasker-web](#17)

**Часть V. Рендеринг**
18. [CandleRenderer: свечи, сетка, линия цены](#18)
19. [ChartPriceScaleRenderer: шкала цен и badge](#19)
20. [ChartTimeScaleRenderer: шкала времени](#20)
21. [ChartCrosshairRenderer и ChartTextRenderer](#21)
22. [FootprintRenderer: кластерный график, popup, агрегация уровней](#22)
23. [LiquidationRenderer: маркеры и гистограмма](#23)
24. [Рисование: Drawing, DrawingRenderer, DrawingOverlay, DrawingRepository](#24)

**Часть VI. Утилиты и математика**
25. [ChartCalculator: все формулы в одном месте](#25)
26. [Timeframes, Format, ChartConstants, SymbolFormatter](#26)
27. [DrawingHistory: Undo/Redo как Compose-состояние](#27)
28. [Система координат и порядок слоёв Canvas](#28)

**Часть VII. Интеграция, качество, рецепты**
29. [Интеграция в composeApp: workspace и legacy-режим](#29)
30. [Интеграция в bidasker-web](#30)
31. [Тестирование модуля](#31)
32. [Технический долг и известные проблемы](#32)
33. [Рецепты: добавить индикатор, таймфрейм, инструмент, источник](#33)
34. [Глоссарий](#34)

---

# Часть I. Введение

# 1. Что такое feature-chart и что он умеет <a name="1"></a>

## 1.1. Контекст проекта

`feature-chart` — модуль биржевого графика в составе **Nous Platform**, торгового
терминала для криптовалют. Платформа написана на **Kotlin Multiplatform (KMP)**,
UI — на **Compose Multiplatform**. График — один из трёх главных виджетов
терминала наряду со стаканом (DOM) и лентой сделок (Trades).

Модуль самодостаточен: он собирается как отдельное desktop-приложение
(`./gradlew :features:chart:run`) и одновременно встраивается в `composeApp`
как панель рабочего пространства (workspace).

## 1.2. Два режима отображения

У графика есть переключатель режима (`ChartMode`):

| Режим | Что показывает | Кто рисует |
|---|---|---|
| `CANDLESTICK` | Японские свечи OHLCV | `drawChart()` из `CandleRenderer.kt` |
| `FOOTPRINT` | Кластерный график (bid/ask объёмы по ценовым уровням внутри свечи) | `drawFootprintChart()` из `FootprintRenderer.kt` |

Переключение — интент `ChartIntent.ToggleChartMode`, кнопка `C`/`FP` живёт
в `ChartToolbar`.

Важная деталь: **оба режима рисуются одним и тем же composable** —
`CandleStickChart`. Когда включён footprint, в него передаётся параметр
`footprintCandles: List<FootprintCandle>?`, и `ChartInteraction` выбирает
`drawFootprintChart` вместо `drawChart`. Свечи при этом всё равно нужны —
из footprint-свечей строится «каркас» `Candle` для масштаба, скролла и кроссхаира.

## 1.3. Возможности

Что умеет график сейчас:

- **Свечи** за таймфреймы `1m, 5m, 15m, 30m, 1h, 4h, 1d, 1w` с realtime-обновлением
  последней свечи (WebSocket Binance). Маппинг таймфреймов вынесен в
  `Timeframes` (platform-core) и больше не теряет `30m`/`1w`.
- **Ленивая загрузка истории**: скролл влево до пустой зоны → подгрузка 200 свечей.
- **Зум** колёсиком: обычный (якорь — правая, самая новая свеча) и `Ctrl+Zoom`
  (якорь — свеча под курсором). Минимальный зум — `0.05` (видно очень много свечей).
- **Панорамирование** мышью (drag).
- **Crosshair** с инфо-панелью (Time/O/H/L), метками high/low и ценой/временем на осях.
  Свечи, crosshair и шкала цен используют **одно Y-пространство** (`chartMainArea`),
  поэтому метки совпадают со свечами.
- **Footprint**: история с REST-сервера, live-накопление из ленты сделок для
  `1m`/`5m`, серверный polling для старших таймфреймов, агрегация уровней `1x/10x/100x`.
  Границы footprint-свечей выровнены по абсолютному времени (нет «05:26–05:27»).
- **Ликвидации**: маркеры на графике и гистограмма в отдельной панели-индикаторе.
- **Рисование**: трендовая, горизонтальный уровень, прямоугольник, вертикальная
  линия и **линейка** (`Δ цены`, `%`, время) с undo/redo, метками и сохранением
  на диск в разрезе workspace + панель.
- **Дисковый кэш**: свечи и footprint пишутся в SQLite (`LocalStorage`),
  при старте график мгновенно показывает кэш, затем обновляется с биржи/бэкенда.
  В `Settings → Storage` кэш виден и чистится по бирже/символу/таймфрейму.
- **Выбор символа** через `SymbolSearchDropdown` с подсветкой символов,
  для которых есть footprint.

## 1.4. Чего в коде пока нет

Чтобы не создавать ложных ожиданий:

- Индикаторы SMA/EMA/VWAP в этом модуле не реализованы — есть только контракт
  индикаторных панелей (`indicatorRenderers`) и гистограмма ликвидаций.
- Провайдер MEXC отсутствует: обобщённый `TimeSeriesController` и
  `LiquidationSeriesSource` готовы к нему, но сам провайдер ещё не написан.
- Footprint-бэкенд (`FootprintApiClient`) — конкретный REST-клиент с хардкодом
  адреса; интерфейса `FootprintAdapter` в `api-market` пока нет.
- Рисование не привязано к таймфрейму: рисунки сохраняются на панель, а не на
  «символ + таймфрейм».
- Нет виртуального времени/тестов UI: тестами покрыты чистые функции,
  контроллер, персистент и кэш.

---

# 2. Карта модуля: файлы и пакеты <a name="2"></a>

## 2.1. Полная структура

```
features/chart/
├── build.gradle.kts
└── src/
    ├── commonMain/kotlin/com/aandios/nous/feature/chart/
    │   ├── footprint/
    │   │   ├── FootprintApiClient.kt          # REST-клиент market-data-server
    │   │   ├── FootprintAggregator.kt         # чистая логика: source-tf и выровненная агрегация
    │   │   └── FootprintController.kt         # вся footprint-логика (история, live, polling, кэш)
    │   ├── indicator/
    │   │   ├── LiquidationViewModel.kt        # состояние ликвидаций поверх TimeSeriesController
    │   │   └── LiquidationSeriesSource.kt     # TimeSeriesSource для LiquidationAdapter
    │   ├── model/
    │   │   ├── CandleMetrics.kt               # width + spacing свечи
    │   │   ├── ChartLayout.kt                 # прямоугольники областей графика
    │   │   ├── FootprintMapping.kt            # FootprintCandle.toSkeletonCandle()
    │   │   └── PriceRange.kt                  # диапазон цен
    │   ├── scale/
    │   │   ├── TimeScale.kt                   # горизонтальная шкала: скролл/зум/index↔x
    │   │   └── PriceScale.kt                  # вертикальная шкала: fit/сдвиг/диапазон
    │   ├── rendering/
    │   │   ├── CandleRenderer.kt              # свечи, сетка, линия цены
    │   │   ├── ChartCrosshairRenderer.kt      # перекрестие и инфо-панель
    │   │   ├── ChartPriceScaleRenderer.kt     # шкала цен, badge цены
    │   │   ├── ChartTextRenderer.kt           # утилита drawTextLine
    │   │   ├── ChartTimeScaleRenderer.kt      # шкала времени
    │   │   └── FootprintRenderer.kt           # footprint-свечи, popup, агрегация уровней
    │   ├── tools/
    │   │   ├── Drawing.kt                     # @Serializable модели рисунков + DrawingToolType
    │   │   ├── DrawingHistory.kt              # Undo/Redo; список — Compose-состояние
    │   │   ├── DrawingRenderer.kt             # отрисовка рисунков и их меток
    │   │   └── DrawingRepository.kt           # персистент рисунков (workspace + panel)
    │   ├── ui/
    │   │   ├── ChartConfig.kt                 # ChartConfig/CandleStyle/FootprintConfig + ChartMode
    │   │   ├── ChartIntent.kt                 # MVI-интенты
    │   │   ├── ChartUiState.kt                # единое состояние + sealed ChartState
    │   │   ├── ChartStatePersistor.kt         # save/restore символа/ТФ/режима/агрегации
    │   │   ├── ChartToolbar.kt                # верхняя панель: символ, режим, ТФ, рисование
    │   │   ├── ChartViewModel.kt              # оркестратор: state + dispatch
    │   │   └── chart/
    │   │       ├── CandleStickChart.kt        # тонкая обёртка (публичный API)
    │   │       ├── ChartInteraction.kt        # единый движок: layout, жесты, Canvas-конвейер
    │   │       ├── ChartCanvas.kt             # контекст отрисовки серий/оверлеев
    │   │       ├── ChartSeries.kt             # ChartSeries + Candlestick/FootprintSeries
    │   │       ├── DrawingOverlay.kt          # перехват жестов при активном инструменте
    │   │       ├── FootprintChart.kt          # тонкая обёртка над движком (bidasker-web)
    │   │       └── LiquidationRenderer.kt     # маркеры и гистограмма ликвидаций
    │   └── utils/
    │       ├── ChartCalculator.kt             # чистые функции: priceToY, PriceRange и др.
    │       ├── ChartConstants.kt              # BASE_CANDLE_WIDTH
    │       └── Format.kt                      # formatPrice / formatTime
    └── jvmMain/kotlin/com/aandios/nous/feature/chart/
        ├── di/FeatureChartModule.kt           # Koin-модуль для изолированного запуска
        └── ui/ChartWindow.kt                  # composable-обёртка + fun main() для preview
```

Смежные модули, появившиеся/изменённые в рефакторинге:

```
platform-core/src/commonMain/kotlin/com/aandios/nous/core/
├── domain/timeseries/
│   ├── TimeSeries.kt        # TimeSeriesSource, TimeSeriesState, TimeSeriesController
│   └── Timeframes.kt        # supported / toExchangeInterval / millis
├── domain/cache/
│   └── CacheStores.kt       # CandleCacheStore, FootprintCacheStore
└── data/
    ├── timeseries/CandleSeriesSource.kt     # свечи поверх ChartAdapter
    └── repository/ChartRepositoryImpl.kt    # фабрика candleSource

features/localstorage/.../LocalStorage.kt    # SQLite: settings + candles_cache + footprint_cache
```

## 2.2. Зачем именно так: слои и ответственности

| Пакет | Ответственность | Зависит от |
|---|---|---|
| `model/` | data class'ы без логики + маппинг моделей | Compose geometry (`Rect`) |
| `utils/` | чистые функции и константы | `api-market`, `model/` |
| `scale/` | шкалы времени/цены (состояние + математика) | `utils/`, `model/`, Compose state |
| `rendering/` | `DrawScope`-функции: рисуют, не решают | `model/`, `utils/`, `ui/ChartConfig` |
| `tools/` | модели, история и персистент рисунков | `model/`, `utils/`, Compose, `platform-core` |
| `ui/chart/` | движок, серии, composable'ы и интерактивность | всё выше + `platform-core` |
| `ui/` | состояние (MVI), ViewModel, конфиг, toolbar | `platform-core`, `api-market` |
| `footprint/` | сетевой клиент и footprint-логика | Ktor, `api-market`, `platform-core` |
| `di/` (jvm) | сборка зависимостей для preview | Koin, `binance-provider` |

Направление зависимостей: **снизу вверх**, `rendering` не знает про `ui/chart`,
а `utils` не знает про Canvas. Единственное оставшееся исключение:

- `rendering/FootprintRenderer.kt` и `ui/ChartConfig.kt` зависят от
  `features:dom` через `AggregationLevel` — межфичевая зависимость, кандидат
  на переезд в `api-market`/`platform-core`.

## 2.3. Ключевые файлы по размеру (что читать первым)

| Файл | Строк | Что внутри |
|---|---:|---|
| `ui/ChartViewModel.kt` | ~300 | оркестрация: state, dispatch, контроллер свечей, кэш |
| `footprint/FootprintController.kt` | ~400 | вся footprint-логика |
| `ui/chart/ChartInteraction.kt` | ~520 | единый движок: layout, жесты, Canvas-конвейер |
| `rendering/FootprintRenderer.kt` | ~320 | footprint-рендеринг и popup |
| `jvmMain/ui/ChartWindow.kt` | ~330 | экран графика + preview main() |
| `scale/TimeScale.kt` | ~150 | скролл/зум/index↔x |
| `ui/chart/ChartSeries.kt` | ~90 | контракт серии + свечи/footprint |
| `platform-core/.../TimeSeries.kt` | ~150 | обобщённый контроллер пагинации/live |
| `rendering/ChartCrosshairRenderer.kt` | ~300 | crosshair и панель |
| `tools/DrawingRenderer.kt` | ~200 | рисунки и метки |

---

# 3. Сборка, source sets и запуск модуля отдельно <a name="3"></a>

## 3.1. build.gradle.kts

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
            implementation(project(":features:dom"))

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.compose.material3)
        }

        jvmMain.dependencies {
            implementation(compose.desktop.currentOs)
            implementation(libs.kotlinx.coroutines.swing)
            implementation(libs.koin.core)
            implementation(libs.koin.compose)
        }

        jvmTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit.jupiter)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.aandios.nous.feature.chart.ui.ChartWindowKt"
    }
}
```

### 3.1.1. Плагин `conventions.kmp-feature`

Плагин из `build-logic` включает `kotlin.multiplatform`, `org.jetbrains.compose`
и compose-компилятор, а также добавляет базовые compose-зависимости
(`runtime`, `foundation`, `material3`, `ui`) в `commonMain`. Именно поэтому
`DrawingHistory` может использовать `mutableStateListOf`, а рендереры —
`DrawScope` без дополнительных объявлений.

### 3.1.2. Зачем в feature-модуле `binance-provider`?

Только для изолированного preview-приложения: `FeatureChartModule` создаёт
`Provider` напрямую через `BinanceProviderFactory`. В основном приложении
(`composeApp`) провайдер приходит из `AppModule`, а не из этого модуля.
Технический долг: `commonMain`-код фичи не должен зависеть от конкретного
провайдера — зависимость нужна лишь `jvmMain/di`.

### 3.1.3. `AggregationLevel` из `features:dom`

Уровни агрегации footprint (`1x/10x/100x`) переиспользуют тип из DOM-фичи.
Это удобно (один тип на терминал), но создаёт связность фич. Правильное
решение на будущее — перенести `AggregationLevel` в `api-market` или
`platform-core`.

## 3.2. Запуск изолированного приложения

```bash
./gradlew :features:chart:run
```

Точка входа — `fun main()` в конце `ChartWindow.kt`:

```kotlin
fun main() = application {
    stopKoin()
    initKoinForPreview()

    Window(
        onCloseRequest = ::exitApplication,
        title = "Nous Platform • Chart Preview",
        state = rememberWindowState(width = 800.dp, height = 600.dp)
    ) {
        KoinContext {
            TradingTerminalTheme {
                ChartWindow()
            }
        }
    }
}
```

`ChartWindow()` без параметров достаёт `ChartViewModel` и `LiquidationViewModel`
из Koin и один раз отправляет `ChartIntent.LoadChart()`.

## 3.3. Целевые платформы

`conventions.kmp-feature` объявляет `jvm()`, `js(IR)` и `wasmJs`.
Фактически модуль используется на JVM (composeApp, preview) и в JS
(bidasker-web использует `FootprintChart` и `ChartConfig`). Код `commonMain`
не должен тянуть JVM-only API — за этим следит сборка
`:features:chart:compileKotlinJs`/`:compileKotlinWasmJs`.

---

# 4. Dependency Injection: Koin, appModule и featureChartModule <a name="4"></a>

## 4.1. Что такое DI и зачем он здесь

**Для Junior.** Dependency Injection — это когда класс не создаёт свои
зависимости сам (`new HttpClient()`), а получает их в конструкторе. Koin —
контейнер: вы описываете, как создать объект, а Koin отдаёт его тому, кто
попросил. Это позволяет подменять реализации (Binance → MEXC), переиспользовать
`HttpClient` и не плодить глобальные синглтоны руками.

## 4.2. Два места сборки зависимостей

### 4.2.1. `AppModule` (production, `composeApp`)

Ключевое для графика:

```kotlin
// Adapters from Provider
single<ChartAdapter> { get<Provider>().chart ?: error("Chart adapter not available") }
single<TradesAdapter> { get<Provider>().trades ?: error("Trades adapter not available") }
single<LiquidationAdapter?> { get<Provider>().liquidation }

// Repositories
single<ChartRepository> { ChartRepositoryImpl(chartAdapter = get()) }

// Local storage (SQLite) + кэш-интерфейсы
single<LocalStorage> { LocalStorage() }
single<StateStore> { get<LocalStorage>() }
single<CandleCacheStore> { get<LocalStorage>() }
single<FootprintCacheStore> { get<LocalStorage>() }

// ViewModels
factory {
    ChartViewModel(
        chartRepository = get(),
        symbolInfoAdapter = get(),
        footprintApiClient = get(),
        tradesAdapter = get(),
        stateStore = get(),
        candleCache = get(),
        footprintCache = get(),
    )
}
```

`ChartViewModel` — `factory`, то есть новый экземпляр на каждую панель
графика. В workspace-режиме `main.kt` кэширует VM в
`ws.liveViewModels["${pc.id}_chart"]`, поэтому на панель живёт ровно один VM.

### 4.2.2. `featureChartModule` (preview, `features:chart`)

Повторяет часть `AppModule`, включая локальное хранилище: регистрируются
`LocalStorage`, `StateStore`, `CandleCacheStore`, `FootprintCacheStore`, и
все они передаются в `ChartViewModel`. Preview использует ту же БД
`~/.nous/storage.db`, что и основное приложение — кэш свечей/footprint,
персистент рисунков и `ChartStatePersistor` работают в изолированном окне.

### 4.2.3. Кэш как отдельные интерфейсы

`CandleCacheStore` и `FootprintCacheStore` объявлены в `platform-core`
(`core/domain/cache/CacheStores.kt`). `LocalStorage` реализует их, а
`ChartViewModel`/`FootprintController` зависят только от интерфейсов —
поэтому в тестах и preview кэш можно не подключать вовсе.

## 4.3. `single` vs `factory`

| Тип | Что делает | Пример |
|---|---|---|
| `single` | один объект на всё приложение | `HttpClient`, `LocalStorage`, репозитории |
| `factory` | новый объект на каждый запрос | `ChartViewModel`, `LiquidationViewModel` |

## 4.4. Как ViewModel попадает в UI

В preview:

```kotlin
@Composable
fun ChartWindow() {
    val chartViewModel: ChartViewModel = koinInject()
    LaunchedEffect(Unit) { chartViewModel.dispatch(ChartIntent.LoadChart()) }
    val liquidationViewModel: LiquidationViewModel = koinInject()
    ChartWindowContent(chartViewModel, liquidationViewModel = liquidationViewModel)
}
```

В workspace (`main.kt`) VM создаётся через `koinInject()` и кладётся в
`liveViewModels` воркспейса, а `ChartWindow(vm, ...)` получает его аргументом.
Так панель и её VM живут и умирают вместе с воркспейсом.

---

# Часть II. Модели, данные и кэш

# 5. Модели из api-market: Candle, Footprint, Liquidation, SymbolInfo <a name="5"></a>

Все рыночные модели живут в `public-api:api-market` и не зависят от UI.

## 5.1. Candle — свеча

```kotlin
data class Candle(
    val open: Float,
    val high: Float,
    val close: Float,
    val low: Float,
    val timestamp: Long,
    val volume: Float = 0f
)
```

Порядок полей важен: в коде встречается позиционный конструктор
`Candle(open, high, close, low, timestamp, volume)`. Конвертация
footprint-свечи в «каркасную» — `FootprintCandle.toSkeletonCandle()`
(`model/FootprintMapping.kt`).

**Технический долг.** Цены — `Float`. Для большинства инструментов этого
хватает, но на экстремально малых ценах (8+ знаков) точность ограничена.
Перспективное направление — `Double` в моделях и конвертация в `Float`
только на уровне рендеринга.

## 5.2. FootprintLevel и FootprintCandle

```kotlin
@Serializable
data class FootprintLevel(
    val price: String,
    val bidVolume: String,
    val askVolume: String,
    val bidCount: Int = 0,
    val askCount: Int = 0
) {
    val priceFloat: Float get() = price.toFloatOrNull() ?: 0f
    val bidVolumeFloat: Float get() = bidVolume.toFloatOrNull() ?: 0f
    val askVolumeFloat: Float get() = askVolume.toFloatOrNull() ?: 0f
}

@Serializable
data class FootprintCandle(
    val exchange: String = "",
    val symbol: String = "",
    val timeframe: String = "",
    val startTime: Long = 0L,
    val endTime: Long = 0L,
    val totalTicks: Long = 0L,
    val minPrice: String = "0",
    val maxPrice: String = "0",
    val levels: List<FootprintLevel> = emptyList()
)
```

Почему цены и объёмы — строки? Так их отдаёт market-data-server, и строки
позволяют не терять точность до момента, когда число реально нужно
(рендеринг/агрегация). `@Serializable` нужен для дискового кэша:
`LocalStorage` хранит footprint как JSON.

## 5.3. MutableFootprintCandle — live-накопление

```kotlin
class MutableFootprintCandle(
    val exchange: String = "Binance",
    val symbol: String = "",
    val timeframe: String = "1m",
    val startTime: Long = 0L,
    val endTime: Long = 0L
) {
    private val levelMap = linkedMapOf<Float, MutableLevel>()
    var lastPrice: Float = 0f
        private set

    fun addTrade(price: Float, quantity: Float, isBuy: Boolean) { ... }
    fun clear() { ... }
    fun toFootprintCandle(totalTicks: Long = 0L): FootprintCandle { ... }
}
```

Используется в `FootprintController` для накопления текущей минуты из ленты
сделок. Важно: `startTime`/`endTime` теперь задаются при создании
накопителя под конкретный бакет (`candleStart`, `candleStart + displayMs`),
поэтому завершённые live-свечи получают корректные метки времени.

## 5.4. LiquidationOrder

```kotlin
@Serializable
data class LiquidationOrder(
    val symbol: String,
    val price: Double,
    val quantity: Double,
    val timestamp: Long,
    val side: TradeSide,
    val orderType: String = ""
)
```

`side == SELL` — ликвидация лонга (рисуем красный маркер), `BUY` — ликвидация
шорта (зелёный).

## 5.5. Trade — сделка из ленты

```kotlin
@Serializable
data class Trade(
    val id: Long,
    val symbol: String,
    val price: Double,
    val quantity: Double,
    val timestamp: Long,
    val isBuyerMaker: Boolean, // true = продажа (красный), false = покупка (зелёный)
    val side: TradeSide
)
```

`TradesAdapter.subscribeToTrades(symbol)` отдаёт поток `Trade`. Из него
`FootprintController` накапливает live-свечу: `addTrade(price, quantity,
isBuy = !trade.isBuyerMaker)`.

## 5.6. SymbolInfo

```kotlin
@Serializable
data class SymbolInfo(
    val symbol: String,      // "BTCUSDT"
    val tickSize: Double,    // минимальный шаг цены
    val stepSize: Double,    // минимальный шаг объёма
    val minQty: Double,      // минимальный объём ордера
    val minNotional: Double, // минимальная сумма ордера
    val status: String,      // TRADING, HALT и т.д.
    val baseAsset: String,   // "BTC"
    val quoteAsset: String,  // "USDT"
)
```

Из `SymbolInfo` строится `SymbolFormatter` (точность цен/объёмов). Список
символов фильтруется по `status == "TRADING"`.

## 5.7. AggregationLevel (из features:dom)

```kotlin
sealed class AggregationLevel(val multiplier: Double) {
    object BaseTick : AggregationLevel(1.0)
    object TenTick : AggregationLevel(10.0)
    object HundredTick : AggregationLevel(100.0)

    fun effectiveTickSize(baseTickSize: Double): Double = baseTickSize * multiplier
    fun roundDown(price: Double, baseTickSize: Double): Double { ... }
    fun aggregationKey(price: String, baseTickSize: Double): String { ... }
}
```

`aggregationKey` используется в `aggregateLevels()` (рендеринг footprint):
цены группируются по `roundDown(price, tickSize)`.

---

# 6. Внутренние модели модуля <a name="6"></a>

## 6.1. PriceRange — диапазон цен

```kotlin
data class PriceRange(
    val max: Float,         // реальный максимум видимых свечей
    val min: Float,         // реальный минимум
    val visibleMax: Float,  // max + 5% padding
    val visibleMin: Float,  // min − 5% padding
    val range: Float        // visibleMax − visibleMin
)
```

`range` никогда не должен использоваться без защиты от нуля — все формулы
(`priceToY`, `priceFromY`) подставляют `0.01f`, если диапазон вырожден.

## 6.2. CandleMetrics — размеры свечи

```kotlin
data class CandleMetrics(val width: Float, val spacing: Float)
```

Считается из зума: `width = BASE_CANDLE_WIDTH * zoomLevel`, `spacing = width * 0.3 / 0.7`.

## 6.3. ChartLayout — раскладка областей

```kotlin
data class ChartLayout(
    val canvasWidth: Float,
    val canvasHeight: Float,
    val priceScaleWidth: Float,
    val priceScaleArea: Rect,
    val chartPadding: Float = 8f,
    val timeScaleHeight: Float = 20f,
    val chartMainArea: Rect,
    val timeScaleArea: Rect,
    val indicatorAreas: List<Rect> = emptyList()
)
```

После рефакторинга в `ChartLayout` **нет** поля `chartArea`: свечи, crosshair
и шкала цен работают в одном прямоугольнике — `chartMainArea`. Это устранило
рассинхрон, когда crosshair считался по одной высоте, а свечи рисовались по другой.

```
┌──────────────────────────────┬──────────┐
│                              │          │
│         chartMainArea        │  price   │
│   (свечи, footprint,         │  Scale   │
│    crosshair, шкала)         │  Area    │
│                              │          │
├──────────────────────────────┴──────────┤
│ indicatorAreas[i] (например, гистограмма)│
├─────────────────────────────────────────┤
│              timeScaleArea               │
└─────────────────────────────────────────┘
```

## 6.4. ChartConfig, CandleStyle, FootprintConfig

```kotlin
data class ChartConfig(
    val backgroundColor: Color = ChartColors.chartBackground,
    val gridColor: Color = ChartColors.gridLine,
    val axisTextColor: Color = ChartColors.axisText,
    val showGrid: Boolean = true,
    val showPriceScale: Boolean = true,
    val priceScaleWidth: Dp = 60.dp,
    val candleStyle: CandleStyle = CandleStyle(),
    val footprintConfig: FootprintConfig = FootprintConfig(),
    val priceFormatter: SymbolFormatter = SymbolFormatter.DEFAULT
)
```

Что изменилось после рефакторинга:

- `priceFormatter` — единый форматтер инструмента. Все рендереры
  (`ChartPriceScaleRenderer`, `ChartCrosshairRenderer`, popup footprint,
  подписи рисунков) берут точность оттуда, а не из `DEFAULT`.
- Удалены неиспользуемые поля `bodyWidth`, `showWicks`, `showNumbers`,
  `maxLevelsPerCandle`, `showVolume`.
- `showPriceScale`/`priceScaleWidth` больше не дублируются параметрами
  composable-функций — источник истины один: `ChartConfig`.

`ChartWindowContent` собирает конфиг так:

```kotlin
val chartConfig = remember(uiState.fpAggregation, uiState.currentSymbolFormatter) {
    DefaultChartConfig.copy(
        footprintConfig = DefaultChartConfig.footprintConfig.copy(
            aggregationLevel = uiState.fpAggregation,
            tickSize = uiState.currentSymbolFormatter.tickSize
        ),
        priceFormatter = uiState.currentSymbolFormatter
    )
}
```

## 6.5. ChartState — состояние загрузки

```kotlin
sealed interface ChartState {
    object Loading : ChartState
    data class Success(val candles: List<Candle>, val currentPrice: Float? = null) : ChartState
    data class Error(val message: String) : ChartState
}
```

`sealed interface` даёт исчерпывающий `when` без `else`. UI показывает
«Loading chart data…», ошибку или график.

---

# 7. Путь данных свечей: Binance → TimeSeriesController <a name="7"></a>

## 7.1. Общая схема

```
Binance REST (klines)         Binance WS (kline_<tf>)
        │                              │
        ▼                              ▼
  ChartAdapter.getCandles      ChartAdapter.subscribeToCandles
        │                              │
        └──────────┬───────────────────┘
                   ▼
        CandleSeriesSource  (loadInitial / loadBefore / liveUpdates / mergeItem)
                   │
                   ▼
        TimeSeriesController<Candle>   (state: items, hasMore, loading, loadCount, error)
                   │
                   ▼
        ChartViewModel.startCandleSeries → ChartUiState.chartState
                   │
                   ▼
        ChartWindowContent → CandleStickChart → ChartInteraction → drawChart
```

Ключевое изменение рефакторинга: **логика «история + realtime + пагинация»
больше не размазана** между репозиторием и ViewModel. Она живёт в
`TimeSeriesController` (platform-core) и конкретном `TimeSeriesSource`.

## 7.2. ChartAdapter — контракт провайдера

```kotlin
interface ChartAdapter : MarketAdapter {
    suspend fun getCandles(symbol: String, interval: String, limit: Int = 500): List<Candle>
    suspend fun getCandlesBefore(symbol: String, interval: String, endTime: Long, limit: Int = 500): List<Candle>
    fun subscribeToCandles(symbol: String, interval: String): Flow<Candle>
}
```

Binance-реализация ходит на `fapi/v1/klines` и слушает
`wss://fstream.binance.com/market/ws/<symbol>@kline_<interval>`.

## 7.3. TimeSeriesSource и TimeSeriesController

```kotlin
interface TimeSeriesSource<T> {
    suspend fun loadInitial(): List<T>
    suspend fun loadBefore(beforeTimestamp: Long, limit: Int): List<T>
    fun liveUpdates(): Flow<T>
    fun mergeItem(items: List<T>, update: T): List<T>
    fun timestampOf(item: T): Long
}

data class TimeSeriesState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val loadCount: Int = 0,
    val error: String? = null
)
```

`TimeSeriesController`:

```kotlin
class TimeSeriesController<T>(
    private val source: TimeSeriesSource<T>,
    private val scope: CoroutineScope,
    private val pageSize: Int = 200,
) {
    val state: StateFlow<TimeSeriesState<T>>

    fun start()      // loadInitial + подписка на liveUpdates
    fun loadMore()   // loadBefore(oldest − 1), prepend + distinctBy + sort
    fun stop()       // отменить live-подписку, данные оставить
    fun dispose()    // stop
}
```

Детали реализации, которые важно знать:

- `start()` переводит состояние в `loading = true`, грузит initial, затем
  подписывается на `liveUpdates()`. Каждый live-элемент проходит через
  `source.mergeItem(items, update)` — правило слияния задаёт источник.
- `loadMore()` защищён от гонок (`loadingMore`) и умеет завершать пагинацию:
  пустой ответ → `hasMore = false`.
- `loadCount` — сколько элементов пришло последней подгрузкой; UI использует
  его для коррекции скролла.
- `CancellationException` пробрасывается наружу (не глотается), остальные
  ошибки попадают в `state.error`.

**Почему generic-контроллер, а не три копии?** Раньше «загрузить историю,
подписаться на realtime, догрузить старое» было продублировано в VM свечей,
footprint и ликвидаций. Теперь источник описывает только специфику
(эндпоинты и правило слияния), а пагинация/состояние — общие. Провайдеру
MEXC достаточно реализовать `ChartAdapter`/`LiquidationAdapter`.

## 7.4. CandleSeriesSource: правило слияния realtime

```kotlin
override fun mergeItem(items: List<Candle>, update: Candle): List<Candle> {
    if (items.isEmpty()) return listOf(update)

    val lastCandle = items.last()
    val isSameCandle = update.timestamp / timeframeMs == lastCandle.timestamp / timeframeMs

    return if (isSameCandle) {
        items.dropLast(1) + update.copy(
            high = maxOf(lastCandle.high, update.high),
            low = minOf(lastCandle.low, update.low),
            close = update.close,
            volume = lastCandle.volume + update.volume
        )
    } else {
        items + update
    }
}
```

Биржа присылает обновления текущей свечи много раз в секунду. Слияние
по бакету времени (`timestamp / timeframeMs`) не даёт плодить дубликаты,
а high/low берутся максимумом/минимумом.

## 7.5. Timeframes: единый маппинг

```kotlin
object Timeframes {
    val supported = setOf("1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w")
    fun toExchangeInterval(timeframe: String): String =
        if (timeframe in supported) timeframe else "1h"
    fun millis(timeframe: String): Long = when (timeframe) { ... }
}
```

До рефакторинга `30m` и `1w` молча превращались в `1h` — теперь это
исправлено и маппинг живёт в одном месте.

## 7.6. Ленивая загрузка истории

1. Пользователь тянет график влево до пустой зоны (`clampedOffset < 0`).
2. `ChartInteraction` вызывает `onNeedMoreHistory()`.
3. UI отправляет `ChartIntent.LoadMoreHistory`.
4. `ChartViewModel` вызывает `candleController.loadMore()`.
5. `CandleSeriesSource.loadBefore(oldest − 1, 200)` тянет свечи с биржи.
6. Контроллер делает `prepend + distinctBy + sortedBy`, выставляет `loadCount`.
7. `ChartInteraction` по `historyLoadCount` сдвигает `scrollOffset`, чтобы
   картинка не «прыгнула».

Отмена realtime-подписки при этом **не происходит**: `TimeSeriesController`
хранит полный список (включая догруженную историю), и live-обновления
продолжают в него мержиться. Это исправило баг «график замирает после
подгрузки истории».

## 7.7. Кэш свечей

`ChartViewModel.startCandleSeries()` делает две вещи, связанные с кэшем:

```kotlin
// 1. Быстрый показ из кэша, пока грузится свежая история с биржи.
//    Guard внутри update: кэш не может перетереть свежие данные
viewModelScope.launch {
    val cache = candleCache ?: return@launch
    val cached = cache.getCandles(EXCHANGE, ticker, timeframe, CACHE_LIMIT)
    _state.update { s ->
        if (cached.isNotEmpty() &&
            s.currentSymbol == ticker &&
            s.currentTimeframe == timeframe &&
            s.chartState is ChartState.Loading
        ) {
            s.copy(chartState = ChartState.Success(cached, cached.last().close))
        } else s
    }
}

// 2. Throttled-запись (не чаще раза в 30 секунд, последние 500 свечей)
private fun scheduleCacheWrite(symbol: String, timeframe: String, candles: List<Candle>) {
    if (now - lastCacheWriteMs < CACHE_WRITE_INTERVAL_MS) return
    lastCacheWriteMs = now
    lastSnapshot = candles.takeLast(CACHE_LIMIT)   // для flushCache()
    cacheScope.launch { cache.saveCandles(EXCHANGE, symbol, timeframe, lastSnapshot) }
}

// 3. Немедленный flush без троттла — смена символа/ТФ и dispose()
private fun flushCache() {
    if (lastSnapshot.isEmpty()) return
    cacheScope.launch { cache.saveCandles(EXCHANGE, lastSnapshotSymbol, lastSnapshotTimeframe, lastSnapshot) }
}
```

Почему throttle, а не запись на каждый тик: realtime-свеча приходит каждую
секунду, а `INSERT OR REPLACE` 500 строк на каждый тик — лишняя нагрузка.
Почему preload только при `ChartState.Loading`: кэш не должен перетирать
уже пришедшие свежие данные.

Почему нужен `flushCache`: троттл может «проглотить» данные последнего
символа при быстром переключении (например, сменил символ через 10 секунд
после записи). Flush вызывается при пересоздании контроллера
(`startCandleSeries`) и в `dispose()`, поэтому последний снапшот
сохраняется независимо от троттла.

Записи выполняются в отдельном `cacheScope` (`Dispatchers.Default` +
`SupervisorJob`), чтобы SQLite не блокировал главный поток и flush успевал
выполниться после `dispose()` панели.

Кэш подключён **и в preview**: `FeatureChartModule` регистрирует
`LocalStorage`/`StateStore`/`CandleCacheStore`/`FootprintCacheStore` и
передаёт их в `ChartViewModel` — изолированное окно (`:features:chart:run`)
использует ту же БД `~/.nous/storage.db`, что и composeApp. Заодно в
preview заработали персистент рисунков и `ChartStatePersistor`.

**Технический долг.** `EXCHANGE = "Binance"` пока константа — при появлении
мультибиржевости её нужно брать из выбранного провайдера.

---

# 8. Путь данных footprint, ликвидаций и дисковый кэш <a name="8"></a>

## 8.1. FootprintApiClient — REST-клиент market-data-server

```kotlin
class FootprintApiClient(
    private val httpClient: HttpClient,
    private val baseUrl: String = "http://95.81.99.28:8085"
) {
    suspend fun getFootprint(
        exchange: String = "Binance",
        symbol: String,
        timeframe: String,
        from: Long? = null,
        to: Long? = null,
        limit: Int = 20
    ): List<FootprintCandle>

    suspend fun getCandleLevels(...): List<FootprintLevel>
    suspend fun getInstruments(exchange: String = "Binance"): List<InstrumentSummary>
}
```

Сервер отдаёт свечи **по убыванию времени** — все вызовы в контроллере
делают `.reversed()`, чтобы внутри модуля данные всегда шли по возрастанию.

**Технический долг.** Хардкод IP, конкретный класс вместо интерфейса
`FootprintAdapter` — MEXC-бэкенду пока некуда подключиться.

## 8.2. Как выбирается «источник» footprint-таймфрейма

```kotlin
fun resolveFootprintSourceTimeframe(displayTimeframe: String): Pair<String, Int> =
    when (displayTimeframe) {
        "1m" -> "1m" to 1
        "5m" -> "1m" to 5
        "15m" -> "15m" to 1
        "30m" -> "15m" to 2
        "1h" -> "15m" to 4
        "4h" -> "15m" to 16
        "1d" -> "15m" to 96
        "1w" -> "15m" to 672
        else -> "1m" to 1
    }
```

Сервер хранит footprint только за `1m` и `15m`, поэтому старшие таймфреймы
агрегируются на клиенте. Длительность source-свечи — `sourceTimeframeMs()`.

## 8.3. Выровненная агрегация (главный фикс)

Раньше свечи группировались `chunked(count)` — от первой свечи выборки.
Из-за этого 5m-свечи начинались в `05:26`, `05:31` и т.п.

Теперь группировка идёт по абсолютному времени:

```kotlin
val bucketMs = sourceMs * count
val bucketStart = candle.startTime / bucketMs * bucketMs   // floor к границе бакета
// startTime = bucketStart, endTime = bucketStart + bucketMs
```

Пример: source `1m`, count `5`, бакет 5 минут. Свечи `05:26` и `05:27`
попадают в бакет `05:25–05:30` — независимо от того, с какой свечи началась
выборка. Это покрыто тестом `aggregateFootprintCandles aligns buckets to
absolute time boundaries`.

## 8.4. Live-накопление из сделок (1m и 5m)

```kotlin
tradesAdapter.subscribeToTrades(symbol).collect { trade ->
    val candleStart = trade.timestamp / displayMs * displayMs

    if (lastCandleStart > 0L && candleStart != lastCandleStart) {
        val completed = liveCandle?.toFootprintCandle(tickCount)
        if (completed != null) {
            _state.update { it.copy(candles = it.candles + completed) }
            saveToCache(listOf(completed))
            if (aggCount == 1) {
                // для 1m серверная свеча авторитетнее локальной
                fetchCompletedCandle(lastCandleStart, candleStart)?.let { serverCandle -> ... }
            }
        }
        liveCandle = null
        tickCount = 0
    }

    val candle = liveCandle ?: MutableFootprintCandle(
        symbol = symbol,
        startTime = candleStart,
        endTime = candleStart + displayMs,
    ).also { liveCandle = it }

    candle.addTrade(trade.price.toFloat(), trade.quantity.toFloat(), !trade.isBuyerMaker)
    tickCount++
    _state.update { it.copy(liveCandle = candle.toFootprintCandle(tickCount), currentPrice = ...) }
}
```

Ключевые моменты:

- Накопитель создаётся **на бакет** и получает корректные `startTime/endTime`.
  Раньше `MutableFootprintCandle` создавался один раз с нулями, и все
  завершённые live-свечи имели `startTime = 0`.
- Для `1m` после закрытия минуты запрашивается серверная свеча и заменяет
  локальную (у сервера полнее лента).
- Для `5m` локальное накопление считается авторитетным.

## 8.5. Серверный polling (15m+) и формирующаяся свеча

Для старших таймфреймов live-лента не используется. Работают **два цикла**:

1. **Закрытые бакеты** — раз в `sourceMs` контроллер запрашивает окно
   source-свечей, находит чанк, выровненный по границе display-бакета,
   агрегирует и обновляет/добавляет свечу в списке. Граница бакета —
   `FootprintAggregator.bucketStart(now, sourceMs, aggCount)` (общая с
   агрегацией функция выравнивания по абсолютному времени).
2. **Формирующаяся свеча** — раз в ~30 секунд запрашивается текущий
   (незакрытый) бакет, агрегируется и кладётся в `liveCandle`. UI мержит
   `liveCandle` поверх completed по `startTime`, поэтому видна текущая
   свеча ещё до её закрытия. Когда бакет закрывается (или его подхватывает
   первый цикл), `liveCandle` очищается.

Оба цикла отменяются в `stop()`/`dispose()`.

## 8.6. Пагинация footprint

`FootprintController.loadMore()`:

```kotlin
val oldestTime = _state.value.candles.firstOrNull()?.startTime ?: return
val (sourceTf, aggCount) = FootprintAggregator.resolveFootprintSourceTimeframe(displayTimeframe)
val sourceMs = FootprintAggregator.sourceTimeframeMs(sourceTf)

val historical = (footprintApiClient?.getFootprint(
    symbol = symbol, timeframe = sourceTf, to = oldestTime - 1, limit = 20 * aggCount
) ?: emptyList()).reversed()

val aggregated = if (aggCount > 1)
    FootprintAggregator.aggregateFootprintCandles(historical, aggCount, sourceMs)
else historical

_state.update { it.copy(candles = (aggregated + it.candles).distinctBy { c -> c.startTime }) }
saveToCache(aggregated)
```

## 8.7. Ликвидации

```
LiquidationAdapter (Binance/MEXC)
        │  getHistoricalLiquidations + subscribeToLiquidations
        ▼
LiquidationSeriesSource (features:chart/indicator)
        │  loadInitial = окно последнего часа, loadBefore = пусто,
        │  mergeItem = append + cap 1000
        ▼
TimeSeriesController<LiquidationOrder>
        ▼
LiquidationViewModel.state: LiquidationState(orders, connected, error)
```

Один и тот же `TimeSeriesController` обслуживает и свечи, и ликвидации —
это и есть «обобщённое поведение» вместо дублирования логики под MEXC.

## 8.8. Дисковый кэш: интерфейсы и SQLite

Интерфейсы в `platform-core`:

```kotlin
interface CandleCacheStore {
    suspend fun saveCandles(exchange: String, symbol: String, timeframe: String, candles: List<Candle>)
    suspend fun getCandles(exchange: String, symbol: String, timeframe: String, limit: Int = 500): List<Candle>
}

interface FootprintCacheStore {
    suspend fun saveFootprintCandles(exchange: String, symbol: String, candles: List<FootprintCandle>)
    suspend fun getFootprintCandles(exchange: String, symbol: String, limit: Int = 200): List<FootprintCandle>
}
```

`LocalStorage` (SQLite, `~/.nous/storage.db`) реализует их. Схема:

```sql
CREATE TABLE candles_cache (
    exchange TEXT NOT NULL, symbol TEXT NOT NULL, timeframe TEXT NOT NULL,
    timestamp INTEGER NOT NULL, open REAL, high REAL, low REAL, close REAL, volume REAL,
    PRIMARY KEY (exchange, symbol, timeframe, timestamp)
);

CREATE TABLE footprint_cache (
    exchange TEXT NOT NULL, symbol TEXT NOT NULL,
    start_time INTEGER NOT NULL, end_time INTEGER NOT NULL, json_data TEXT NOT NULL,
    PRIMARY KEY (exchange, symbol, start_time)
);
```

**Миграция.** Если БД была создана до рефакторинга (без `exchange`),
`LocalStorage` при открытии пересоздаёт таблицы с новой схемой и переносит
старые строки, помечая их как `Binance`. Это проверено тестом
`migrates legacy schema adding exchange column`.

**Sync-политика:**

| Событие | Что пишем |
|---|---|
| Свечи: любое обновление `TimeSeriesState` | `saveCandles` (не чаще 30 c, последние 500) |
| Footprint: загрузили историю / догрузили старые | `saveFootprintCandles` пачкой |
| Footprint: закрылась live-свеча | `saveFootprintCandles` одной свечой |
| Footprint: polling 15m+ | `saveFootprintCandles` обновлённой свечой |
| Старт панели | preload из кэша (если ещё нет свежих данных) |

**Очистка.** `Settings → Storage` показывает статистику по
`exchange + symbol (+ timeframe)` и умеет чистить: конкретную строку,
данные старше N дней, либо всё. `getDetailedStats()` группирует напрямую
по таблицам и возвращает `CacheStats(key, symbol, count, firstTs, lastTs,
sizeBytes, durationMs, exchange)`.

---

# Часть III. Состояние и ViewModel

# 9. MVI: ChartIntent, ChartUiState и dispatch <a name="9"></a>

## 9.1. Проблема, которую решали

До рефакторинга `ChartViewModel` отдавал наружу **17 отдельных `StateFlow`**
(`chartState`, `currentSymbol`, `currentTimeframe`, `symbols`,
`currentSymbolFormatter`, `historyLoadCount`, `hasMoreHistory`,
`footprintCandles`, `liveFootprintCandle`, `footprintCurrentPrice`,
`footprintLoading`, `footprintError`, `chartMode`, `symbolsWithFootprint`,
`fpAggregation`, `hasMoreFootprintHistory`, `footprintHistoryLoadCount`).
`ChartWindowContent` собирал их семнадцатью `collectAsState()`, а снаружи
модуля каждый потребитель вынужден был знать, какой именно поток ему нужен.

## 9.2. Единое состояние

```kotlin
data class ChartUiState(
    val chartState: ChartState = ChartState.Loading,
    val currentSymbol: String = "BTCUSDT",
    val currentTimeframe: String = "1h",
    // TODO this hardcode need change to repository/datalayer initialisation symbol list
    val symbols: List<String> = listOf("BTCUSDT", "ETHUSDT"),
    val currentSymbolFormatter: SymbolFormatter = SymbolFormatter(),
    val historyLoadCount: Int = 0,
    val hasMoreHistory: Boolean = true,
    val footprintCandles: List<FootprintCandle> = emptyList(),
    val liveFootprintCandle: FootprintCandle? = null,
    val footprintCurrentPrice: Float? = null,
    val footprintLoading: Boolean = false,
    val footprintError: String? = null,
    val chartMode: ChartMode = ChartMode.CANDLESTICK,
    val symbolsWithFootprint: Set<String> = emptySet(),
    val fpAggregation: AggregationLevel = AggregationLevel.BaseTick,
    val hasMoreFootprintHistory: Boolean = true,
    val footprintHistoryLoadCount: Int = 0,
)
```

В VM остаётся один поток:

```kotlin
private val _state = MutableStateFlow(ChartUiState())
val state: StateFlow<ChartUiState> = _state.asStateFlow()
```

Все внутренние обновления — атомарные `_state.update { it.copy(...) }`.

**Для Junior.** `MutableStateFlow.update` принимает лямбду и может быть
перезапущен, если другой поток успел изменить значение. Поэтому в лямбде
нельзя делать side effects — только вычислять новое состояние.

## 9.3. Интенты

```kotlin
sealed interface ChartIntent {
    data class SelectSymbol(val symbol: String) : ChartIntent
    data class SelectTimeframe(val timeframe: String) : ChartIntent
    data object ToggleChartMode : ChartIntent
    data class SetFpAggregation(val level: AggregationLevel) : ChartIntent

    /** null — оставить текущие значения. */
    data class LoadChart(val symbol: String? = null, val timeframe: String? = null) : ChartIntent

    data object LoadMoreHistory : ChartIntent
    data object LoadMoreFootprintHistory : ChartIntent
    data object RestoreState : ChartIntent
}
```

Публичный API ViewModel — строго MVI:

```kotlin
class ChartViewModel(...) : Disposable {
    val state: StateFlow<ChartUiState>
    fun dispatch(intent: ChartIntent) { ... }
    override fun dispose() { ... }
}
```

Никаких публичных `loadChart()`, `selectSymbol()` и т.п. больше нет —
все действия идут через `dispatch`. Внутри `dispatch` — обычный `when`
по типам интентов, который вызывает приватные обработчики.

## 9.4. Как это выглядит в UI

```kotlin
val uiState by chartViewModel.state.collectAsState()
...
CandleStickChart(
    candles = state.candles,
    config = chartConfig,
    onNeedMoreHistory = { chartViewModel.dispatch(ChartIntent.LoadMoreHistory) },
    ...
)

ChartToolbar(
    currentSymbol = uiState.currentSymbol,
    onSymbolChange = { chartViewModel.dispatch(ChartIntent.SelectSymbol(it)) },
    onChartModeToggle = { chartViewModel.dispatch(ChartIntent.ToggleChartMode) },
    ...
)
```

Значения передаются вниз аргументами, действия — лямбдами с `dispatch`.
`ChartWindowContent` больше не «простыня из стейтов», а один `uiState`.

## 9.5. Миграция внешних потребителей

`composeApp` синхронизирует панель воркспейса с VM. Раньше:

```kotlin
LaunchedEffect(Unit) {
    var skipInitial = true
    vm.currentSymbol.collect { s -> ... }
}
```

Теперь:

```kotlin
LaunchedEffect(Unit) {
    var skipInitial = true
    vm.state.map { it.currentSymbol }.distinctUntilChanged().collect { s -> ... }
}
```

`skipInitial` остался: первый эмит — текущее значение, его не нужно
записывать обратно в конфиг панели. Аналогично для таймфрейма и режима.
Чтение текущих значений — `vm.state.value.currentSymbol` и т.д.

## 9.6. Почему strict MVI

- Одно место, где меняется состояние, — легче отлаживать («кто поменял
  `chartMode`?» → только обработчик `ToggleChartMode`).
- `ChartUiState` — обычный `data class`: его легко копировать, тестировать
  и передавать.
- Новые поля не требуют нового `StateFlow` и нового `collectAsState`.

---

# 10. Свечи: загрузка, realtime и ленивая история <a name="10"></a>

## 10.1. loadChart

```kotlin
private fun loadChart(ticker: String, timeframe: String) {
    _state.update {
        it.copy(
            currentSymbol = ticker,
            currentTimeframe = timeframe,
            hasMoreHistory = true,
            historyLoadCount = 0,
        )
    }
    startCandleSeries(ticker, timeframe)

    if (_state.value.chartMode == ChartMode.FOOTPRINT) {
        footprintController.start(_state.value.currentSymbol, _state.value.currentTimeframe)
    }
}
```

Что было исправлено относительно старой версии:

- убраны `delay(100)` и двойная установка `Loading` (это была гонка-хак);
- `currentJob?.cancel()` заменён на управление контроллером;
- при смене символа/ТФ footprint перезапускается, только если режим активен.

## 10.2. startCandleSeries

```kotlin
private fun startCandleSeries(ticker: String, timeframe: String) {
    candleStateJob?.cancel()
    candleController?.dispose()

    _state.update { it.copy(chartState = ChartState.Loading) }

    // 1. preload из кэша (см. главу 7.7)

    val controller = TimeSeriesController(
        source = chartRepository.candleSource(ticker, timeframe),
        scope = viewModelScope,
    )
    candleController = controller

    candleStateJob = viewModelScope.launch {
        controller.state.collect { series ->
            val error = series.error
            _state.update { s ->
                val chartState = when {
                    error != null && series.items.isEmpty() -> ChartState.Error(error)
                    series.items.isNotEmpty() -> ChartState.Success(series.items, series.items.last().close)
                    series.loading -> ChartState.Loading
                    else -> s.chartState
                }
                s.copy(
                    chartState = chartState,
                    hasMoreHistory = series.hasMore,
                    historyLoadCount = series.loadCount,
                )
            }
            scheduleCacheWrite(ticker, timeframe, series.items)
        }
    }

    controller.start()
}
```

Обратите внимание на правило: ошибка показывается только если **нечего
рисовать** (`items.isEmpty()`). Если realtime-подписка отвалилась после
загрузки истории, график остаётся на экране.

## 10.3. Полный цикл загрузки свечей

```
dispatch(LoadChart) ─► state: symbol/tf, Loading
        │
        ├─► cache.getCandles(...)  ──► Success(cached)  (если ещё Loading)
        │
        └─► TimeSeriesController.start()
                │
                ├─ CandleSeriesSource.loadInitial()  ──► Success(fresh)
                │
                └─ liveUpdates().collect { mergeItem } ──► Success(updated)
                        │
                        └─► scheduleCacheWrite (раз в 30 c)
```

## 10.4. Смена символа и таймфрейма

`selectSymbol` обновляет символ и форматтер (из `_symbolInfoMap`, который
заполняется `loadSymbols()`), сохраняет состояние и перезагружает график.
`selectTimeframe` делает то же для таймфрейма. Оба действия предварительно
зовут `saveState()`.

## 10.5. Восстановление состояния

```kotlin
private fun restoreState() {
    val persistor = persistor ?: return
    viewModelScope.launch {
        val saved = persistor.restore()
        _state.update { s ->
            s.copy(
                currentSymbol = saved.symbol ?: s.currentSymbol,
                currentTimeframe = saved.timeframe ?: s.currentTimeframe,
                chartMode = saved.chartMode ?: s.chartMode,
                fpAggregation = saved.fpAggregation ?: s.fpAggregation,
            )
        }
    }
}
```

`ChartStatePersistor` инкапсулирует ключи `chart_symbol`, `chart_timeframe`,
`chart_mode`, `fp_aggregation` и умеет безопасно парсить значения
(битый режим → `CANDLESTICK`, битая агрегация → `BaseTick`). Легаси-значения
вроде `"10x"` для агрегации поддерживаются через `AggregationLevel.fromString`.

---

# 11. FootprintController: история, live-накопление, polling <a name="11"></a>

## 11.1. Зачем отдельный контроллер

Footprint-логика — самая объёмная в модуле (~400 строк): REST-история,
агрегация, live-накопление из сделок, два вида polling и пагинация.
Держать это в `ChartViewModel` означало, что VM знает и про свечи, и про
footprint, и про UI-состояние. После рефакторинга VM только оркестрирует.

## 11.2. Состояние

```kotlin
data class FootprintUiState(
    val candles: List<FootprintCandle> = emptyList(),
    val liveCandle: FootprintCandle? = null,
    val currentPrice: Float? = null,
    val loading: Boolean = false,
    val error: String? = null,
    val hasMoreHistory: Boolean = true,
    val historyLoadCount: Int = 0,
)
```

VM мержит его в `ChartUiState` одним коллектором:

```kotlin
init {
    viewModelScope.launch {
        footprintController.state.collect { fp ->
            _state.update {
                it.copy(
                    footprintCandles = fp.candles,
                    liveFootprintCandle = fp.liveCandle,
                    footprintCurrentPrice = fp.currentPrice,
                    footprintLoading = fp.loading,
                    footprintError = fp.error,
                    hasMoreFootprintHistory = fp.hasMoreHistory,
                    footprintHistoryLoadCount = fp.historyLoadCount,
                )
            }
        }
    }
    loadSymbols()
    loadFootprintSymbols()
}
```

## 11.3. Публичный API контроллера

```kotlin
class FootprintController(
    private val footprintApiClient: FootprintApiClient?,
    private val tradesAdapter: TradesAdapter?,
    private val footprintCache: FootprintCacheStore? = null,
) : Disposable {
    val state: StateFlow<FootprintUiState>

    fun start(symbol: String, displayTimeframe: String)
    fun stop()
    fun loadMore()
    override fun dispose()
}
```

- `start` вызывается при включении режима и при смене символа/ТФ в режиме
  footprint. Внутри: сброс состояния, preload кэша, история с сервера,
  затем live-ветка или polling.
- `stop` отменяет **оба** job'а — основной и минутный polling.
- `loadMore` защищён `isLoadingMore`/`hasMoreHistory`, как и раньше.

## 11.4. Ветвление: лента или polling

```kotlin
val (sourceTf, aggCount) = FootprintAggregator.resolveFootprintSourceTimeframe(displayTimeframe)
val isLiveTrades = sourceTf == "1m" && tradesAdapter != null
```

| Display TF | Source | Как обновляется live |
|---|---|---|
| `1m` | `1m` | лента сделок + серверная свеча по закрытию минуты |
| `5m` | `1m` | лента сделок (локально авторитетно) |
| `15m` | `15m` | polling раз в 15 минут |
| `30m`, `1h`, `4h`, `1d`, `1w` | `15m` | polling раз в 15 минут, агрегация на клиенте |

## 11.5. Ошибки

Если нет ни ленты, ни API, контроллер сохраняет прежнее поведение:
выставляет ошибку и пытается отдать данные из кэша/БД
(`loadData()` → `"No footprint data in DB"`). Все сетевые ошибки —
`println` + `error` в состоянии (техдолг: нужен логгер).

---

# 12. LiquidationViewModel, кэш и жизненный цикл <a name="12"></a>

## 12.1. LiquidationViewModel

```kotlin
class LiquidationViewModel(
    private val liquidationAdapter: LiquidationAdapter?
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var controller: TimeSeriesController<LiquidationOrder>? = null
    private var stateJob: Job? = null

    private val _state = MutableStateFlow(LiquidationState())
    val state: StateFlow<LiquidationState> = _state.asStateFlow()

    fun subscribe(symbol: String) { ... }
    fun unsubscribe() { ... }
    fun clear() { unsubscribe(); scope.cancel() }
}
```

`subscribe(symbol)` пересоздаёт контроллер:

```kotlin
val seriesController = TimeSeriesController(
    source = LiquidationSeriesSource(adapter, symbol),
    scope = scope,
)
stateJob = scope.launch {
    seriesController.state.collect { series ->
        _state.value = _state.value.copy(
            orders = series.items,
            connected = series.error == null,
            error = series.error,
        )
    }
}
seriesController.start()
```

Было: ручной `subscriptionJob` + `getHistoricalLiquidations` + `collect`
с копированием списка `toMutableList()` на каждое событие. Стало: один
контроллер и `mergeItem`, который добавляет ордер и обрезает список до 1000.

## 12.2. Жизненный цикл в UI

`ChartWindowContent`:

```kotlin
LaunchedEffect(uiState.currentSymbol) {
    liquidationViewModel.subscribe(uiState.currentSymbol)
}
DisposableEffect(Unit) {
    onDispose { liquidationViewModel.clear() }
}
```

При смене символа ликвидации переподключаются, при уходе экрана — VM
очищается.

## 12.3. Жизненный цикл ChartViewModel

- `ChartViewModel` реализует `Disposable`. `dispose()` отменяет
  `candleStateJob`, `candleController`, `footprintController` и
  `viewModelScope`.
- В workspace-режиме `TabManager.closeWorkspace()` проходит по
  `liveViewModels` и вызывает `dispose()` у всех `Disposable` — панели
  закрываются вместе с воркспейсом.
- `main.kt` при закрытии панели удаляет VM из `liveViewModels` и тоже
  вызывает `dispose()`.

## 12.4. Кэш в жизненном цикле

Кэш не держит собственный scope: записи запускаются в `viewModelScope`
(свечи) и в scope `FootprintController` (footprint). Если VM умерла —
записи отменяются. Это нормально: данные на диске могли не сохраниться
за последние 30 секунд, но при следующем открытии панели история
подтянется с биржи.

---

# Часть IV. UI-слой

# 13. ChartWindow и ChartWindowContent <a name="13"></a>

## 13.1. Два overload'а

```kotlin
// 1. Preview/isolated: сам достаёт VM из Koin и восстанавливает состояние
@Composable
fun ChartWindow() {
    val chartViewModel: ChartViewModel = koinInject()
    val liquidationViewModel: LiquidationViewModel = koinInject()
    val previewScope = rememberCoroutineScope()

    // Восстановление из той же БД, что и у composeApp (символ/ТФ/режим/зум)
    val persistor = remember {
        runCatching { GlobalContext.getOrNull()?.get<StateStore>() }
            .getOrNull()?.let { ChartStatePersistor(it) }
    }
    var initialZoom by remember { mutableStateOf(1f) }

    LaunchedEffect(Unit) {
        chartViewModel.dispatch(ChartIntent.RestoreState)
        val saved = persistor?.restore()
        initialZoom = persistor?.restoreZoom() ?: 1f
        chartViewModel.dispatch(ChartIntent.LoadChart(saved?.symbol, saved?.timeframe))
    }

    ChartWindowContent(
        chartViewModel = chartViewModel,
        liquidationViewModel = liquidationViewModel,
        initialZoomLevel = initialZoom,
        onZoomChange = { zoom -> persistor?.let { p -> previewScope.launch { p.saveZoom(zoom) } } },
    )
}

// 2. Встраивание: VM приходит аргументом (+ привязка к workspace/панели)
@Composable
fun ChartWindow(
    chartViewModel: ChartViewModel,
    modifier: Modifier = Modifier,
    initialZoomLevel: Float = 1f,
    onZoomChange: ((Float) -> Unit)? = null,
    workspaceId: String? = null,
    panelId: String? = null,
) {
    val liquidationViewModel: LiquidationViewModel = koinInject()
    ChartWindowContent(...)
}
```

`workspaceId`/`panelId` нужны для персистента рисунков; в legacy `MainScreen`
они не передаются, и рисование живёт только в памяти панели.

## 13.2. ChartWindowContent

### 13.2.1. Один uiState

```kotlin
val uiState by chartViewModel.state.collectAsState()
```

Дальше UI использует `uiState.chartState`, `uiState.chartMode`,
`uiState.footprintCandles` и т.д. Конфиг:

```kotlin
val chartConfig = remember(uiState.fpAggregation, uiState.currentSymbolFormatter) {
    DefaultChartConfig.copy(
        footprintConfig = DefaultChartConfig.footprintConfig.copy(
            aggregationLevel = uiState.fpAggregation,
            tickSize = uiState.currentSymbolFormatter.tickSize
        ),
        priceFormatter = uiState.currentSymbolFormatter
    )
}
```

### 13.2.2. Ликвидации и индикаторная панель

```kotlin
val liquidationState by liquidationViewModel.state.collectAsState()
LaunchedEffect(uiState.currentSymbol) {
    liquidationViewModel.subscribe(uiState.currentSymbol)
}
DisposableEffect(Unit) { onDispose { liquidationViewModel.clear() } }

val indicatorRenderers = remember(liqOrders) {
    listOf<DrawScope.(Rect, List<Candle>, PriceRange, Float, Float) -> Unit>(
        { area, candles, _, scroll, zoom ->
            drawLiquidationHistogram(area, candles, liqOrders, scroll, zoom)
        }
    )
}
```

Индикаторная панель — это просто список `DrawScope`-лямбд; так же
подключается любой будущий индикатор (см. рецепт 33.1).

### 13.2.3. Рисование и персистент

```kotlin
val drawingStore: StateStore? = remember {
    runCatching { org.koin.core.context.GlobalContext.getOrNull()?.get<StateStore>() }.getOrNull()
}
val drawingHistory = remember { DrawingHistory() }
var activeDrawingTool by remember { mutableStateOf(DrawingToolType.NONE) }

LaunchedEffect(workspaceId, panelId, drawingStore) {
    val store = drawingStore
    if (workspaceId == null || panelId == null || store == null) return@LaunchedEffect
    val repository = DrawingRepository(store)
    drawingHistory.replaceAll(repository.load(workspaceId, panelId))
    snapshotFlow { drawingHistory.drawings.toList() }
        .collect { repository.save(workspaceId, panelId, it) }
}
```

`snapshotFlow` подписывается на изменения Compose-состояния
`drawingHistory.drawings` и сохраняет рисунки при каждом изменении
(добавление, undo, redo, удаление).

**Технический долг.** `GlobalContext` — обращение к Koin из UI. В preview
`StateStore` не зарегистрирован, поэтому используется `runCatching`.
Чище — передавать `DrawingRepository?` аргументом из `main.kt`.

### 13.2.4. Основной when по состоянию

```kotlin
when (val state = uiState.chartState) {
    is ChartState.Loading -> { /* "Loading chart data..." */ }
    is ChartState.Error   -> { /* "Error loading chart" + message */ }
    is ChartState.Success -> {
        when (uiState.chartMode) {
            ChartMode.CANDLESTICK -> CandleStickChart(...)
            ChartMode.FOOTPRINT   -> { /* loading/error/график */ }
        }
        ChartToolbar(...)
    }
}
```

### 13.2.5. Footprint-ветка: слияние live и completed

```kotlin
val allFp = buildList {
    val liveStart = uiState.liveFootprintCandle?.startTime
    addAll(uiState.footprintCandles.filter { it.startTime != liveStart })
    uiState.liveFootprintCandle?.let { add(it) }
}
val fpToCandle = remember(allFp) {
    allFp.map { it.toSkeletonCandle() }
}
```

Live-свеча перетирает completed с тем же `startTime`, а из footprint-свечей
строится «каркас» `Candle` (`toSkeletonCandle`) — его движок использует
для скролла, зума, шкалы времени и autoscale.

### 13.2.6. Preview main()

```kotlin
fun main() = application {
    stopKoin(); initKoinForPreview()
    Window(onCloseRequest = ::exitApplication, title = "Nous Platform • Chart Preview", ...) {
        KoinContext { TradingTerminalTheme { ChartWindow() } }
    }
}
```

---

# 14. ChartInteraction: layout, состояния, Canvas-конвейер <a name="14"></a>

## 14.1. Публичный API CandleStickChart

`CandleStickChart` — тонкая обёртка:

```kotlin
@Composable
fun CandleStickChart(
    candles: List<Candle>,
    currentPrice: Float? = null,
    modifier: Modifier = Modifier,
    config: ChartConfig = DefaultChartConfig,
    crosshairEnabled: Boolean = false,
    onNeedMoreHistory: () -> Unit = {},
    historyLoadCount: Int = 0,
    hasMoreHistory: Boolean = true,
    footprintCandles: List<FootprintCandle>? = null,
    liquidationOrders: List<LiquidationOrder> = emptyList(),
    indicatorRenderers: List<DrawScope.(Rect, List<Candle>, PriceRange, Float, Float) -> Unit> = emptyList(),
    indicatorHeightDp: Dp = 80.dp,
    drawingHistory: DrawingHistory? = null,
    activeDrawingTool: DrawingToolType = DrawingToolType.NONE,
    onActiveDrawingToolChange: (DrawingToolType) -> Unit = {},
    initialZoomLevel: Float = 1f,
    onZoomChange: ((Float) -> Unit)? = null,
)
```

После рефакторинга из API убраны:

- `showPriceScale`/`priceScaleWidth` — берутся из `config`;
- `onCrosshairEnabledChange` — не использовался;
- `chartWidth` у `findNearestCandleIndex` — не использовался.

## 14.2. Модель движка: шкалы и серия

По образцу lightweight-charts движок разделён на три части:

```kotlin
// Горизонтальная шкала: скролл/зум/index↔x (state — Compose snapshot)
val timeScale = remember { TimeScale(initialZoom = initialZoomLevel) }

// Вертикальная шкала: базовый диапазон + сдвиг при вертикальном скролле
val priceScale = remember { PriceScale() }

// Серия: данные + рендер. Свечи и footprint — две реализации одного контракта
val series: ChartSeries = remember(candles, footprintCandles, config) {
    if (footprintCandles != null) {
        FootprintSeries(skeleton = candles, footprintCandles = footprintCandles, config = config)
    } else {
        CandlestickSeries(skeleton = candles, config = config)
    }
}
```

UI-состояния жестов остались у движка:

```kotlin
var mousePosition by remember { mutableStateOf<Offset?>(null) }
var isCrosshairVisible by remember { mutableStateOf(false) }
var chartWidthPx by remember { mutableFloatStateOf(0f) }
var chartHeightPx by remember { mutableFloatStateOf(0f) }
var verticalScroll by remember { mutableFloatStateOf(0f) }   // footprint, Alt+drag
var isCtrlPressed by remember { mutableStateOf(false) }
var isAltPressed by remember { mutableStateOf(false) }
var footprintHoverPos by remember { mutableStateOf<Offset?>(null) }
```

Границы зума — из конфига (позже станут сериализуемыми):

```kotlin
val minZoom = config.minZoom                    // 0.05
val maxZoom = if (footprintCandles != null) config.maxZoomFootprint else config.maxZoom  // 30 / 4
```

`mutableFloatStateOf` — специализированное состояние для `Float`:
меньше боксинга, чем `mutableStateOf<Float>`.

**Почему так.** Раньше у footprint был собственный движок с копией
скролла/зума/layout (`FootprintChart.kt`). Теперь `FootprintSeries` — это
просто серия того же движка: «footprint расширяет chart», а не дублирует.

## 14.3. Layout

```kotlin
val layout = remember(config.priceScaleWidth, canvasWidth, canvasHeight,
                     indicatorRenderers.size, indicatorHeightDp) {
    val chartMainArea = Rect(0f, 0f, widthPx - priceScaleWidthPx - chartPadding,
                             heightPx - timeScaleHeight - indicatorTotalH)
    val priceScaleArea = Rect(widthPx - priceScaleWidthPx, chartMainArea.top,
                              widthPx, chartMainArea.bottom)
    val timeScaleArea = Rect(0f, heightPx - timeScaleHeight, chartMainArea.right, heightPx)
    val indicatorAreas = ...
    ChartLayout(...)
}
```

`priceScaleArea` выровнен по вертикали с `chartMainArea` — это и есть фикс
«шкала цен не совпадает с crosshair».

## 14.4. Видимые свечи и autoscale

```kotlin
val startIdx = (clampedOffset / totalW).toInt().coerceIn(0, max(0, candles.size - 1))
val endIdx = ((clampedOffset + chartWidthPx) / totalW + 1).toInt().coerceIn(startIdx + 1, candles.size)

// Autoscale по видимым свечам; текущая цена НЕ влияет на диапазон
priceScale.fit { series.priceRange(startIdx, endIdx) }
val priceRange = priceScale.range(verticalScroll, chartHeightPx)

// Линия и badge текущей цены — только если цена попадает в видимый диапазон
val visibleCurrentPrice = currentPrice?.takeIf { it in priceRange.visibleMin..priceRange.visibleMax }
```

Почему autoscale именно так:

- Раньше ключа `candles` не было у `remember`, и график считал диапазон по
  **старому** списку после смены символа — «горизонтальная линия».
- Текущая цена в диапазоне: при уходе далеко в историю она растягивала
  `PriceRange` на сотни процентов, и видимые свечи сжимались в полоску.
  Поэтому масштаб — только по видимым свечам, а линия/badge цены
  показываются лишь когда цена в диапазоне.
- Сдвиг `verticalScroll` применяется только к диапазону (footprint).

## 14.5. Порядок отрисовки Canvas

```kotlin
Canvas(Modifier.fillMaxSize().clipToBounds()) {
    // 1. Серия (свечи или footprint)
    series.draw(this, chartCanvas)

    // 2. Пунктирная линия текущей цены (только footprint, alpha 0.5)
    // 3. Шкала времени (по каркасным свечам — общая для обоих режимов)
    // 4. Маркеры ликвидаций
    // 5. Индикаторные панели + разделители
    // 6. Шкала цен (+ разделительная линия)
    // 7. Alt+hover popup footprint
    // 8. Рисунки пользователя
    // 9. Crosshair: footprint-панель (O/H/L/C/Ticks) или свечная (Time/O/H/L)
}
```

Порядок важен: поздние слои рисуются поверх ранних. Crosshair — всегда
последний, чтобы не перекрываться свечами. Серия ничего не знает про
порядок: она получает готовый `ChartCanvas` (layout + шкалы + диапазон).

## 14.6. Технический долг

- `chartWidthPx`/`chartHeightPx` пишутся прямо в композиции.
  Правильнее — `derivedStateOf` или `remember`.
- `priceScale.fit { ... }` — мутация в композиции (идемпотентная, но
  не чистая).
- `visibleCandles` создаёт новый `subList` при каждом изменении индексов.
- Планы на фазу F: `ChartPanePrimitive` с zOrder/hitTest (рисунки,
  ликвидации, линия цены, индикаторы) и multi-pane с общим `TimeScale`.

---

# 15. Скролл, зум, crosshair и ленивая загрузка — подробно <a href="#15"></a>

## 15.1. Модель скролла

График — «виртуальная лента» шириной `candles.size * totalW`, где
`totalW = candleMetrics.width + candleMetrics.spacing`. `scrollOffset` —
смещение левого края видимой области.

```kotlin
val maxScroll = timeScale.maxScroll(candles.size, chartWidthPx)
val clampedOffset = timeScale.scrollOffset.coerceIn(-TimeScale.MAX_SCROLL_LEFT, maxScroll)
```

`TimeScale.MAX_SCROLL_LEFT = 300f` — запас «пустой зоны» слева, в которую
можно проскроллить, чтобы инициировать подгрузку истории.

## 15.2. Панорамирование и вертикальный скролл

```kotlin
.pointerInput(crosshairEnabled) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (crosshairEnabled) {
            // жест — crosshair
        } else {
            var previous = down.position
            do {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull() ?: break
                if (!change.pressed) break

                val alt = event.keyboardModifiers.isAltPressed
                val deltaX = change.position.x - previous.x
                val deltaY = change.position.y - previous.y
                previous = change.position

                if (alt && footprintCandles != null) {
                    // Вертикальный скролл уровней footprint (Alt+drag)
                    verticalScroll = (verticalScroll + deltaY)
                        .coerceIn(-chartHeightPx * 2f, chartHeightPx * 2f)
                } else {
                    timeScale.panBy(deltaX, currentCandles.size, chartWidthPx)
                }
                change.consume()
            } while (true)
        }
    }
}
```

Когда crosshair включён, drag отдаётся ему, а не панорамированию.
Вертикальный скролл активен только в footprint-режиме и сдвигает
диапазон цен (`PriceScale.range`), не трогая данные.

Перед панорамированием движок проверяет **hit-test по рисункам**
(см. главу 24.4): если под курсором фигура — drag её перемещает или
меняет размер; при активном инструменте рисования жесты обрабатывает
`DrawingOverlay`, и панорамирование отключается.

**Модификаторы — из pointer-событий.** `Ctrl`/`Alt` читаются через
`event.keyboardModifiers.isCtrlPressed/isAltPressed`, а не через
`onKeyEvent`-состояние: раньше после клика по тулбару фокус уходил
с графика и модификаторы «слетали» (Ctrl-зум, Alt-popup и Alt-вертикаль
переставали работать). `onKeyEvent` остался только для `Ctrl+Z/Y`
(undo/redo).

## 15.3. Зум

```kotlin
val factor = if (sd.y < 0) zoomStep else 1f / zoomStep
timeScale.zoomAt(
    factor = factor,
    mouseX = change.position.x,
    anchorAtMouse = isCtrlPressed,
    chartWidth = chartWidthPx,
    candleCount = currentCandles.size,
    minZoom = config.minZoom,
    maxZoom = if (footprintCandles != null) config.maxZoomFootprint else config.maxZoom,
)
```

Два режима зума:

- **обычный** — якорь на правом краю (самая новая видимая свеча);
- **`Ctrl+Zoom`** — якорь на точке под курсором.

Внутри `TimeScale.zoomAt` вызывает чистые `calculateZoomScrollOffset` и
`calculateMaxScroll` (глава 25.6), а главное — считает `maxScroll` для
**нового** зума. Кламп по устаревшему значению сдвигал якорь влево при
приближении — именно это ломало зум «от крайней правой свечи».

## 15.4. Crosshair

- Включается кнопкой в тулбаре (кнопка `⧉`).
- Пока включён — drag не панорамирует, а двигает перекрестие.
- В footprint-режиме показывается footprint-панель `O/H/L/C/Ticks`
  (`drawCrosshairForFootprint`), в свечах — `Time/O/H/L` (`drawCrosshair`).
- При зажатом `Alt` и наведении на footprint-график показывается popup
  с таблицей bid/ask по уровням. `Alt` читается из pointer-события
  (`keyboardModifiers.isAltPressed`), поэтому popup работает независимо
  от фокуса; цены/объёмы форматируются `config.priceFormatter`.
- `Ctrl+Z` / `Ctrl+Y` — undo/redo рисунков (через `onKeyEvent`).

## 15.5. Ленивая загрузка, follow live и удержание позиции

Один эффект на изменение списка свечей решает оба случая — подгрузку истории
и появление новой свечи:

```kotlin
val firstCandleTs = candles.firstOrNull()?.timestamp
var prevFirstTs by remember { mutableStateOf<Long?>(null) }

LaunchedEffect(candles.size, firstCandleTs) {
    val prepended = prependedCount(candles, prevFirstTs)
    if (prepended > 0) {
        // История: первая свеча стала старше → удерживаем позицию
        timeScale.offsetAfterPrepend(prepended, candles.size, chartWidthPx)
    } else if (timeScale.scrollOffset == 0f ||
        timeScale.isAtLatest(candles.size, chartWidthPx, tolerance = totalW)
    ) {
        // Новая свеча: следуем, если стоим у правого края
        timeScale.scrollToLatest(candles.size, chartWidthPx)
    }
    prevFirstTs = firstCandleTs
}
```

**Как различаются случаи без счётчиков.** Якорь — таймстамп первой свечи.
Если он стал старше (`prependedCount > 0`) — это подгрузка истории, и
`scrollOffset` сдвигается ровно на число добавленных свечей
(`prependedCount` находит прежнюю первую свечу в новом списке). Иначе это
append realtime-свечи — следуем за ней, только если пользователь у правого
края (`tolerance = totalW` — допуск одна свеча, иначе новая свеча увеличивает
`maxScroll` ровно на её ширину и «следование» терялось бы).

Раньше здесь были `loadGeneration`/`historyGeneration`/`loadCount` и два
отдельных эффекта; якорь по первой свече убрал всю обвязку счётчиков.

Ленивая загрузка (когда пользователь скроллит левее первой свечи):

```kotlin
LaunchedEffect(clampedOffset, hasMoreHistory) {
    if (hasMoreHistory && clampedOffset < 0f) onNeedMoreHistory()
}
```

**Автозаполнение вьюпорта.** Если восстановленный зум «вдаль» требует
больше свечей, чем загружено, движок догружает недостающие:

```kotlin
LaunchedEffect(candles.size, hasMoreHistory) {
    if (hasMoreHistory && candles.isNotEmpty() &&
        timeScale.maxScroll(candles.size, chartWidthPx) == 0f
    ) {
        onNeedMoreHistory()
    }
}
```

Условие `maxScroll == 0f` означает «свечей меньше, чем помещается на
экран» — подгрузка повторяется, пока вьюпорт не заполнится или не
закончится история.

## 15.6. Кнопка «к последней свече»

В правом нижнем углу области графика — кнопка `⇥`:

```kotlin
val controlsBottomPadding = with(density) {
    (layout.canvasHeight - layout.chartMainArea.bottom).toDp()
} + 8.dp
val isAtRightEdge = timeScale.isAtLatest(candles.size, chartWidthPx)
Box(
    modifier = Modifier
        .align(Alignment.BottomEnd)
        .padding(end = config.priceScaleWidth + 10.dp, bottom = controlsBottomPadding)
        .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
        .clickable { timeScale.scrollToLatest(candles.size, chartWidthPx) }
        .padding(horizontal = 8.dp, vertical = 3.dp)
) {
    Text(
        text = "\u21E5",
        color = if (isAtRightEdge) Color(0xFF6B7A88) else Color(0xFF5B9BD5),
        fontSize = 14.sp,
        fontFamily = FontFamily.Monospace,
    )
}
```

- отступы учитывают ширину шкалы цен и высоту шкалы времени/индикаторов —
  кнопка всегда в правом нижнем углу именно области графика;
- клик — `scrollToLatest` (переход к самой новой свече);
- цвет: синий, когда есть куда листать; серый — когда график уже у края.

## 15.7. Double-tap (footprint)

```kotlin
.pointerInput(footprintCandles) {
    if (footprintCandles != null) {
        detectTapGestures(onDoubleTap = {
            timeScale.setZoom(1f)
            timeScale.scrollToLatest(currentCandles.size, chartWidthPx)
            verticalScroll = 0f
        })
    }
}
```

Сброс зума/скролла/вертикального сдвига — поведение переехало из старого
`FootprintChart` в общий движок.

---

# 16. ChartToolbar: символ, режим, агрегация, рисование <a name="16"></a>

Тулбар — компактная строка поверх графика:

```
[Symbol ▾] [C/FP] [1x|10x|100x] [⧉] [1m 5m 15m 30m 1h 4h 1d 1w] [T H R V Δ] [↶ ↷]
```

## 16.1. Символ

`SymbolSearchDropdown` из `platform-core`: поиск по списку, подсветка
символов с footprint (`symbolsWithFootprint`).

## 16.2. Режим и агрегация

Кнопка `C`/`FP` переключает `ChartMode`; при footprint появляется селектор
агрегации `1x/10x/100x`, который меняет `ChartConfig.footprintConfig.aggregationLevel`.

## 16.3. Таймфреймы

`1m, 5m, 15m, 30m, 1h, 4h, 1d, 1w`. Маппинг в интервал биржи — `Timeframes`
(platform-core), `30m`/`1w` работают корректно.

## 16.4. Инструменты рисования

```kotlin
private val drawingTools = listOf(
    DrawingToolType.TREND_LINE to "T",
    DrawingToolType.HORIZONTAL to "H",
    DrawingToolType.RECTANGLE to "R",
    DrawingToolType.VERTICAL to "V",
    DrawingToolType.RULER to "\u0394",
)
```

Поведение:

- клик по инструменту активирует его (подсветка);
- повторный клик по активному — сбрасывает в `NONE`;
- `↶`/`↷` — undo/redo (`DrawingHistory.canUndo/canRedo`);
- после создания фигуры `DrawingOverlay` сам сбрасывает инструмент через
  `onActiveDrawingToolChange` (раньше колбэк не пробрасывался).

---

# 17. FootprintChart: тонкая обёртка для bidasker-web <a name="17"></a>

`ui/chart/FootprintChart.kt` — composable для `bidasker-web` (там нет
DOM/Trades и не нужен `ChartViewModel`). После фазы E это **тонкая обёртка
над общим движком** — собственного скролла/зума/layout в ней больше нет:

```kotlin
@Composable
fun FootprintChart(
    completedCandles: List<FootprintCandle>,
    liveCandle: FootprintCandle? = null,
    currentPrice: Float? = null,
    modifier: Modifier = Modifier,
    config: ChartConfig = DefaultChartConfig,
    crosshairEnabled: Boolean = false,
) {
    val allCandles = remember(completedCandles, liveCandle) {
        if (liveCandle != null) completedCandles + liveCandle else completedCandles
    }
    if (allCandles.isEmpty()) { /* "No footprint data available." */ return }

    // Footprint-свечи → каркасные Candle (модель/FootprintMapping)
    val skeleton = remember(allCandles) { allCandles.map { it.toSkeletonCandle() } }

    CandleStickChart(
        candles = skeleton,
        currentPrice = currentPrice ?: skeleton.lastOrNull()?.close,
        modifier = modifier,
        config = config,
        crosshairEnabled = crosshairEnabled,
        footprintCandles = allCandles,   // включает footprint-режим движка
        hasMoreHistory = false,          // пагинация — на стороне bidasker (DataLoader)
    )
}
```

Что это даёт:

- вертикальный скролл (`Alt+drag`), double-tap сброс, zум с Ctrl и кнопка
  `⇥` — автоматически работают и в bidasker, потому что живут в движке;
- footprint-crosshair (O/H/L/C/Ticks), popup, пунктирная линия цены —
  тоже из движка;
- кэш и пагинация — по-прежнему на стороне `bidasker-web` (`DataLoader`).

---

# Часть V. Рендеринг

# 18. CandleRenderer: свечи, сетка, линия цены <a name="18"></a>

## 18.1. drawChart

```kotlin
fun DrawScope.drawChart(
    candles: List<Candle>,
    priceRange: PriceRange,
    config: ChartConfig,
    chartArea: Rect,
    currentPrice: Float?,
    textMeasurer: TextMeasurer,
    scrollOffset: Float = 0f,
    zoomLevel: Float = 1f,
    visibleStartIndex: Int = 0,
    visibleEndIndex: Int = 0,
) {
    withTransform({
        translate(left = chartArea.left, top = chartArea.top)
        clipRect(0f, 0f, chartArea.width, chartArea.height)
    }) {
        drawGrid(config, chartArea.width, chartArea.height)
        // цикл только по видимым свечам
        for (i in visibleStartIndex until visibleEndIndex) { drawCandle(...) }
        if (currentPrice != null) drawCurrentPriceLine(...)
    }
}
```

`chartArea` теперь всегда `layout.chartMainArea` — свечи не заезжают под
шкалу времени и индикаторные панели.

**Для Junior.** `withTransform` сдвигает систему координат в левый верхний
угол области. Внутри можно рисовать так, будто область начинается в `(0,0)`,
а `clipRect` обрезает всё, что вылезло за границы.

## 18.2. drawCandle

Алгоритм:

1. `isBullish = close >= open`, выбираем цвет тела.
2. Тени (wick): от `highY` до верха тела и от низа тела до `lowY`.
3. Тело: `drawRect` высотой `|openY − closeY|`.
4. Если тело нулевое (doji) — рисуем горизонтальную линию.

## 18.3. drawGrid и drawCurrentPriceLine

- Сетка — 5 горизонтальных и 10 вертикальных линий.
- Линия текущей цены — пунктир (`PathEffect.dashPathEffect`), зелёная,
  во всю ширину `chartMainArea`.

---

# 19. ChartPriceScaleRenderer: шкала цен и badge <a name="19"></a>

## 19.1. drawPriceScale

```kotlin
fun DrawScope.drawPriceScale(
    priceRange: PriceRange,
    config: ChartConfig,
    priceScaleArea: Rect,
    currentPrice: Float?,
    textMeasurer: TextMeasurer
) {
    val priceLevels = generatePriceLevels(
        min = priceRange.visibleMin,
        max = priceRange.visibleMax,
        count = 8
    )
    // обычные уровни, кроме текущей цены
    // затем badge текущей цены поверх
}
```

`priceScaleArea.height` совпадает с высотой `chartMainArea` — поэтому цена
на шкале соответствует цене на свечах.

## 19.2. Форматтер

Все цены форматируются через `config.priceFormatter`:

```kotlin
val priceText = formatPrice(price, config.priceFormatter)
```

Это починило отображение низкоценовых инструментов: раньше везде был
`SymbolFormatter.DEFAULT` (2 знака), и на SHIB-подобных символах шкала
показывала `0.00`.

## 19.3. Badge текущей цены

```kotlin
fun DrawScope.drawCurrentPriceBadge(
    price: Float,
    y: Float,
    priceScaleWidth: Float,
    priceScaleHeight: Float,   // добавлен в рефакторинге
    textMeasurer: TextMeasurer,
    config: ChartConfig
)
```

Раньше кламп позиции badge использовал `size.height` (высота всего Canvas)
вместо высоты шкалы — badge мог уехать под график. Теперь передаётся
`priceScaleArea.height`.

`drawCurrentPriceLabel()` удалён как мёртвый код (нигде не вызывался).

---

# 20. ChartTimeScaleRenderer: шкала времени <a name="20"></a>

```kotlin
fun DrawScope.drawTimeScale(
    candles: List<Candle>,
    config: ChartConfig,
    timeScaleArea: Rect,
    textMeasurer: TextMeasurer,
    scrollOffset: Float = 0f,
    zoomLevel: Float = 1f,
)
```

Логика:

1. Фон и разделительная линия.
2. Метрики свечей → `totalW`.
3. Видимый диапазон индексов по `scrollOffset`.
4. Шаг меток `step = (visibleCount / 6).coerceAtLeast(1)` — примерно
   5–7 подписей на экран.
5. Для каждой метки — вертикальная черточка и `formatTime(timestamp)`
   (`HH:mm`, системная таймзона).

---

# 21. ChartCrosshairRenderer и ChartTextRenderer <a name="21"></a>

## 21.1. drawCrosshair

```kotlin
fun DrawScope.drawCrosshair(
    mousePosition: Offset,
    candles: List<Candle>,
    priceRange: PriceRange,
    config: ChartConfig,
    chartLayout: ChartLayout,
    textMeasurer: TextMeasurer,
    scrollOffset: Float = 0f,
    zoomLevel: Float = 1f,
)
```

Шаги:

1. Проверка, что курсор внутри `chartMainArea` — иначе выходим.
2. Две линии: вертикальная и горизонтальная.
3. `findNearestCandleIndex(mouseX, candles, scrollOffset, zoomLevel)` —
   параметр `chartWidth` удалён, он не использовался.
4. Маркеры high (красный) и low (зелёный).
5. Инфо-панель `Time/O/H/L` — цены через `config.priceFormatter`.
6. Ценовая метка на оси Y (правая сторона).
7. Временная метка на оси X (низ).

Все Y-координаты считаются по `chartMainArea.height` — тому же, по которому
рисуются свечи.

## 21.2. ChartTextRenderer

```kotlin
fun DrawScope.drawTextLine(text: String, x: Float, y: Float,
                           textMeasurer: TextMeasurer, color: Color)
```

Утилита для одной строки моноширинного текста 10sp. Используется
в crosshair-панели и в метках рисунков.

**Технический долг.** Текст меряется заново на каждом кадре. Для статичных
надписей имеет смысл кэшировать `TextLayoutResult` (например, в
`remember`/мапе по строке).

---

# 22. FootprintRenderer: кластерный график, popup, агрегация уровней <a name="22"></a>

## 22.1. drawFootprintChart

```kotlin
fun DrawScope.drawFootprintChart(
    candles: List<FootprintCandle>,
    priceRange: PriceRange,
    config: ChartConfig,
    chartArea: Rect,
    textMeasurer: TextMeasurer,
    scrollOffset: Float = 0f,
    zoomLevel: Float = 1f,
    visibleStartIndex: Int = 0,
    visibleEndIndex: Int = 0,
)
```

- Считает `viewportMaxVol` по видимым свечам — для нормировки градиента.
- Каждая свеча рисуется вдвое шире обычной (`CandleMetrics(fpWidth * 2f, ...)`).
- `drawFootprintCandle` для каждого ценового уровня рисует два
  горизонтальных бара: ask (вправо/красный) и bid (влево/зелёный),
  с alpha от объёма.

## 22.2. aggregateLevels

```kotlin
fun aggregateLevels(
    levels: List<FootprintLevel>,
    aggLevel: AggregationLevel,
    tickSize: Double
): List<FootprintLevel> {
    if (levels.isEmpty() || tickSize <= 0.0 || aggLevel == AggregationLevel.BaseTick) return levels

    val grouped = linkedMapOf<String, Accumulator>()
    for (level in levels) {
        val key = aggLevel.aggregationKey(level.price, tickSize)
        val acc = grouped.getOrPut(key) { Accumulator() }
        acc.bidVol += level.bidVolumeFloat
        acc.askVol += level.askVolumeFloat
        acc.bidCnt += level.bidCount
        acc.askCnt += level.askCount
    }
    return grouped.map { (price, acc) -> FootprintLevel(price = price, ...) }
}
```

Вызывается и в рендере, и в popup. **Технический долг:** вызывается каждый
кадр — результат стоит кэшировать по (candles, aggregationLevel, tickSize).

## 22.3. drawFootprintPopup (Alt+hover)

Показывает таблицу `Price | Ask | Bid` по ~22 уровням вокруг курсора:

- находит уровень, ближайший к цене под мышью;
- берёт окно уровней, разворачивает (сверху — большие цены);
- рисует фон, заголовок и строки с барами;
- позиционирует панель справа от свечи, а если не влезает — слева.

Цены и объёмы — через `SymbolFormatter` из `config.priceFormatter`.

## 22.4. Вспомогательные рендереры

- `drawCrosshairForFootprint` — crosshair с инфо-панелью `O/H/L/C/Ticks`;
  движок вызывает его в footprint-режиме вместо свечного `drawCrosshair`.
- Отдельной шкалы времени для footprint больше нет: движок рисует общую
  `drawTimeScale` по каркасным свечам (`toSkeletonCandle`), поэтому
  `drawTimeScaleForFootprint` удалён за ненадобностью.

---

# 23. LiquidationRenderer: маркеры и гистограмма <a name="23"></a>

## 23.1. Маркеры на графике

```kotlin
fun DrawScope.drawLiquidationMarkers(
    orders: List<LiquidationOrder>,
    priceRange: PriceRange,
    chartWidth: Float,
    chartHeight: Float,
    scrollOffset: Float,
    candles: List<Candle>,
    timeframeMs: Long,
    zoomLevel: Float
)
```

- `SELL` → красный треугольник вниз, `BUY` → зелёный вверх.
- Размер зависит от объёма, прозрачность — от возраста (старше 4 часов
  не рисуем).
- Позиция X — от времени ордера относительно первой свечи.

## 23.2. Гистограмма

```kotlin
fun DrawScope.drawLiquidationHistogram(
    area: Rect,
    candles: List<Candle>,
    orders: List<LiquidationOrder>,
    scrollOffset: Float,
    zoomLevel: Float
)
```

Это `indicatorRenderer` — рисуется в своей панели под графиком:
лонги (SELL) вверх от середины, шорты (BUY) вниз.

Мёртвая функция `timeframeToMs()` удалена — её заменил `Timeframes.millis()`.

---

# 24. Рисование: Drawing, DrawingRenderer, DrawingOverlay, DrawingRepository <a name="24"></a>

## 24.1. Модели и сериализация

```kotlin
@Serializable
sealed class Drawing {
    abstract val id: String
    abstract val color: Color
    abstract val createdAt: Long

    @Serializable data class TrendLine(..., val label: String? = null) : Drawing()
    @Serializable data class HorizontalLevel(..., val isDashed: Boolean = false) : Drawing()
    @Serializable data class Rectangle(...) : Drawing()
    @Serializable data class VerticalLine(...) : Drawing()
}
```

Compose `Color` не сериализуется «из коробки», поэтому написан
`ColorSerializer` (ARGB → `Long`):

```kotlin
object ColorSerializer : KSerializer<Color> {
    override val descriptor = PrimitiveSerialDescriptor("Color", PrimitiveKind.LONG)
    override fun serialize(encoder: Encoder, value: Color) = encoder.encodeLong(value.value.toLong())
    override fun deserialize(decoder: Decoder): Color = Color(decoder.decodeLong().toULong())
}
```

Аннотация ставится на конкретные свойства:
`@Serializable(with = ColorSerializer::class) override val color: Color`.

## 24.2. DrawingRenderer и метки

```kotlin
fun DrawScope.drawDrawings(
    drawings: List<Drawing>,
    candles: List<Candle>,
    priceRange: PriceRange,
    chartWidth: Float,
    chartHeight: Float,
    scrollOffset: Float,
    candleWidth: Float,
    candleSpacing: Float,
    textMeasurer: TextMeasurer,   // добавлен
)
```

Рендерится:

- `TrendLine` — линия, кружки-ручки на концах, **метка на середине**;
- `HorizontalLevel` — линия (опционально пунктир), **метка справа**;
- `Rectangle` — заливка + рамка;
- `VerticalLine` — линия, **метка сверху**.

Метка — это `drawRect` (тёмный фон) + `drawText`. Раньше метки были
заглушками «handled by composable text» и не рисовались вовсе.

## 24.3. DrawingOverlay и live-превью

Прозрачный слой, перехватывающий жесты при активном инструменте:

```kotlin
@Composable
fun DrawingOverlay(
    activeDrawingTool: DrawingToolType,
    drawingHistory: DrawingHistory?,
    candles: List<Candle>,
    priceRange: PriceRange,
    layout: ChartLayout,
    scrollOffset: Float,
    zoomLevel: Float,
    priceFormatter: SymbolFormatter = SymbolFormatter.DEFAULT,
    onToolChange: (DrawingToolType) -> Unit,
    onPreviewChange: (Drawing?) -> Unit = {},
    modifier: Modifier = Modifier
)
```

- `HORIZONTAL`/`VERTICAL` — одиночный клик (превью видно сразу, коммит
  на release, инструмент сбрасывается);
- остальные — drag от начала к концу;
- **live-превью**: на каждом движении `buildDrawing(...)` строит фигуру и
  отдаёт её в `onPreviewChange` — движок рисует её поверх остальных
  рисунков, поэтому во время drag видна линия и актуальные вычисления
  линейки (`Δ/%/время`); на release — `onPreviewChange(null)` + коммит
  в историю;
- линейка (`RULER`) создаёт `TrendLine` с меткой из чистой
  `rulerLabel(startPrice, endPrice, startTimeMs, endTimeMs, formatter)`:
  `Δ<цена> (<знак+2 знака %>) | <длительность>` — процент со знаком
  `(end − start) / start × 100`, длительность через `formatDuration`
  (`"2 d 12 h 15 m"`, нулевые компоненты пропускаются, секунды — только
  если длительность меньше минуты);
- **проекции на шкалу цен**: во время рисования трендовой/линейки и для
  выделенной трендовой линии движок рисует пунктирные горизонтали на
  уровнях start/end-цены через область графика с продолжением на
  `priceScaleArea` (`drawDrawingProjections`, рисуется под тиками шкалы)
  и ценовые теги start (синий) / end (цвет линии) поверх шкалы
  (`drawProjectionPriceTags`);
- цена в метках форматируется `priceFormatter`.

Построение фигуры вынесено в чистую `buildDrawing(...)` — одно и то же
значение для превью и коммита.

## 24.4. Перемещение и resize существующих фигур

Когда инструмент не активен, движок сначала проверяет hit-test по
рисункам (порог 8px):

```kotlin
fun hitTestDrawings(
    drawings: List<Drawing>,
    position: Offset,
    candles: List<Candle>,
    priceRange: PriceRange,
    chartHeight: Float,
    scrollOffset: Float,
    candleWidth: Float,
    candleSpacing: Float,
    threshold: Float = 8f,
): DrawingHit?   // id + drawing + handle (START/END или null)

fun moveDrawing(
    drawing: Drawing,
    handle: DrawingHandle?,
    from: Offset, to: Offset,       // дельта от старта жеста — без накопления ошибки
    candles: List<Candle>,
    priceRange: PriceRange,
    chartHeight: Float,
    scrollOffset: Float,
    candleWidth: Float,
    candleSpacing: Float,
    formatter: SymbolFormatter,
): Drawing
```

Поведение:

- тело фигуры → **move**: горизонтальный уровень двигается по цене,
  вертикальная линия — по времени, тренд/прямоугольник — целиком
  (обе точки);
- ручка (`START`/`END` у тренда, углы прямоугольника) → **resize** одной
  точки;
- при движении линейки её метка пересчитывается (`rulerLabel`);
- во время drag — серия `DrawingHistory.update(id, ...)`, на отпускании —
  `commit()` (один шаг undo на весь жест);
- **выделение и удаление**: клик без движения по фигуре выделяет её
  (рисуются ручки — `drawDrawingSelection`), `Delete`/`Backspace`
  удаляет выделенный рисунок (с записью в undo), клик по пустому месту
  снимает выделение.

Hit-test, move/resize и `rulerLabel` — чистые функции в
`tools/DrawingGeometry.kt`, покрыты тестами.

## 24.5. DrawingRepository — персистент

```kotlin
class DrawingRepository(private val store: StateStore, ...) {
    suspend fun load(workspaceId: String, panelId: String): List<Drawing>
    suspend fun save(workspaceId: String, panelId: String, drawings: List<Drawing>)

    companion object {
        fun key(workspaceId: String, panelId: String) = "drawings_${workspaceId}_$panelId"
    }
}
```

- Хранение — JSON в `settings`-таблице `LocalStorage` (ключ
  `drawings_<workspaceId>_<panelId>`).
- Битый JSON не роняет приложение: `load` возвращает пустой список.
- Привязка к панели воркспейса: у каждой панели графика свои рисунки.

## 24.6. Undo/Redo

`DrawingHistory` — см. главу 27. В тулбаре — кнопки `↶`/`↷`, в
`ChartInteraction` — горячие клавиши `Ctrl+Z`/`Ctrl+Y`.

---

# Часть VI. Утилиты и математика

# 25. ChartCalculator: все формулы в одном месте <a name="25"></a>

`utils/ChartCalculator.kt` — чистые функции без Compose-зависимостей
(кроме `Rect` в моделях), поэтому они идеально тестируются.

## 25.1. Метрики свечей

```kotlin
fun calculateCandleMetrics(zoomLevel: Float): CandleMetrics {
    val width = BASE_CANDLE_WIDTH * zoomLevel          // BASE_CANDLE_WIDTH = 8f
    val spacing = width * 0.3f / 0.7f                  // пропорция 70/30
    return CandleMetrics(width, spacing)
}
```

Ширина свечи зависит **только от зума**, не от количества свечей.

## 25.2. PriceRange

```kotlin
fun calculatePriceRangeWithCurrentPrice(candles: List<Candle>, currentPrice: Float?): PriceRange {
    val priceList = buildList {
        addAll(candles.map { it.high })
        addAll(candles.map { it.low })
        currentPrice?.let { add(it) }
    }
    val maxPrice = priceList.maxOrNull() ?: 0f
    val minPrice = priceList.minOrNull() ?: 0f
    val padding = (maxPrice - minPrice) * 0.05f
    return PriceRange(
        max = maxPrice,
        min = minPrice,
        visibleMax = maxPrice + padding,
        visibleMin = minPrice - padding,
        range = (maxPrice + padding) - (minPrice - padding)
    )
}
```

Диапазон считается **только по видимым свечам** — при скролле/зуме
Y-масштаб подстраивается. Параметр `currentPrice` сохранён для тестов
и обратной совместимости, но движок передаёт `null`: текущая цена не
должна растягивать масштаб (см. главу 14.4). Нефинитные значения
(`NaN`/`Infinity`) отфильтровываются, чтобы не «взорвать» диапазон.

Для footprint диапазон считается **по уровням bid/ask**, а не по
каркасным свечам — поля `minPrice`/`maxPrice` серверных footprint-свечей
могут отсутствовать, и skeleton-based расчёт давал «плоскую линию»:

```kotlin
fun calculatePriceRangeFromLevels(candles: List<FootprintCandle>): PriceRange {
    val prices = candles.flatMap { c -> c.levels.map { it.priceFloat } }.filter { it.isFinite() }
    if (prices.isEmpty()) return PriceRange(0f, 0f, 0f, 0f, 0f)
    val maxPrice = prices.maxOrNull() ?: 0f
    val minPrice = prices.minOrNull() ?: 0f
    val padding = if (maxPrice - minPrice <= 0f) maxPrice * 0.01f else (maxPrice - minPrice) * 0.05f
    ...
}
```

Этот расчёт переопределён в `FootprintSeries.priceRange()`. Агрегация
(`FootprintAggregator`) и `toSkeletonCandle()` тоже считают min/max из
уровней, когда строковые поля пусты.

Сдвиг диапазона при вертикальном скролле footprint:

```kotlin
fun shiftPriceRange(base: PriceRange, verticalScroll: Float, chartHeight: Float): PriceRange {
    if (chartHeight <= 0f || verticalScroll == 0f) return base
    val ratio = verticalScroll / chartHeight
    val shift = base.range * ratio
    return PriceRange(
        max = base.max + shift,
        min = base.min + shift,
        visibleMax = base.visibleMax + shift,
        visibleMin = base.visibleMin + shift,
        range = base.range,
    )
}
```

Используется `PriceScale.range()`.

## 25.3. Преобразование цены в Y и обратно

```kotlin
fun priceToY(price: Float, priceRange: PriceRange, height: Float): Float {
    val range = if (priceRange.range <= 0f) 0.01f else priceRange.range
    return height - ((price - priceRange.visibleMin) / range) * height
}

fun priceFromY(y: Float, priceRange: PriceRange, chartHeight: Float): Float {
    val range = if (priceRange.range <= 0f) 0.01f else priceRange.range
    return priceRange.visibleMax - (y / chartHeight) * range
}
```

**Для Junior.** В экранных координатах Y растёт вниз, а цена — вверх.
Поэтому в формуле стоит `height - ...`. Функции взаимно обратны, что
проверяется тестом `priceFromY is inverse of priceToY`.

## 25.4. Уровни шкалы цен

```kotlin
fun generatePriceLevels(min: Float, max: Float, count: Int): List<Float> {
    val step = (max - min) / (count - 1)
    return List(count) { i -> max - step * i }
}
```

## 25.5. Ближайшая свеча

```kotlin
fun findNearestCandleIndex(
    mouseX: Float,
    candles: List<Candle>,
    scrollOffset: Float = 0f,
    zoomLevel: Float = 1f,
): Int {
    val metrics = calculateCandleMetrics(zoomLevel)
    val totalW = metrics.width + metrics.spacing
    val virtualX = mouseX + scrollOffset
    return (virtualX / totalW).toInt().coerceIn(0, candles.size - 1)
}
```

Используется crosshair'ом и рисованием. Параметр `chartWidth` удалён —
он не участвовал в вычислении.

## 25.6. Зум и максимальный скролл

Чистое ядро математики осталось в `ChartCalculator`, а состояние и
удобный API — в `scale/TimeScale`:

```kotlin
class TimeScale(initialZoom: Float = 1f) {
    var scrollOffset by mutableFloatStateOf(0f)   // Compose snapshot state
    var zoomLevel by mutableFloatStateOf(initialZoom)

    fun maxScroll(candleCount: Int, chartWidth: Float): Float   // → calculateMaxScroll
    fun indexToX(index: Int): Float
    fun xToIndex(x: Float): Int
    fun panBy(deltaX: Float, candleCount: Int, chartWidth: Float)
    fun zoomAt(factor, mouseX, anchorAtMouse, chartWidth, candleCount, minZoom, maxZoom)
    fun scrollToLatest(candleCount: Int, chartWidth: Float)
    fun offsetAfterPrepend(addedCount: Int, candleCount: Int, chartWidth: Float)
    fun isAtLatest(candleCount: Int, chartWidth: Float): Boolean
}
```

`zoomAt` внутри вызывает `calculateZoomScrollOffset`/`calculateMaxScroll`.
Покрыто тестами (`TimeScaleTest`): кламп панорамы, якоря зума
(правый край/Ctrl), границы min/max, `indexToX ↔ xToIndex`, коррекция
после prepend, `scrollToLatest`.

Важно: в обработчике зума `maxScroll` для клампа считается от **нового**
зума (`calculateCandleMetrics(newZoom)`), иначе кламп по устаревшему
значению сдвигал якорь.

---

# 26. Timeframes, Format, ChartConstants, SymbolFormatter <a name="26"></a>

## 26.1. Timeframes (platform-core)

```kotlin
object Timeframes {
    val supported = setOf("1m", "5m", "15m", "30m", "1h", "4h", "1d", "1w")
    fun toExchangeInterval(timeframe: String): String
    fun millis(timeframe: String): Long
}
```

Единая точка маппинга «строка UI → интервал биржи/миллисекунды».
До рефакторинга этот маппинг был продублирован в 4+ местах и терял
`30m`/`1w`.

## 26.2. Format

```kotlin
fun formatPrice(price: Float, formatter: SymbolFormatter = SymbolFormatter.DEFAULT): String =
    formatter.formatPrice(price)

fun formatTime(timestamp: Long): String {
    val local = Instant.fromEpochMilliseconds(timestamp)
        .toLocalDateTime(TimeZone.currentSystemDefault())
    return "${local.hour.toString().padStart(2, '0')}:${local.minute.toString().padStart(2, '0')}"
}
```

В рендерерах всегда передаётся `config.priceFormatter`. **Технический
долг:** `formatTime` не учитывает таймфрейм (для `1d` логичнее `dd.MM`).

## 26.3. ChartConstants

```kotlin
/** Базовая ширина свечи в пикселях (при zoomLevel=1) */
const val BASE_CANDLE_WIDTH = 8f
```

## 26.4. SymbolFormatter (platform-core)

```kotlin
class SymbolFormatter(val tickSize: Double = 0.01, val minQty: Double = 0.001) {
    val priceDecimals: Int = if (tickSize <= 0.0) 2 else maxOf(0, -log10(tickSize).toInt())
    val volumeDecimals: Int = ...

    fun formatPrice(price: Double): String
    fun formatPrice(price: Float): String
    fun formatVolume(volume: Double): String   // 1.2K / 3.4M
}
```

`tickSize = 0.01` → 2 знака (BTC), `tickSize = 0.00000001` → 8 знаков (мелкие
монеты). Форматтер строится из `SymbolInfo` в `ChartViewModel.loadSymbols()`
и лежит в `ChartUiState.currentSymbolFormatter`.

---

# 27. DrawingHistory: Undo/Redo как Compose-состояние <a name="27"></a>

## 27.1. Реализация: снимки списка

```kotlin
class DrawingHistory(private val maxHistory: Int = 100) {
    private val undoStack = ArrayDeque<List<Drawing>>(maxHistory)  // снимки ДО операции
    private val redoStack = ArrayDeque<List<Drawing>>(maxHistory)
    private val _drawings = mutableStateListOf<Drawing>()
    val drawings: List<Drawing> get() = _drawings

    fun add(drawing: Drawing)          // снимок до + добавить
    fun update(id: String, drawing: Drawing)  // замена БЕЗ записи в undo
    fun commit()                       // один undo-шаг на серию update (drag)
    fun remove(drawing: Drawing)
    fun undo()
    fun redo()
    fun replaceAll(drawings: List<Drawing>)   // загрузка из персистента
    fun clear()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()
    val size: Int get() = _drawings.size
}
```

Почему снимки, а не «обратная операция»: перемещение и resize — это
замена фигуры, а не добавление/удаление. Снимок списка делает undo
тривиально корректным для любой операции.

## 27.2. Почему mutableStateListOf

Раньше `_drawings` был обычным `mutableListOf`, и добавление рисунка
не вызывало рекомпозицию — фигура появлялась только после следующего
случайного изменения состояния. `mutableStateListOf` — snapshot-state
Compose: любое изменение автоматически инвалидирует Canvas и
`snapshotFlow`, который сохраняет рисунки на диск.

## 27.3. Семантика undo/redo

- `add`/`remove` кладут в undo-стек снимок списка **до** операции и
  очищают redo;
- `update` меняет фигуру на месте и запоминает baseline-снимок до
  первого изменения; `commit()` переносит его в undo-стек — вся серия
  движений мыши = один шаг undo;
- `undo` кладёт текущий список в redo и применяет последний снимок;
- `redo` — обратная операция;
- `maxHistory = 100` — старые снимки вытесняются;
- `replaceAll` (загрузка из персистента) очищает историю — загруженные
  фигуры нельзя «отменить до пустоты».

Покрыто тестами `DrawingHistoryTest` (add/undo/redo/update+commit/remove/
maxHistory/replaceAll/clear).

---

# 28. Система координат и порядок слоёв Canvas <a name="28"></a>

## 28.1. Три системы координат

| Система | Где используется | Как считается |
|---|---|---|
| Виртуальная лента | индексы свечей, скролл | `TimeScale.indexToX / xToIndex` |
| Экранная (Canvas) | рисование | `virtualX − scrollOffset` |
| Цена ↔ Y | всё вертикальное | `priceToY` / `priceFromY` (через `PriceScale`) |

Ключевой инвариант: **все вертикальные вычисления идут в одном
прямоугольнике** `chartMainArea` и с одним `PriceRange`.

## 28.2. Единое Y-пространство

```
chartMainArea:  top = 0
                bottom = canvasHeight − timeScaleHeight − indicatorTotalH

priceScaleArea: top = chartMainArea.top
                bottom = chartMainArea.bottom
```

Почему это важно:

- свечи рисуются с `chartMainArea.height`;
- crosshair и его метки — с `chartMainArea.height`;
- шкала цен — с `priceScaleArea.height`, который равен высоте main area;
- гистограмма ликвидаций — в своём `indicatorAreas[i]`.

До рефакторинга свечи рисовались по высоте всего Canvas, а crosshair —
по `chartMainArea`, и метки перекрестия не совпадали со свечами.

## 28.3. Порядок слоёв

| # | Слой | Кто рисует |
|---|---|---|
| 1 | Сетка + серия (свечи/footprint) | `ChartSeries.draw` |
| 2 | Линия текущей цены (footprint) | `drawCurrentPriceLine` |
| 3 | Шкала времени | `drawTimeScale` |
| 4 | Маркеры ликвидаций | `drawLiquidationMarkers` |
| 5 | Индикаторные панели | `indicatorRenderers` |
| 6 | Шкала цен + badge | `drawPriceScale` |
| 7 | Popup footprint (Alt) | `drawFootprintPopup` |
| 8 | Рисунки пользователя | `drawDrawings` |
| 9 | Crosshair (свечный или footprint) | `drawCrosshair` / `drawCrosshairForFootprint` |

В фазе F эти слои планируется перевести на `ChartPanePrimitive` с
zOrder (`bottom/normal/top`), как в lightweight-charts.

## 28.4. Z-index и клиппинг

- Каждый рендерер сам вызывает `withTransform { clipRect(...) }` — ничего
  не вылезает за свою область.
- `Canvas` дополнительно обёрнут в `Modifier.clipToBounds()`.
- Popup и метки рисуются последними, чтобы их не перекрывали свечи.

---

# Часть VII. Интеграция, качество, рецепты

# 29. Интеграция в composeApp: workspace и legacy-режим <a name="29"></a>

## 29.1. Workspace (основной режим)

`main.kt` строит панели через `LayoutRenderer`. Для панели графика:

```kotlin
val vmKey = "${pc.id}_chart"
val vm: ChartViewModel = ws.liveViewModels.getOrPut(vmKey) { koinInject<ChartViewModel>() }

// 1. Восстановить режим из конфига панели
LaunchedEffect(pc.id) {
    val target = try { ChartMode.valueOf(state?.chartMode ?: "CANDLESTICK") }
                 catch (e: Exception) { ChartMode.CANDLESTICK }
    if (vm.state.value.chartMode != target) vm.dispatch(ChartIntent.ToggleChartMode)
}

// 2. Перезагрузить, только если символ/ТФ разошлись с конфигом
LaunchedEffect(pc.symbol, tf) {
    val needReload = vm.state.value.currentSymbol != pc.symbol ||
                     vm.state.value.currentTimeframe != tf
    if (needReload) vm.dispatch(ChartIntent.LoadChart(pc.symbol, tf))
}

// 3. Синхронизация VM → PanelConfig (skipInitial, чтобы не зациклиться)
LaunchedEffect(Unit) {
    var skipInitial = true
    vm.state.map { it.currentSymbol }.distinctUntilChanged().collect { s ->
        if (skipInitial) { skipInitial = false; return@collect }
        panelConfigs = panelConfigs + (currentPc.id to currentPc.copy(symbol = s))
        persistConfig()
    }
}
// ... аналогично timeframe, chartMode

// 4. Сам график + привязка рисунков к панели
ChartWindow(
    vm,
    initialZoomLevel = state?.zoomLevel ?: 1f,
    onZoomChange = { zl -> /* сохранить zoomLevel в PanelConfig */ },
    workspaceId = ws.config.id,
    panelId = pc.id
)
```

Ключевые моменты:

- VM кэшируется в `liveViewModels` — панель переживает рекомпозиции.
- Состояние панели (`symbol`, `timeframe`, `chartMode`, `zoomLevel`)
  хранится в `PanelConfig` и переживает перезапуск приложения
  (`WorkspaceRepository` → `StateStore`).
- При закрытии панели VM удаляется из `liveViewModels` и получает
  `dispose()`.

## 29.2. Legacy MainScreen

`MainScreen` (когда workspace выключен) работает с одним `ChartViewModel`:

```kotlin
LaunchedEffect(Unit) { chartViewModel.dispatch(ChartIntent.RestoreState) }
LaunchedEffect(selectedSymbol, selectedTimeframe) {
    chartViewModel.dispatch(ChartIntent.LoadChart(selectedSymbol, selectedTimeframe))
}
```

Символ/ТФ синхронизируются из `state.map { ... }.distinctUntilChanged()`.
Рисунки здесь не персистятся: `workspaceId`/`panelId` не передаются.

## 29.3. Полная картина жизненного цикла

```
App start
  └─ initKoin() (AppModule: LocalStorage, репозитории, адаптеры)
       └─ TabManager.restoreSession() → открыть воркспейсы
            └─ LayoutRenderer → панель CHART
                 ├─ koinInject<ChartViewModel>() → liveViewModels
                 ├─ dispatch(LoadChart) / RestoreState
                 ├─ cache preload → TimeSeriesController → WS live
                 └─ ChartWindow(workspaceId, panelId) → рисунки с диска
```

---

# 30. Интеграция в bidasker-web <a name="30"></a>

`bidasker-web` — отдельное JS-приложение (tariff-версия footprint-графика).
Оно использует:

- `FootprintApiClient` — загрузка инструментов и свечей (`DataLoader`);
- `FootprintChart` — тонкая обёртка над движком (глава 17);
- `ChartConfig`/`FootprintConfig`/`DefaultChartConfig` — конфигурацию;
- `AggregationLevel` (через `features:dom`).

Особенности:

- нет Koin и `ChartViewModel`: данные грузятся напрямую в composable-стейт;
- таймфреймы и символы приходят из конфига тарифа;
- сборка — только JS (`:bidasker-web:compileKotlinJs`), поэтому любое
  использование JVM-only API в `commonMain` фичи сломает его сборку —
  держите `commonMain` чистым.

---

# 31. Тестирование модуля <a name="31"></a>

## 31.1. Где лежат тесты

| Модуль | Файл | Что покрывает |
|---|---|---|
| `features:chart` | `ChartCalculatorTest` | метрики, `priceToY/priceFromY`, уровни, nearest index, PriceRange (включая низкие цены, нефинитные, footprint по уровням), якоря зума, `shiftPriceRange` |
| `features:chart` | `TimeScaleTest` | кламп панорамы, якоря зума (правый край/Ctrl), min/max, `indexToX ↔ xToIndex`, prepend-коррекция, `scrollToLatest` |
| `features:chart` | `PriceScaleTest` | fit, пропорциональный сдвиг, нулевая высота, идемпотентность |
| `features:chart` | `FootprintMappingTest` | `toSkeletonCandle` (open/high/low/close/startTime/maxVolume, fallback на уровни) |
| `features:chart` | `FootprintAggregatorTest` | source-ТФ, выровненная агрегация, `bucketStart`, суммы объёмов, чанки |
| `features:chart` | `FootprintRendererTest` | `aggregateLevels` (BaseTick/TenTick/HundredTick, tickSize ≤ 0) |
| `features:chart` | `ChartStatePersistorTest` | save/restore, легаси-ключи, битые значения, zoom roundtrip |
| `features:chart` | `ChartUiStateTest` | дефолты и `copy` |
| `features:chart` | `ChartViewModelCacheTest` | с фейками: запись при загрузке, показ из кэша, flush при смене символа |
| `features:chart` | `DrawingHistoryTest` | add/undo/redo/update+commit/remove/maxHistory (снимки списка) |
| `features:chart` | `DrawingGeometryTest` | hit-test (тело/ручки), move/resize (уровень, тренд, прямоугольник), `rulerLabel` (знак/округление), `formatDuration` |
| `features:chart` | `DrawingRepositoryTest` | JSON-roundtrip, изоляция workspace/panel, битый JSON |
| `platform-core` | `TimeSeriesControllerTest` | initial load, live-merge, loadMore, пустой ответ, ошибка, `loadGeneration` |
| `features:localstorage` | `LocalStorageTest` | roundtrip свечей/footprint, изоляция по exchange, лимит, очистка, **миграция схемы** |

Запуск:

```bash
./gradlew :platform-core:jvmTest
./gradlew :features:chart:jvmTest
./gradlew :features:localstorage:jvmTest
```

## 31.2. Паттерны

**Чистые функции** — обычные `kotlin.test`-ассерты, без корутин.

**Корутины** — `runTest` + `advanceUntilIdle`:

```kotlin
@Test
fun `live updates are merged into items`() = runTest {
    val live = MutableSharedFlow<Int>(extraBufferCapacity = 16)
    val source = FakeSource(initial = listOf(1, 2), liveFlow = live)
    val controller = TimeSeriesController(source, this)

    controller.start()
    advanceUntilIdle()
    live.emit(3)
    advanceUntilIdle()

    assertEquals(listOf(1, 2, 3), controller.state.value.items)
    controller.stop()   // иначе runTest будет ждать вечную подписку
}
```

**Важно:** в текущем окружении `backgroundScope` не прогоняется
`advanceUntilIdle()` — используйте `this` (TestScope) и явно останавливайте
бесконечные подписки (`controller.stop()`).

**SQLite** — временный файл:

```kotlin
val dir = Files.createTempDirectory("nous-storage-test")
val storage = LocalStorage(dir.resolve("storage.db").absolutePathString())
```

Миграция проверяется так: сначала вручную создаётся БД старой схемы через
`DriverManager`, затем открывается `LocalStorage` и проверяется, что данные
доступны под exchange `"Binance"`.

## 31.3. Что не покрыто

- UI и жесты (`ChartInteraction`, `DrawingOverlay`) — нужен
  `compose-ui-test`.
- Реальные сетевые адаптеры (есть `ktor-client-mock` в зависимостях —
  можно писать тесты провайдеров).
- `FootprintController` целиком (зависит от времени и сети; логичные
  кандидаты — вынести расчёты в чистые функции и покрыть их).

---

# 32. Технический долг и известные проблемы <a name="32"></a>

Сводка после рефакторинга фаз 0–E. Часть прежних проблем закрыта и
из списка убрана (мёртвый `chart2/`, stale `visibleCandles`, `30m/1w`,
потеря realtime после истории, `delay(100)` в `loadChart`, кривые границы
footprint, отсутствие меток у рисунков и т.д.).

## 32.1. Баги и поведение

| # | Проблема | Где |
|---|---|---|
| 1 | `LaunchedEffect(historyLoadCount, candles.size)` может повторно сдвигать скролл при добавлении realtime-свечи | `ChartInteraction.kt` |
| 2 | У `ChartAdapter.subscribeToCandles` нет авто-реконнекта (у ликвидаций есть) | `BinanceChartAdapter` |
| 3 | `formatTime` игнорирует таймфрейм (для `1d`/`1w` нужны даты) и системную таймзону как настройку | `Format.kt` |
| 4 | Рисунки привязаны к панели, а не к символу/ТФ: при смене инструмента остаются на экране | `DrawingRepository` |

## 32.2. Архитектура и связность

| # | Проблема | Где |
|---|---|---|
| 5 | `features:chart` зависит от `features:dom` (`AggregationLevel`) | `build.gradle.kts`, `ChartConfig.kt` |
| 6 | `features:chart` зависит от `providers:binance-provider` (preview DI) | `build.gradle.kts` |
| 7 | `FootprintApiClient` — конкретный класс с хардкодом IP; нет `FootprintAdapter` в `api-market` (MEXC-бэкенду некуда подключиться) | `FootprintApiClient.kt` |
| 8 | Exchange в кэше — константа `"Binance"` в VM/контроллере | `ChartViewModel.kt`, `FootprintController.kt` |
| 9 | `Koin GlobalContext` внутри UI для получения `StateStore` | `ChartWindow.kt` |
| 10 | Список таймфреймов в тулбаре дублирует `Timeframes.supported` | `ChartToolbar.kt` |
| 11 | `LiquidationViewModel` — не ViewModel (нет lifecycle-aware scope) | `indicator/` |
| 12 | `FootprintController` всё ещё крупный: live + polling + история + кэш в одном классе | `footprint/` |
| 13 | Оверлеи захардкожены в порядке отрисовки движка; нет `ChartPanePrimitive` с zOrder/hitTest и multi-pane (план фазы F) | `ChartInteraction.kt` |
| 14 | Границы зума в `ChartConfig`, но конфиг ещё не сериализуется и нет окна настроек | `ChartConfig.kt` |

## 32.3. Производительность

| # | Проблема | Где |
|---|---|---|
| 15 | `aggregateLevels` вызывается каждый кадр | `FootprintRenderer.kt` |
| 16 | Текст меряется заново каждый кадр (crosshair, popup, метки рисунков) | рендереры |
| 17 | Записи состояния прямо в композиции (`chartWidthPx`, `chartHeightPx`) | `ChartInteraction.kt` |
| 18 | `visibleCandles` создаёт `subList` при каждом изменении индексов | `ChartInteraction.kt` |
| 19 | Кэш перезаписывает до 500 свечей целиком раз в 30 c (можно инкрементально) | `ChartViewModel.kt` |

## 32.4. Обработка ошибок

- `println(...)` вместо логгера — в `ChartViewModel`, `FootprintController`,
  `LiquidationViewModel`.
- Сетевые ошибки footprint истории превращаются в пустой график
  (`catch { emptyList() }`) — нет различия «нет данных» и «ошибка сети».
- Сообщения об ошибках (сырой `e.message`) показываются пользователю как есть.

---

# 33. Рецепты: добавить индикатор, таймфрейм, инструмент, источник <a name="33"></a>

## 33.1. Как добавить индикаторную панель

Например, панель объёма под графиком.

1. Написать `DrawScope`-функцию:

```kotlin
// rendering/VolumeRenderer.kt
fun DrawScope.drawVolumePanel(
    area: Rect,
    candles: List<Candle>,
    scrollOffset: Float,
    zoomLevel: Float,
    config: ChartConfig
) {
    val metrics = calculateCandleMetrics(zoomLevel)
    val totalW = metrics.width + metrics.spacing
    val maxVol = candles.maxOfOrNull { it.volume } ?: return
    val start = (scrollOffset / totalW).toInt().coerceAtLeast(0)
    val end = ((scrollOffset + area.width) / totalW + 1).toInt().coerceAtMost(candles.size)
    // нарисовать бары
}
```

2. Передать рендерер в `CandleStickChart`:

```kotlin
val indicatorRenderers = remember(candles) {
    listOf<DrawScope.(Rect, List<Candle>, PriceRange, Float, Float) -> Unit>(
        { area, cs, _, scroll, zoom -> drawVolumePanel(area, cs, scroll, zoom, chartConfig) }
    )
}
```

3. `ChartInteraction` сам выделит панель высотой `indicatorHeightDp`
   и нарисует разделитель.

## 33.2. Как добавить таймфрейм

1. `Timeframes.supported` — добавить строку.
2. `Timeframes.millis` — добавить длительность.
3. `ChartToolbar.timeframes` — добавить кнопку.
4. Если это footprint-таймфрейм — обновить
   `FootprintAggregator.resolveFootprintSourceTimeframe`.

Больше нигде править не нужно: репозиторий и контроллер берут интервал
из `Timeframes`.

## 33.3. Как добавить инструмент рисования

1. `Drawing` — новый `@Serializable data class` (не забыть
   `@Serializable(with = ColorSerializer::class)` для цветов).
2. `DrawingToolType` — новое значение.
3. `DrawingOverlay.addDrawing` — обработка жеста и создание модели.
4. `DrawingRenderer.drawDrawings` — отрисовка (и метки, если нужны).
5. `ChartToolbar.drawingTools` — кнопка.

Персистент заработает автоматически: список сериализуется целиком.

## 33.4. Как добавить новый источник данных (например, MEXC)

1. Реализовать адаптер в провайдере (`ChartAdapter`/`LiquidationAdapter`).
2. Написать `TimeSeriesSource<T>`:
   - `loadInitial` — последние N;
   - `loadBefore` — пагинация (или `emptyList()`, если не поддержана);
   - `liveUpdates` — WebSocket/пустой поток;
   - `mergeItem` — правило слияния;
   - `timestampOf` — временная метка.
3. Обернуть в `TimeSeriesController` там, где нужен стейт:
   ```kotlin
   val controller = TimeSeriesController(MySeriesSource(adapter), scope)
   controller.start()
   ```
4. Если нужен кэш — реализовать `CandleCacheStore`/`FootprintCacheStore`
   или переиспользовать `LocalStorage` с другим `exchange`.

## 33.5. Как отладить график

- `./gradlew :features:chart:run` — изолированное окно с preview-Koin.
- Логи footprint/live — `println` в `FootprintController` (временно).
- Проверить кэш: `Settings → Storage` покажет, что и когда записано.
- Если график «плоский» — почти наверняка проблема в `PriceRange`
  или в ключах `remember` (см. главу 14.4).
- Если рисунки не сохраняются — проверьте, что панель получила
  `workspaceId`/`panelId` и что `StateStore` зарегистрирован в Koin.

---

# 34. Глоссарий <a name="34"></a>

| Термин | Значение |
|---|---|
| **Candle** | свеча OHLCV за таймфрейм |
| **Footprint** | кластерный график: bid/ask объёмы по ценовым уровням внутри свечи |
| **Bid / Ask** | покупка по биду (вниз) / продажа по аску (вверх) |
| **DOM** | стакан заявок (Depth of Market) |
| **TimeSeriesController** | обобщённый контроллер: initial load + live + пагинация |
| **TimeSeriesSource** | специфика источника данных для контроллера |
| **ChartUiState** | единое состояние графика |
| **ChartIntent** | пользовательское намерение (MVI) |
| **dispatch** | единственная публичная точка входа в ChartViewModel |
| **FootprintAggregator** | чистая логика: source-ТФ и выравненная агрегация |
| **ChartStatePersistor** | сохранение символа/ТФ/режима/агрегации в StateStore |
| **DrawingRepository** | персистент рисунков (`drawings_<workspaceId>_<panelId>`) |
| **PriceRange** | диапазон цен (реальный + 5% padding) |
| **CandleMetrics** | ширина и отступ свечи при текущем зуме |
| **chartMainArea** | единое Y-пространство свечей, crosshair и шкалы цен |
| **StateStore** | key-value хранилище (SQLite `settings`) |
| **LocalStorage** | SQLite-реализация `StateStore` + кэш свечей/footprint |
| **CandleCacheStore** | интерфейс кэша свечей (exchange + symbol + timeframe) |
| **FootprintCacheStore** | интерфейс кэша footprint (exchange + symbol) |
| **snapshotFlow** | мост Compose-состояния в корутинный поток (сохранение рисунков) |
| **Ruler (линейка)** | инструмент измерения `Δ цены`, `%` и времени между двумя точками |

---

*Документ актуализирован после рефакторинга фаз 0–E
(ветка `char-big-refactoring`).*

