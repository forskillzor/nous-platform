# Nous Platform v1.0 — Полная техническая спецификация (актуализированная)

## Оглавление

1. [Введение и видение продукта](#1)  
2. [Бизнес-требования и целевая аудитория](#2)  
3. [Этапы развития (Roadmap) — ОБНОВЛЕНО](#3)  
4. [Архитектура платформы](#4)  
5. [Функциональные требования](#5)  
6. [Нефункциональные требования](#6)  
7. [Технический стек](#7)  
8. [Модульная структура проекта](#8)  
9. [Плагинная система и экосистема — ОТЛОЖЕНО](#9)  
10. [Требования к безопасности](#10)  
11. [Требования к интерфейсу](#11)  
12. [Требования к данным и хранилищам](#12)  
13. [Интеграция с внешними системами](#13)  
14. [Монетизация и бизнес-модель](#14)  
15. [План разработки (соло, 15 дней/мес) — ОБНОВЛЕНО](#15)  
16. [Риски и пути их минимизации — РАСШИРЕНО](#16)  
17. [Заключение](#17)  

---

## 1. Введение и видение продукта <a name="1"></a>

### 1.1. Миссия
Создать современную, модульную и открытую платформу для профессионального криптотрейдинга, которая объединит лучшие практики ATAS, CScalp и MetaTrader, но с фокусом на крипторынки, предоставляя беспрецедентные возможности для кастомизации и алгоритмической торговли.

### 1.2. Видение
Nous Platform — это не просто терминал, а экосистема, состоящая из:
- **Мощного десктопного приложения** для анализа Order Flow, Volume Profile и кластерных графиков
- **Открытого API** для создания плагинов сообществом (после инвестиций)
- **Предметно-ориентированного языка (DSL)** для написания индикаторов и стратегий (после инвестиций)
- **Маркетплейса** для распространения платных и бесплатных плагинов (после инвестиций)

### 1.3. Ключевые отличия от конкурентов
1. **Live Trading с первого дня** — пользователи могут торговать сразу, а не только анализировать
2. **Фокус на крипту** — оптимизация под Binance/Bybit, индикатор ликвидаций, дельта-профиль
3. **Современный технологический стек** — Kotlin Multiplatform + Compose Desktop
4. **Кроссплатформенность** — один код для Windows, macOS и Linux
5. **Соло-френдли** — проект построен так, что один разработчик может дойти до первых 1000 пользователей

---

## 2. Бизнес-требования и целевая аудитория <a name="2"></a>

### 2.1. Целевая аудитория

#### Сегмент A: Профессиональные криптотрейдеры
- **Характеристики:** Торгуют на споте и фьючерсах, используют Volume Profile, Cluster Charts, DOM, Delta
- **Потребности:** Высокая производительность, стабильность, прямой доступ к рынку, кастомизация интерфейса
- **Боли:** ATAS не оптимизирован для крипты, TradingView недостаточно глубок для Order Flow анализа

#### Сегмент B: Трейдеры, которым нужен Live Trading
- **Характеристики:** Хотят торговать прямо из терминала, а не переключаться между биржей и графиками
- **Потребности:** Быстрое выставление ордеров, управление позициями, риск-менеджмент
- **Боли:** CScalp ограничен, ATAS сложен в настройке

### 2.2. Бизнес-цели (обновлено)
1. **Краткосрочные (2026):** Запуск Live Trading, привлечение первых 1000 активных пользователей, 50+ платных
2. **Среднесрочные (2027):** Привлечение Pre-Seed/Seed инвестиций, расширение команды до 3-4 человек
3. **Долгосрочные (2028+):** Создание экосистемы плагинов, выход на ARR $2M+

### 2.3. Ключевые показатели успеха (KPI)
- Количество активных пользователей в день (DAU) — целевые 500 к концу 2026
- **Live Trading ордеров в день** — целевые 1000 к концу 2026
- Среднее время от получения данных с биржи до отображения в UI — менее 300 мс
- Конверсия в платную подписку — 5-10%

---

## 3. Этапы развития (Roadmap) — ОБНОВЛЕНО <a name="3"></a>

**Ключевое изменение:** Live Trading на Этапе 1. Плагины, DSL, маркетплейс — только после инвестиций.

### 3.1. Этап 0: Фундамент (Q2 2026) — ЗАВЕРШЁН
- ✅ Настройка модульной архитектуры согласно утверждённой структуре
- ✅ Создание `public-api` модулей с базовыми интерфейсами и моделями
- ✅ Реализация `platform-core` с бизнес-логикой
- ✅ Разработка feature-модулей (`feature-dom`, `feature-chart`, `feature-trades`, `feature-localstorage`, `feature-settings`)
- ✅ Миграция существующего кода из `composeApp` в новые модули
- ✅ Добавление WASM/JS таргетов в convention-плагины (Kotlin Multiplatform)
- ✅ Переход Koin/ktor-client-cio из `commonMain` в `jvmMain` для WASM-совместимости
- ✅ `expect`/`actual` паттерны: HttpClientFactory, Platform.currentTimeMillis, ConcurrentMapFactory, Fonts
- ✅ Обновление зависимостей: kotlinx-coroutines 1.9.0, kotlinx-serialization 1.7.3, kotlinx-datetime 0.6.1

### 3.2. Этап 1: MVP с Bidasker (Q2-Q3 2026) — В ПРОЦЕССЕ

**Bidasker SaaS (freemium footprint chart):**
- ✅ `bidasker-web` модуль — Compose Multiplatform → Kotlin/JS (IR)
- ✅ Vue 3 Landing Page с iframe-интеграцией WASM/JS чарта
- ✅ Тарифная система (Free/Registered/Pro) с JSON-конфигом
- ✅ Email-регистрация (localStorage, подготовка к API)
- ✅ GitHub Actions deploy на GitHub Pages
- ✅ 10x агрегация ценовых уровней для производительности
- ✅ Загрузка через `onWasmReady()` — устранение race condition с Skiko WASM

**Live Trading + Анализ:**
- Подключение к Binance и Bybit (WebSocket + REST) ✅ (Binance)
- Отображение стакана (DOM) с визуализацией объёмов ✅
- Отображение ленты сделок (Time & Sales) ✅
- Отображение свечного/футпринт графика ✅
- Перетаскиваемые окна ✅
- Базовые индикаторы (SMA, EMA, VWAP) ✅
- Инструменты рисования на графике (Trend Line, Horizontal, Rectangle, Ruler) ✅
- Undo/Redo (Ctrl+Z/Y) ✅
- **Paper trading** (симуляция торговли)
- Поддержка ликвидаций (forceOrder) — клиент и сервер ✅
- CORS на market-data-server ✅
- Сохранение настроек интерфейса

### 3.2b. Этап 1.5: Workspace & Tab System (Q3 2026) — ПЛАН

**Цель:** IDE-подобный workspace manager для трейдинга

**Концепция:** Пользователь работает с Workspace — аналогом файла проекта. Каждый workspace описывает: биржу, инструмент, набор панелей (chart, DOM, trades), их расположение, индикаторы, объекты рисования. Workspace'ы сгруппированы в Project Tree слева, открываются в Tab Bar вверху, могут быть detached в отдельное окно.

**Архитектура:**
| Компонент | Роль |
|-----------|------|
| **WorkspaceConfig** (JSON) | Сериализуемый документ: провайдеры, layout, panels, drawings, indicators |
| **LayoutNode** | Рекурсивное дерево H/V сплитов (Split/Leaf) — без ограничения вложенности |
| **ProjectTree** | Левая панель: группы + workspace'ы, drag-and-drop переупорядочивание |
| **TabManager** | Управление вкладками: open/close/activate/detach-to-window |
| **ProviderPool** | Глобальный пул WebSocket-соединений с reference counting |
| **WorkspaceViewModel** | Один workspace = одна ViewModel с провайдерами и панелями |

**Фазы реализации:**
1. **Foundation:** Модели данных (`@Serializable`), SQLite через SQLDelight, CRUD
2. **Core:** ProviderPool, WorkspaceVM, PanelVM, TabManager
3. **UI:** TerminalShell, ProjectTree, TabBar, LayoutRenderer (рекурсивные сплиты), SplitHandle
4. **Interaction:** Compose Drag & Drop, Floating Window, WelcomeScreen, контекстное меню
5. **Integration:** Замена `MainScreen` на `TerminalShell`, backward compatibility

**Гибкость:**
- **Scalping setup:** Chart (1m) + DOM (20 уровней) + Trades в одном workspace
- **12x DOM Grid:** 12 панелей DOM с разными инструментами в одном workspace
- **Multi-provider:** Binance BTC chart + Bybit BTC DOM в одном workspace

**Хранение:** JSON в SQLite + экспорт в `.workspace.json` для шаринга.

**Почему JSON, а не Kotlin DSL:** drawings, indicators, references к дневнику/скриптам естественно хранятся в JSON-документе. SQLite даёт транзакционность и атомарность. Миграции при обновлении — добавить поле с default.

### 3.3. Этап 2: Live Trading + Профессиональный анализ (Q4 2026 - Q1 2027)
**Цель:** Полноценный торговый терминал с реальными деньгами

**Функционал:**
- **Реальная торговля на Binance/Bybit** (размещение ордеров, отмена)
- **Авторизация по API-ключам** (локальное зашифрованное хранение)
- **Модуль портфеля** (балансы, открытые позиции, активные ордера, PnL)
- **Footprint / Delta** (кластерный график с дельтой по ценам)
- **Delta Profile** (профиль объёма с разделением на покупки/продажи)
- **Индикатор ликвидаций** (оценка ликвидаций по аномальным свечам)
- Улучшенная обработка ошибок и авто-переподключение WebSocket

### 3.4. Этап 3: Первые пользователи и монетизация (Q2 2027)
**Цель:** Привлечь 1000 MAU и 50+ платных подписчиков

**Функционал:**
- **Публичный релиз** (сайт, документация, onboarding)
- **Freemium модель**:
  - Бесплатно: базовый анализ, paper trading
  - Pro ($29.9/мес или $299/год): Live Trading, Footprint, Delta Profile, ликвидации
- Сбор обратной связи, быстрые итерации
- Контент-маркетинг (YouTube, Twitter, Telegram)

### 3.5. Этап 4: Привлечение инвестиций (Pre-Seed/Seed) — Q3-Q4 2027
**Цель:** Получить $100k–$500k для расширения команды

**Что дают инвестиции:**
- Найм 2-3 разработчиков (ускорение в 3-4 раза)
- Профессиональный маркетинг
- Юридическое оформление (международное)

### 3.6. Этап 5: Экосистема (2028+) — ПОСЛЕ ИНВЕСТИЦИЙ
**Цель:** Стать платформой для разработчиков

**Функционал (отложен до найма команды):**
- Плагинная система (загрузка JAR, песочница)
- SDK и документация для разработчиков
- DSL для индикаторов и стратегий (как Pine Script)
- Редактор кода с подсветкой синтаксиса
- Бэктестинг на исторических данных
- Маркетплейс плагинов
- AI-интеграции

---

## 4. Архитектура платформы <a name="4"></a>

### 4.1. Общая архитектура

```mermaid
graph TB
    subgraph "Public API (Open Source)"
        API_MARKET["api-market"]
        API_TRADING["api-trading"]
        API_INDICATORS["api-indicators"]
        API_UI["api-ui"]
    end
    
    subgraph "Platform Core (Closed Source)"
        CORE["platform-core"]
        DEPS["core-dependencies"]
    end
    
    subgraph "Features (Closed Source)"
        DOM["feature-dom"]
        CHART["feature-chart"]
        TRADES["feature-trades"]
        TERMINAL["feature-terminal"]
        PORTFOLIO["feature-portfolio"]
    end
    
    subgraph "Providers (Open Source)"
        BINANCE["provider-binance"]
        BYBIT["provider-bybit"]
    end
    
    subgraph "Application (Closed Source)"
        APP["app"]
    end
    
    API_MARKET --> CORE
    API_MARKET --> FEATURES
    API_MARKET --> PROVIDERS
    
    CORE --> DEPS
    FEATURES --> CORE
    FEATURES --> API_MARKET
    
    PROVIDERS --> API_MARKET
    PROVIDERS --> DEPS
    
    APP --> FEATURES
    APP --> PROVIDERS
    APP --> CORE
```

### 4.2. Слои внутри модулей

Каждый feature-модуль имеет чёткое разделение на слои:

```
feature-*/src/commonMain/kotlin/com/aandios/nous/feature/xxx/
├── domain/                      # Бизнес-логика и интерфейсы
│   ├── models/                  # Модели данных (если специфичны для фичи)
│   ├── repository/              # Интерфейсы репозиториев (расширяют public-api)
│   └── usecases/                # Use cases (если сложная логика)
│
├── data/                        # Реализации репозиториев
│   ├── repository/               # Конкретные реализации
│   ├── datasource/               # Источники данных (локальные, удалённые)
│   └── mappers/                  # Мапперы между моделями
│
├── presentation/                 # UI слой
│   ├── viewmodel/                # ViewModel'и
│   ├── state/                    # Состояния UI
│   ├── components/               # UI компоненты
│   └── navigation/                # Навигация внутри фичи
│
└── di/                           # DI модуль для фичи
    └── XxxModule.kt
```

### 4.3. Ключевые архитектурные решения

#### 4.3.1. Единый источник правды для моделей
Все модели данных живут **только в `public-api` модулях**. Это обеспечивает:
- Консистентность данных во всей платформе
- Возможность использования одних и тех же моделей в плагинах
- Отсутствие дублирования и маппинга

#### 4.3.2. Разделение интерфейсов и реализаций
- **Интерфейсы** — в `public-api` (видны сообществу)
- **Реализации** — в `features/*/data` и `providers/*` (закрыты или открыты по необходимости)

#### 4.3.3. Инверсия зависимостей
- Feature-модули зависят только от `public-api` и `platform-core`
- Providers реализуют интерфейсы из `public-api`
- App модуль собирает всё вместе через DI

#### 4.3.4. Мультиплатформенность и KMP-таргеты
- `commonMain` — бизнес-логика, модели, интерфейсы, рендеринг чартов
- `jvmMain` — десктоп: Compose Desktop, Ktor CIO, Koin DI
- `jsMain` — веб: Compose Multiplatform → Kotlin/JS (IR), Ktor fetch engine
- `wasmJsMain` — веб (эксп.): Compose Multiplatform → WASM, Ktor WASM engine
- iOS и Android — в будущем

**Текущий статус:**
- JVM (десктоп): ✅ Production
- Kotlin/JS (web): ✅ Production (Bidasker SaaS)
- Kotlin/WASM (web): ⚠️ Эксп. (Skiko WASM нестабилен)
- iOS/Android: не начато

---

## 5. Функциональные требования <a name="5"></a>

### 5.1. Модуль подключения к данным (Data Providers)

#### 5.1.1. Базовые требования
- Поддержка публичных WebSocket и REST API
- Автоматическое переподключение при обрывах связи
- Обработка rate limits бирж
- Кэширование данных для снижения нагрузки

#### 5.1.2. Поддерживаемые биржи (MVP)
- **Binance** (Spot & Futures)
- **Bybit** (Spot & Futures)

#### 5.1.3. API провайдеров (интерфейсы в `public-api`)

```kotlin
interface MarketDataProvider {
    fun getSymbols(): Flow<List<Symbol>>
    fun getCandles(symbol: String, interval: Interval, limit: Int): Flow<List<Candle>>
    fun getOrderBook(symbol: String, limit: Int): Flow<OrderBook>
    fun getTrades(symbol: String): Flow<List<Trade>>
    fun getBestPrices(symbol: String): Flow<BestPrices>
}

// Добавлено для Live Trading
interface TradingProvider {
    fun placeOrder(order: Order): Flow<OrderResult>
    fun cancelOrder(orderId: String): Flow<Boolean>
    fun getOpenOrders(symbol: String): Flow<List<Order>>
    fun getPositions(): Flow<List<Position>>
    fun getBalances(): Flow<List<Balance>>
}
```

### 5.2. Модуль отображения (UI Features)

#### 5.2.1. Главное окно (`feature-terminal`)
- Панель инструментов с иконками для открытия модульных окон
- Панель выбора биржи (Binance/Bybit) и типа рынка (Spot/Futures)
- Панель выбора торгового инструмента с поиском и избранным
- Панель выбора таймфрейма
- Строка состояния с информацией о подключении и задержках

#### 5.2.2. Модульное окно "График" (`feature-chart`)
- Отображение свечного графика с поддержкой таймфреймов: 1m, 5m, 15m, 30m, 1h, 4h, 1d, 1w
- Масштабирование (scroll wheel) и панорамирование (drag) ✅
- **Footprint Chart** (кластерный режим: bid/ask объём по ценовым уровням) ✅
- **Индикатор ликвидаций** (треугольные маркеры на свечах: красный вниз — лонг-ликвидация, зелёный вверх — шорт) ✅
- Базовые индикаторы: SMA, EMA, VWAP ✅
- Переключение между режимами: свечи, кластеры (footprint) ✅
- Кроссхаир с отображением O/H/L/C и объёма свечи ✅
- **Рисование на графике** (6 инструментов): ✅
  - **Trend Line** (линия тренда с ручками на концах)
  - **Horizontal Level** (горизонтальный уровень с ценой)
  - **Vertical Line** (вертикальная линия на таймстемпе)
  - **Rectangle** (прямоугольная зона)
  - **Ruler** (линейка: Δцена + Δвремя + проценты)
- **Undo/Redo** (Ctrl+Z / Ctrl+Y) ✅
- NaN-защита: `priceToY`/`priceFromY` не падают при нулевом ценовом диапазоне ✅
- **Pluggable renderers** (в разработке): интерфейс `ChartRenderer` для замены типа свечей (CandleStick/Footprint/Bar) без изменения кода графика

#### 5.2.3. Модульное окно "Стакан" (DOM) (`feature-dom`)
- Отображение бидов и асков с динамическим обновлением
- Визуализация объёмов (горизонтальные бары)
- Настройка глубины стакана (10-50 уровней)
- Выделение лучших цен (best bid/ask)
- Отображение спреда в процентах и абсолютном значении
- **Возможность быстрого выставления ордера** (клик по цене)

#### 5.2.4. Модульное окно "Order Flow / Time & Sales" (`feature-trades`)
- Таблица потока сделок в реальном времени
- Цветовое кодирование: покупки (зелёный), продажи (красный)
- Выделение крупных сделок (блоков)
- Фильтрация по минимальному объёму

#### 5.2.5. Модульное окно "Портфель" (`feature-portfolio`) — Этап 2
- Отображение балансов по активам
- Список открытых позиций с PnL
- Список активных ордеров с возможностью отмены
- История сделок
- Графики PnL и статистика

#### 5.2.6. Workspace & Tab System (Этап 1.5) — ПЛАН
- **Project Tree** — левая панель с иерархическим списком workspace'ов, сгруппированных по папкам
- **Tab Bar** — вкладки (одна вкладка = один workspace), переключение, закрытие, detach в отдельное окно
- **Layout Engine** — рекурсивная сетка H/V сплитов (`Split/Leaf`). Без ограничения вложенности: можно 12 DOM в гриде, или chart+DOM+trades в стандартном скальпинг-layout
- **Split Resizer** — перетаскиваемая ручка для изменения пропорций сплитов
- **Panel Drag & Drop** — перетаскивание панелей между сплитами и workspace'ами (Compose DragAndDrop API)
- **Floating Window** — detach панели или целого таба в отдельное Compose `Window`
- **Workspace Config** — JSON-документ, описывающий провайдеров, layout, панели, drawings, indicators
- **Шаблоны** — предопределённые конфигурации: Scalping, DOM Grid, Order Flow, Empty
- **Welcome Screen** — стартовый экран с шаблонами, недавними workspace'ами, подсказками
- **Provider Pool** — глобальный пул WebSocket-соединений с reference counting. Один провайдер (Binance+BTCUSDT) шарится между всеми workspace'ами
- **Персистенс** — сохранение открытых табов между сессиями, восстановление при запуске
- **Экспорт** — `.workspace.json` файлы для шаринга между инсталляциями

### 5.3. Модуль Live Trading (добавлен)

#### 5.3.1. Выставление ордеров
- Лимитные ордера (цена + количество)
- Рыночные ордера (количество)
- Стоп-лосс и тейк-профит (прикреплённые к позиции)

#### 5.3.2. Управление рисками
- Максимальный размер позиции
- Дневной лимит убытка
- Подтверждение перед отправкой ордера

#### 5.3.3. Безопасность
- API-ключи хранятся в системном хранилище (Keychain/Credential Manager)
- Ключи никогда не передаются на серверы Nous Platform
- Возможность удалить/отозвать ключи

---

## 6. Нефункциональные требования <a name="6"></a>

### 6.1. Производительность
- **Задержка от события на бирже до UI:** < 300 мс (в идеале < 100 мс)
- **FPS графика:** 60 FPS при нормальной нагрузке
- **Обновление стакана:** каждое обновление должно отображаться не более чем за 16 мс
- **Память:** не более 512 MB RAM при стандартной нагрузке
- **Запуск приложения:** не более 5 секунд

### 6.2. Надежность
- **Uptime:** 99.9% (исключая проблемы бирж и интернет-соединения)
- **WebSocket соединения:** автоматическое переподключение с экспоненциальной задержкой
- **Обработка ошибок:** все ошибки должны логироваться и не приводить к падению приложения
- **Graceful degradation:** при отказе одного компонента остальные должны продолжать работу

### 6.3. Масштабируемость
- **Горизонтальная:** возможность добавления новых провайдеров без изменения ядра
- **Вертикальная:** возможность добавления новых фич через плагины
- **Нагрузка:** поддержка до 100 одновременно открытых окон с данными

### 6.4. Безопасность
- **Плагины:** изоляция в песочнице, проверка цифровых подписей
- **API-ключи:** хранятся только локально в зашифрованном виде
- **Сеть:** все соединения только по HTTPS/WSS
- **Данные пользователя:** никакой телеметрии без согласия

### 6.5. Юзабилити
- **Интерфейс:** настраиваемый, с возможностью сохранения профилей
- **Горячие клавиши:** полная поддержка для всех действий
- **Интернационализация:** поддержка как минимум английского и русского языков
- **Документация:** встроенная контекстная помощь

### 6.6. Качество кода
- **Тестовое покрытие:** не менее 70% для core-модулей
- **Code style:** единый стандарт (ktlint)
- **Документация:** все публичные API должны быть документированы
- **CI/CD:** автоматическая сборка и тестирование при каждом коммите

---

## 7. Технический стек <a name="7"></a>

### 7.1. Клиентская часть

| Компонент | Технология | Версия | Обоснование |
|-----------|------------|--------|-------------|
| Язык | Kotlin Multiplatform | 2.3.0 | Единый код JVM + JS + WASM |
| UI | JetBrains Compose Multiplatform | 1.7.3 | Реактивный UI, Canvas рендеринг |
| Архитектура | Clean Architecture + MVI | — | Чёткое разделение ответственности |
| DI | Koin | 3.5.6 | Простота, интеграция с Compose |
| Асинхронность | Kotlin Coroutines + Flow | 1.9.0 | Встроенная поддержка |
| Сеть | Ktor Client (CIO/fetch/WASM) | 3.4.1 | Мультиплатформенный HTTP + WS |
| Сериализация | kotlinx.serialization | 1.7.3 | Кроссплатформенная типобезопасность |
| Дата/время | kotlinx-datetime | 0.6.1 | Кроссплатформенные даты |
| Локальное хранение | SQLDelight (JVM) + SQLite | — | Типобезопасные SQL-запросы |
| Web rendering | Skiko (Canvas → WebGL2) | 1.7.3 | Compose → Canvas в браузере |
| Web target | Kotlin/JS (IR) | 2.3.0 | Bidasker SaaS продакшен |
| Web target (exp) | Kotlin/WASM | 2.3.0 | Экспериментальный |
| Vue.js | Vue 3 + Vite + TypeScript | 3.5 | Bidasker landing page |

### 7.2. Серверная часть (бэкенд-инфраструктура)

| Компонент | Технология | Версия | Обоснование |
|-----------|------------|--------|-------------|
| Trade Collector | Kotlin/JVM (Gradle) | 2.2.20 | Сбор данных с бирж |
| HTTP сервер | Ktor Server (Jetty) | 3.2.0 | Мониторинг и REST API |
| База данных | PostgreSQL 16 | — | Per-symbol таблицы, JSONB |
| Connection Pool | HikariCP | 6.0.0 | Высокопроизводительный пул |
| Статистика | t-Digest | 3.3 | Приближенные перцентили, O(log n) |
| JSON (биржа) | Jackson | 2.15.0 | Парсинг WebSocket-фреймов Binance |
| JSON (конфиг) | kotlinx.serialization | 1.6.0 | Типобезопасная конфигурация |
| Market Data Server | Kotlin/JVM (Ktor/Jetty) | 2.2.20 | REST API для footprint данных |
| Deploy | systemd + Makefile + SCP | — | Автоматический деплой на VPS |

| Компонент | Технология | Обоснование |
|-----------|------------|-------------|
| Бэкенд | Ktor | Единый стек с клиентом |
| База данных | PostgreSQL + TimescaleDB | Оптимизация для временных рядов |
| Кэш | Redis | Высокая производительность |
| Очереди | RabbitMQ / Kafka | Для обработки потоковых данных |

### 7.3. Инструменты разработки

| Инструмент | Назначение |
|------------|------------|
| Gradle | Сборка проекта |
| Version Catalog | Централизованное управление версиями |
| Convention plugins | Переиспользуемые конфигурации сборки |
| ktlint | Статический анализ кода |
| detekt | Дополнительный анализ |
| GitHub Actions | CI/CD |

---

## 8. Модульная структура проекта <a name="8"></a>

### 8.1. Полная структура (актуальная)

```
Nous-Platform/
├── gradle/
│   └── libs.versions.toml                             # Kotlin 2.3.0, Compose 1.7.3, Ktor 3.4.1
│
├── build-logic/
│   ├── build.gradle.kts
│   └── src/main/kotlin/conventions/
│       ├── KmpLibraryConvention.kt                    # jvm() + js(IR) + wasmJs()
│       ├── KmpFeatureConvention.kt                    # + Compose plugin
│       └── KmpApplicationConvention.kt                # + desktop app
│
├── public-api/
│   ├── api-market/                                    # Модели рынка: Candle, Trade, FootprintCandle
│   ├── api-trading/                                   # Заглушки (будущая торговля)
│   └── api-ui/                                        # Заглушки (будущие UI-виджеты)
│
├── platform-core/
│   ├── build.gradle.kts
│   └── src/
│       ├── commonMain/kotlin/com/aandios/nous/core/
│       │   ├── domain/repository/                     # ChartRepository, DomRepository, TradesRepository
│       │   ├── data/repository/                       # Реализации
│       │   ├── network/                               # NetworkManagerImpl, HttpClientFactory
│       │   ├── storage/                               # StateStore interface
│       │   └── ui/theme/                              # ChartColors, SymbolFormatter
│       ├── jvmMain/kotlin/                            # Platform.jvm, CoreModule (Koin)
│       ├── jsMain/kotlin/                             # Platform.js, Fonts.js
│       └── wasmJsMain/kotlin/                         # Platform.wasmJs, Fonts.wasmJs
│
├── core/
│   └── core-dependencies/                             # Shared deps: Ktor, Compose, kotlinx
│
├── features/
│   ├── feature-dom/                                   # Стакан: DomViewModel, DomWindow
│   │   └── src/commonMain/                            # AggregationLevel, TradingSymbol
│   ├── feature-chart/                                 # График: ChartViewModel, CandleStickChart
│   │   └── src/commonMain/
│   │       ├── ui/chart/                              # ChartInteraction, CandleStickChart, DrawingOverlay
│   │       ├── rendering/                             # CandleRenderer, FootprintRenderer, Crosshair
│   │       ├── tools/                                 # Drawing, DrawingHistory, DrawingRenderer
│   │       ├── footprint/                             # FootprintApiClient, LiquidationApiClient
│   │       ├── indicator/                             # LiquidationViewModel
│   │       ├── model/                                 # ChartLayout, CandleMetrics, PriceRange
│   │       └── utils/                                 # ChartCalculator, Format
│   ├── feature-trades/                                # Лента сделок: TradesViewModel, TradesWindow
│   ├── feature-localstorage/                          # SQLite storage (JVM only)
│   └── feature-settings/                              # Settings window (JVM only)
│
├── bidasker-web/                                      # ⭐ Bidasker SaaS footprint chart
│   ├── build.gradle.kts                               # js(IR) target
│   └── src/
│       ├── commonMain/kotlin/com/aandios/nous/bidasker/web/
│       │   ├── Main.kt                                # CanvasBasedWindow + URL params
│       │   ├── App.kt                                 # BidaskerApp composable
│       │   ├── Components.kt                          # FootprintToolbar, StatusBar
│       │   ├── DataLoader.kt                          # Ktor → market-data-server
│       │   └── TariffConfig.kt                        # Тарифные лимиты
│       └── jsMain/resources/
│           └── index.html                             # onWasmReady + Skiko init
│
├── chart2/                                            # ⭐ Новый pluggable chart API (в разработке)
│   └── ChartRenderer.kt                               # Интерфейс: CandleStick/Footprint/Bar renderers
│
├── providers/
│   ├── binance-provider/                              # Binance Futures adapters
│   │   └── src/
│   │       └── commonMain/                            # Chart, DOM, Trades, Liquidation adapters
│
├── composeApp/                                        # Десктопное приложение
│   ├── build.gradle.kts
│   └── src/jvmMain/                                   # Main.kt, AppModule, TerminalLayout
│
├── docs/
│   ├── Nous-Platform-Technical-Specification-v1.0.md  # Этот документ
│   ├── architecture/
│   ├── decisions/
│   └── vision/
│
├── plans/                                             # Планы разработки
│   └── workspace-tab-system.md                        # IDE-подобные табы
│
├── build.gradle.kts
├── settings.gradle.kts
└── gradle.properties
```

### 8.2. Описание модулей

#### 8.2.1. `public-api/*` (Open Source)
- **Назначение:** Единственное место, где живут публичные интерфейсы и модели
- **Видимость:** Полностью открыт для сообщества
- **Содержит:** Модели данных (Candle, Trade, FootprintCandle, LiquidationOrder), интерфейсы адаптеров, интерфейс Provider
- **Зависимости:** Kotlin Multiplatform + kotlinx.serialization + Ktor Client Core
- **Таргеты:** JVM ✅, JS ✅, WASM ✅

#### 8.2.2. `platform-core` (Closed Source)
- **Назначение:** Ядро платформы с бизнес-логикой, репозиториями, UI компонентами
- **Содержит:** Domain repositories (Chart/DOM/Trades), data implementations, NetworkManager, HttpClientFactory (`expect`/`actual`), ChartColors, SymbolFormatter, Terminal UI components
- **Зависимости:** `public-api`, `core-dependencies`, Compose Multiplatform
- **Таргеты:** JVM ✅, JS ✅, WASM ✅

#### 8.2.3. `features/*` (Closed Source)
- **feature-chart:** Самый развитый модуль. FootprintChart, CandleStickChart, Drawing tools (TrendLine/Horizontal/Rectangle/Ruler), Undo/Redo (Ctrl+Z/Y), FootprintApiClient, LiquidationApiClient, crosshair, zoom/pan
- **feature-dom:** DOM (стакан) с Binance sync protocol, aggregation levels
- **feature-trades:** Time & Sales, size filter
- **feature-localstorage:** SQLite persistence (JVM only)
- **feature-settings:** Storage/settings UI (JVM only)
- **Таргеты:** JVM ✅, JS ✅ (chart/dom/trades), WASM ✅

#### 8.2.4. `bidasker-web` (Closed Source) — ⭐ новый
- **Назначение:** SaaS footprint chart для Bidasker landing page
- **Содержит:** BidaskerApp composable, FootprintToolbar, Ktor HTTP client → market-data-server, тарифные лимиты, URL-param parsing
- **Переиспользует:** FootprintChart, FootprintRenderer, FootprintApiClient, AggregationLevel из features/chart и features/dom
- **Таргет:** Kotlin/JS (IR) — компилируется в JS + Skiko WASM, встраивается в Vue.js через iframe

#### 8.2.5. `chart2/` — ⭐ новый (в разработке)
- **Назначение:** Next-gen pluggable chart API
- **Содержит:** `ChartRenderer` интерфейс (CandleStickRenderer, FootprintRendererV2, BarRenderer), `ChartOverlay` интерфейс, `CrosshairOverlay`
- **Цель:** Универсальная библиотека чартов уровня TradingView — любые типы свечей/баров/футпринтов через плагинные рендереры

#### 8.2.6. `providers/*` (Open Source)
- **Назначение:** Адаптеры для конкретных бирж
- **Содержит:** WebSocket/REST клиенты для Binance Futures (aggTrade, forceOrder), модели данных Binance
- **Таргеты:** JVM ✅, JS ✅, WASM ✅

#### 8.2.7. `composeApp` (Closed Source)
- **Назначение:** Точка входа десктопного приложения
- **Содержит:** `main.kt`, AppModule (Koin DI), TerminalLayout, MainScreen
- **Таргет:** JVM ✅ (desktop only)
- **Зависимости:** Все feature-модули, все provider-модули

---

## 9. Плагинная система и экосистема — ОТЛОЖЕНО <a name="9"></a>

**Статус:** Перенесено на пост-инвестиционный этап (2028+)

**Причина:** Сложность реализации (изоляция ClassLoader, безопасность, API-дизайн) неоправданно задержит выход Live Trading. Сначала нужно доказать спрос и получить первых платных пользователей.

---

## 10. Требования к безопасности <a name="10"></a>

### 10.1. Безопасность на уровне приложения
- **Шифрование данных:** Все локальные данные (API-ключи, настройки) шифруются
- **Минимальные привилегии:** Приложение не запрашивает прав, выходящих за рамки необходимости

### 10.2. Безопасность сети
- **TLS:** Все соединения только по HTTPS/WSS
- **Проверка сертификатов:** Отсутствие самоподписанных сертификатов

### 10.3. Безопасность API-ключей (критично для Live Trading)
- **Хранение:** Ключи хранятся в системном хранилище ключей (Keychain на macOS, Credential Manager на Windows)
- **Использование:** Ключи никогда не передаются на серверы Nous Platform
- **Управление:** Возможность добавить/удалить/отозвать ключи

### 10.4. GDPR и конфиденциальность
- **Согласие:** Пользователь должен явно согласиться на сбор любой аналитики
- **Минимизация данных:** Собираем только необходимые данные
- **Право на забвение:** Возможность удалить все данные

---

## 11. Требования к интерфейсу <a name="11"></a>

### 11.1. Общие принципы
- **Тёмная тема:** По умолчанию, с возможностью переключения
- **Кастомизация:** Пользователь может настроить цвета, шрифты, раскладку
- **Профили:** Сохранение и загрузка нескольких профилей настроек

### 11.2. Модульные окна
- Все окна можно открепить от главного окна
- Окна можно перетаскивать и менять размер
- Окна можно сворачивать в панель инструментов
- Состояние окон сохраняется между сессиями

### 11.3. Цветовая схема

```kotlin
// Основные цвета
primary = "#00C853"      // Зелёный (бычий)
secondary = "#D32F2F"    // Красный (медвежий)
background = "#0A0A0A"   // Почти чёрный
surface = "#121212"      // Тёмно-серый
onSurface = "#CCCCCC"    // Светло-серый текст
```

### 11.4. Типографика
- **Моноширинный шрифт:** JetBrains Mono (по умолчанию)
- **Размеры:** 
  - Заголовки: 16-20px
  - Основной текст: 13-14px
  - Вспомогательный: 11-12px

### 11.5. Горячие клавиши
- `Ctrl+N` - Новый график
- `Ctrl+D` - Новый стакан
- `Ctrl+T` - Новый Order Flow
- `Ctrl+Tab` - Переключение между окнами
- `F1` - Помощь
- Полная кастомизация горячих клавиш

---

## 12. Требования к данным и хранилищам <a name="12"></a>

### 12.1. Локальное хранилище

#### 12.1.1. Настройки
- **Формат:** JSON
- **Место:** `~/.nous-platform/settings.json`
- **Содержит:** Настройки интерфейса, список избранных инструментов, горячие клавиши

#### 12.1.2. История сделок (дневник) — после инвестиций
- **Формат:** SQLDelight (SQLite)
- **Таблицы:** 
  - `trades` — все сделки
  - `notes` — заметки к сделкам
  - `tags` — теги для категоризации
  - `screenshots` — скриншоты графиков

#### 12.1.3. Кэш исторических данных
- **Формат:** SQLDelight с TimescaleDB-like расширениями
- **Хранение:** Минутные бары за последние 30 дней (для будущего бэктестинга)

### 12.2. Структура БД для истории (после инвестиций)

```sql
-- Сделки
CREATE TABLE trades (
    id TEXT PRIMARY KEY,
    symbol TEXT NOT NULL,
    side TEXT NOT NULL,
    quantity REAL NOT NULL,
    price REAL NOT NULL,
    timestamp INTEGER NOT NULL,
    fee REAL,
    fee_asset TEXT,
    note_id TEXT,
    strategy TEXT
);

-- Заметки
CREATE TABLE notes (
    id TEXT PRIMARY KEY,
    content TEXT NOT NULL,
    created_at INTEGER NOT NULL,
    updated_at INTEGER NOT NULL
);

-- Теги
CREATE TABLE tags (
    id TEXT PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
    color TEXT
);

-- Связь сделок с тегами
CREATE TABLE trade_tags (
    trade_id TEXT NOT NULL,
    tag_id TEXT NOT NULL,
    FOREIGN KEY(trade_id) REFERENCES trades(id),
    FOREIGN KEY(tag_id) REFERENCES tags(id)
);
```

### 12.3. Кэширование данных бирж

#### 12.3.1. Стакан
- Кэшируется последний снапшот
- Инкрементальные обновления применяются к кэшу

#### 12.3.2. Свечи
- Кэшируются последние N свечей для каждого таймфрейма
- При переключении таймфрейма загружаются недостающие данные

#### 12.3.3. Сделки
- Кэшируются последние 1000 сделок
- При обновлении добавляются в начало списка

---

## 13. Интеграция с внешними системами <a name="13"></a>

### 13.1. Биржи (MVP)
- **Binance Futures** (REST + WebSocket) ✅ — `@aggTrade`, `@forceOrder`, `@kline`, `@depth@100ms`
- **Bybit** (REST + WebSocket) — адаптер создан, не активирован в конфиге

### 13.2. Биржи (после инвестиций)
- OKX, Kraken, Coinbase, KuCoin, Bitget (через плагины сообщества)

### 13.3. Бэкенд-инфраструктура (собственные сервисы)

#### 13.3.1. trade-collector (Kotlin/JVM daemon)
- **Назначение:** Сбор, агрегация и статистический анализ криптовалютных сделок в реальном времени
- **Биржи:** Binance Futures (50 perpetual symbols, top по дневному объёму)
- **WebSocket:** Combined stream (aggTrade + forceOrder), 100 потоков в одном TCP-соединении
- **Watchdog:** Многослойная защита — Ktor pingInterval + application watchdog + exponential backoff
- **База:** PostgreSQL 16 (per-symbol таблицы: raw_trades, aggregates, filtered_trades, volume_windows, liquidations, liquidation_aggregates)
- **Аналитика:** t-Digest (статистика объёмов), footprint агрегация (1m + 15m), whale detection (>98% перцентиль)
- **Resilience:** Circuit Breaker, DiskBuffer, DeadLetterQueue, Watermark Recovery, Catch-up loop
- **Мониторинг:** HTTP API (:8080) — /health, /metrics, /status, /api/logs, /api/instruments
- **Деплой:** systemd на VPS, `make deploy` (JAR → SCP → restart → health check)

#### 13.3.2. market-data-server (Ktor/Jetty REST API)
- **Назначение:** REST API для выдачи пред-агрегированных footprint и liquidation данных
- **База:** PostgreSQL (читает таблицы, созданные trade-collector)
- **Эндпоинты:**
  - `GET /api/footprint` — footprint свечи (bid/ask объём по ценовым уровням)
  - `GET /api/instruments` — список доступных инструментов с количеством свечей
  - `GET /api/liquidations` — сырые ликвидации (timestamp, price, quantity, isLong)
  - `GET /api/liquidation-aggregates` — агрегированные ликвидации по минутам
  - `GET /api/symbols`, `/api/timeframes`, `/health`
- **Деплой:** systemd на VPS (порт 8085), `make deploy`

#### 13.3.3. Bidasker (Vue.js + Kotlin/JS SaaS)
- **Назначение:** Freemium footprint chart сервис, воронка для Nous Platform
- **Frontend:** Vue 3 + Vite, iframe-интеграция Kotlin/JS чарта
- **Backend:** market-data-server REST API (тарифные лимиты через JSON-конфиг)
- **Деплой:** GitHub Pages + GitHub Actions

### 13.4. Экспорт данных (Осторожно см. Пользовательские соглашения бирж)
- CSV (сделки, свечи)
- JSON (для API)
- PNG/JPEG (скриншоты графиков)
- PDF (отчёты)

### 13.5. Импорт данных
- CSV (исторические данные из других платформ)
- JSON (настройки, профили)

---

## 14. Монетизация и бизнес-модель <a name="14"></a>

### 14.1. Бесплатная модель (Freemium)
- **Бесплатно:**
  - Чтение данных с бирж
  - Базовые графики и индикаторы
  - Стакан и Order Flow
  - Paper trading

- **Платно (подписка "Pro"):**
  - **Live Trading** (реальные ордера)
  - Footprint / Delta
  - Delta Profile
  - Индикатор ликвидаций
  - Расширенный модуль портфеля
  - Приоритетная поддержка

### 14.2. Модель ценообразования
- **Pro подписка:** $29.9/месяц или $299/год
- **Бесплатный пробный период:** 7 дней

### 14.3. Маркетплейс и плагины — ПОСЛЕ ИНВЕСТИЦИЙ

---

## 15. План разработки (соло, 15 дней/мес) — ОБНОВЛЕНО <a name="15"></a>

### 15.1. Ресурс
- **15 дней в месяц × 7 часов = 105 часов кода**
- Full-time эквивалент: 60%

### 15.2. Детальный план по месяцам

| Месяц | Задачи | Часов | Результат |
|-------|--------|-------|-----------|
| 1 | Архитектура, public-api, platform-core, convention plugins | 100 | Модульная структура готова ✅ |
| 2 | Binance WebSocket, модели, feature-dom (mock) | 100 | Данные с биржи идут ✅ |
| 3 | Стакан + лента сделок + свечной график (реальные данные) | 110 | Базовый UI работает ✅ |
| 4 | WASM/JS таргеты, Bidasker SaaS, trade-collector v3 | 110 | Web target + бэкенд ✅ |
| 5 | Индикаторы (SMA, EMA, VWAP) + Footprint/Delta | 100 | Проф. анализ ✅ |
| 6 | **Workspace & Tab System** (Phase 1-2: модели + ViewModels) | 110 | Модели, ProviderPool, TabManager |
| 7 | **Workspace & Tab System** (Phase 3-4: UI + interaction) | 110 | TerminalShell, Drag&Drop, Floating Windows |
| 8 | Инструменты рисования ✅ + полировка UI + исправление багов | 100 | Стабильная версия ✅ |
| 9 | **Live Trading** (размещение ордеров, ключи, баланс, портфель) | 100 | Первая реальная торговля |
| 10 | Индикатор ликвидаций ✅, workspace integration, публичный релиз | 80 | Продукт в мире |
| 11-12 | Сбор обратной связи, фичи по запросу, оптимизация, рост | 100/мес | 1000 MAU, 50+ платных |

### 15.3. Ключевые вехи

| Месяц | Веха |
|-------|------|
| 3 | Прототип с данными ✅ |
| 4 | Bidasker SaaS + WASM/JS targets ✅ |
| 6 | **Workspace & Tab System (модели)** |
| 7 | **Workspace & Tab System (UI)** |
| 8 | Chart drawing tools ✅ |
| 9 | **Live Trading готов** |
| 10 | **Публичный релиз** |
| 11 | Первые платные подписки |
| 12 | **1000 MAU, 50+ платных** |

### 15.4. Что НЕ делаем на соло-этапе
- Плагинную систему
- DSL и редактор кода
- Бэктестинг
- Маркетплейс
- Поддержку 5+ бирж (только Binance + Bybit)
- Сложную анимацию
- Свой график с нуля

---

## 16. Риски и пути их минимизации — РАСШИРЕНО <a name="16"></a>

### 16.1. Технические риски (соло)

| Риск | Вероятность | Влияние | Митигация |
|------|-------------|---------|------------|
| **Выгорание** | Высокая (70%) | Критическое | Чёткий график, выходные, спорт, маленькие победы каждые 2 недели |
| **Сложные баги в WebSocket** | Средняя (40%) | Высокое | Логирование, авто-переподключение, fallback на REST |
| **Проблемы с производительностью графика** | Высокая (60%) | Высокое | Использовать легковесную библиотеку, не писать свой Canvas с нуля |
| **Утечки памяти** | Средняя (30%) | Среднее | Профилирование раз в 2 недели |
| **Безопасность API-ключей** | Низкая (10%) | Критическое | Использовать системное хранилище, никогда не логировать ключи |
| **Ошибки в расчёте дельты/футпринта** | Средняя (40%) | Высокое | Модульные тесты, сравнение с эталонными данными |
| **Проблемы с компиляцией KMP** | Средняя (40%) | Среднее | Использовать стабильные версии, не гнаться за обновлениями |

### 16.2. Бизнес-риски

| Риск | Вероятность | Влияние | Митигация |
|------|-------------|---------|------------|
| **Низкий спрос** | Средняя (50%) | Высокое | Запустить MVP быстро, опросить трейдеров до начала |
| **Пользователи не готовы платить за Live Trading** | Средняя (40%) | Высокое | Бесплатный пробный период 7 дней, собрать фидбек |
| **Конкуренты (ATAS, CScalp, TradingView)** | Высокая (70%) | Среднее | Уникальное преимущество — Live Trading + крипта + футпринт |
| **Изменение API бирж** | Низкая (10%) | Среднее | Абстракция провайдеров, быстрое реагирование |
| **Регуляторные риски** | Низкая (5%) | Высокое | Не храним средства пользователей, только API-ключи |
| **Блокировка API-ключей биржей** | Низкая (10%) | Среднее | Чётко соблюдать rate limits, не злоупотреблять |

### 16.3. Риски времени и фокуса

| Риск | Вероятность | Влияние | Митигация |
|------|-------------|---------|------------|
| **Отвлечение на быт/работу** | Высокая (80%) | Среднее | Резервировать 2 полных дня в неделю только на код |
| **Потеря мотивации** | Средняя (40%) | Высокое | Маленькие победы, ранний релиз, обратная связь |
| **Перфекционизм** | Очень высокая (90%) | Критическое | "Грязный, но работающий" лучше идеального, но недоделанного |
| **Оценка времени ошиблась** | Высокая (70%) | Среднее | Добавить 30% буфера к каждой оценке |

### 16.4. Риски сообщества и маркетинга

| Риск | Вероятность | Влияние | Митигация |
|------|-------------|---------|------------|
| **Никто не узнает о продукте** | Высокая (60%) | Высокое | Сделать контент (YouTube, Twitter, Telegram) с 1-го месяца |
| **Отрицательный фидбек** | Средняя (50%) | Среднее | Быстро фиксить баги, не игнорировать пользователей |
| **Токсичное сообщество** | Низкая (20%) | Низкое | Модерация, чёткие правила |

### 16.5. Финансовые риски (до инвестиций)

| Риск | Вероятность | Влияние | Митигация |
|------|-------------|---------|------------|
| **Не хватает денег на жизнь** | Зависит от ситуации | Критическое | Иметь запас на 12 месяцев, подработка, фриланс |
| **Никто не покупает Pro подписку** | Средняя (40%) | Высокое | Пересмотреть цену, добавить больше фич в бесплатную версию |
| **Инвесторы не заходят** | Средняя (50%) | Высокое | Bootstrapping дольше, искать гранты, bounty-программы |

### 16.6. Стратегия выхода из критических рисков

| Сценарий | Действие |
|----------|----------|
| Выгорание | Взять паузу 1-2 недели, снизить expectations |
| Продукт не взлетает через 6 месяцев после релиза | Pivot: сделать инструмент для конкретной ниши (например, только ликвидации) |
| Конкуренты выпускают аналогичную фичу | Усилить уникальное преимущество (скорость, простота, поддержка) |
| Нет платных пользователей | Сделать пожертвования (donation), открыть часть Pro кода |

---

## 17. Заключение <a name="17"></a>

**Nous Platform v1.1** — это реалистичный план для соло-разработчика:

- **6 месяцев** до первого Live Trading
- **8 месяцев** до публичного релиза
- **12 месяцев** до 1000 пользователей и 50+ платных подписок
- **После этого** — привлечение инвестиций и масштабирование

**Ключевой принцип:**  
Сначала работающий продукт и живые пользователи.  
Потом экосистема, плагины, DSL и маркетплейс.

**Документ будет обновляться по мере прохождения этапов.**