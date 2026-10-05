/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui

import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateMapOf
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.api.market.commands.*
import com.aandios.nous.api.market.paper.PaperTrading
import com.aandios.nous.api.market.paper.effectiveTrading
import com.aandios.nous.api.market.model.orderbook.DomEvent
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.core.Disposable
import com.aandios.nous.core.data.repository.SymbolInfoRepositoryImpl
import com.aandios.nous.core.domain.repository.DomRepository
import com.aandios.nous.core.domain.repository.SymbolInfoRepository
import com.aandios.nous.feature.dom.data.repository.DomRepositoryImpl
import com.aandios.nous.feature.dom.domain.DomOptions
import com.aandios.nous.feature.dom.domain.TradingSymbol
import com.aandios.nous.feature.dom.domain.model.OrderIntent
import com.aandios.nous.feature.dom.ui.model.BestPricesState
import com.aandios.nous.feature.dom.ui.model.DomLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlin.math.roundToLong

/**
 * Стакан (DOM). Архитектура «одна таблица» на partial-стриме:
 * каждое окно (топ-N уровней с абсолютными объёмами) полностью заменяет
 * книгу — [levels] (SnapshotStateMap, O(1), точечная инвалидация), а UI
 * читает отсортированный вид [sortedLevels] (derivedStateOf).
 *
 * Провайдер данных выбирается через [ProviderRegistry]: адаптеры резолвятся
 * по [DomOptions.provider] (id зарегистрированного провайдера), переключение
 * — обычная смена опций с переподпиской.
 */
/** Уведомление DOM-панели (snackbar под заголовком). */
data class DomNotification(val id: Long, val text: String)

class DomViewModel(
    private val providerRegistry: ProviderRegistry,
    private val coroutineDispatcher: CoroutineDispatcher? = null,
) : Disposable {
    private val dispatcher = coroutineDispatcher ?: Dispatchers.Main
    private val viewModelScope = CoroutineScope(dispatcher + SupervisorJob())
    private var subscriptionJob: Job? = null

    private val _domOptions = MutableStateFlow(DomOptions.default())
    val domOptions: StateFlow<DomOptions> = _domOptions.asStateFlow()

    private val _selectedPrice = MutableStateFlow<Double?>(null)
    val selectedPrice: StateFlow<Double?> = _selectedPrice.asStateFlow()

    private val _orderQuantity = MutableStateFlow("0.01")
    val orderQuantity: StateFlow<String> = _orderQuantity.asStateFlow()

    private val _isTradingEnabled = MutableStateFlow(true)
    val isTradingEnabled: StateFlow<Boolean> = _isTradingEnabled.asStateFlow()

    /** Reduce-only для следующих ордеров (закрытие позиций). */
    private val _reduceOnly = MutableStateFlow(false)
    val reduceOnly: StateFlow<Boolean> = _reduceOnly.asStateFlow()

    /** Тип лимитных ордеров (LIMIT/POST_ONLY/IOC/FOK). */
    private val _limitOrderType = MutableStateFlow(OrderType.LIMIT)
    val limitOrderType: StateFlow<OrderType> = _limitOrderType.asStateFlow()

    /**
     * Paper-режим ЭТОЙ DOM-панели (независимый, как и у графиков):
     * свои ордера уходят в общий paper-движок, чужие — в реальный адаптер.
     */
    private val _paperEnabled = MutableStateFlow(false)
    val paperEnabled: StateFlow<Boolean> = _paperEnabled.asStateFlow()

    /** Плечо для ордеров DOM (null — дефолт биржи/1x в paper). */
    private val _leverage = MutableStateFlow<Int?>(null)
    val leverage: StateFlow<Int?> = _leverage.asStateFlow()

    /**
     * Confirm: включён — клик по уровню выбирает цену (подсветка) и ордера
     * требуют подтверждения.
     */
    private val _confirmOrders = MutableStateFlow(false)
    val confirmOrders: StateFlow<Boolean> = _confirmOrders.asStateFlow()

    /** Отложенный интент (Confirm: ON) + его текст для строки подтверждения. */
    private val _pendingIntent = MutableStateFlow<OrderIntent?>(null)
    val pendingIntent: StateFlow<OrderIntent?> = _pendingIntent.asStateFlow()

    private val _pendingIntentText = MutableStateFlow<String?>(null)
    val pendingIntentText: StateFlow<String?> = _pendingIntentText.asStateFlow()

    /** Snackbar-уведомления DOM (как в chart trading) — под заголовком. */
    private val _notifications = MutableStateFlow<List<DomNotification>>(emptyList())
    val notifications: StateFlow<List<DomNotification>> = _notifications.asStateFlow()
    private var notificationSeq = 0L
    private var noticesJob: Job? = null

    private val _lastCommandResult = MutableStateFlow<CommandResult?>(null)
    val lastCommandResult: StateFlow<CommandResult?> = _lastCommandResult.asStateFlow()

    private val _loadedSymbols = MutableStateFlow<List<TradingSymbol>>(emptyList())
    val loadedSymbols: StateFlow<List<TradingSymbol>> = _loadedSymbols.asStateFlow()

    private val _symbolTickSize = MutableStateFlow<Double?>(null)
    val symbolTickSize: StateFlow<Double?> = _symbolTickSize.asStateFlow()

    private val _symbolStepSize = MutableStateFlow<Double?>(null)
    val symbolStepSize: StateFlow<Double?> = _symbolStepSize.asStateFlow()

    // --- Scaled-Long модель ---

    private var tickSize: Double = 0.0
    private var stepSize: Double = 0.0
    private val scaleReady: Boolean get() = tickSize > 0.0 && stepSize > 0.0

    private var aggMultiplier: Long = 1

    // ЕДИНСТВЕННАЯ таблица уровней: её читает UI (лесенка: levelAt(key) по строке цены)
    private val levels = mutableStateMapOf<Long, DomLevel>()
    private val sortedLevelsState = derivedStateOf {
        levels.values.sortedByDescending { it.priceTicks }
    }
    val sortedLevels: List<DomLevel> get() = sortedLevelsState.value

    /** Таблица уровней для рендера лесенки (чтение по ключу реактивно). */
    internal val levelsMap: Map<Long, DomLevel> get() = levels

    /** Шаг строки лесенки в тиках (размер корзины агрегации). */
    internal val ladderStepTicks: Long get() = aggMultiplier

    // Одно состояние лучших цен (1 запись на тик вместо шести)
    private val _bestPrices = MutableStateFlow(BestPricesState())
    val bestPrices: StateFlow<BestPricesState> = _bestPrices.asStateFlow()

    // Последнее окно книги по цене (Double) — источник для пересборки при смене агрегации
    private val bidsByPrice = HashMap<Double, Double>()
    private val asksByPrice = HashMap<Double, Double>()

    // Читаются только тестами
    internal val windowBids: Map<Double, Double> get() = bidsByPrice
    internal val windowAsks: Map<Double, Double> get() = asksByPrice

    override fun dispose() {
        subscriptionJob?.cancel()
        viewModelScope.cancel()
    }

    init {
        loadSymbols(_domOptions.value.provider)
        viewModelScope.launch {
            delay(500)
            fetchSymbolMetadata(_domOptions.value.symbol.symbol)
        }
        restartSubscription(_domOptions.value)
        ensureNoticesSubscription()
    }

    fun updateDomOptions(newOptions: DomOptions) {
        val oldOptions = _domOptions.value
        if (oldOptions != newOptions) {
            val aggChanged = oldOptions.aggregation.multiplier != newOptions.aggregation.multiplier
            _domOptions.value = newOptions
            updateAggMultiplier()

            val providerChanged = oldOptions.provider != newOptions.provider
            val subscriptionChanged =
                providerChanged ||
                oldOptions.symbol != newOptions.symbol ||
                oldOptions.depth != newOptions.depth

            if (subscriptionChanged) {
                restartSubscription(newOptions)
            }
            if (providerChanged) {
                loadSymbols(newOptions.provider)
                fetchSymbolMetadata(newOptions.symbol.symbol)
                ensureNoticesSubscription()
            } else if (oldOptions.symbol != newOptions.symbol) {
                fetchSymbolMetadata(newOptions.symbol.symbol)
            }
            if (aggChanged && !subscriptionChanged) {
                rebuildLevelsFromWindow()
            }
        }
    }

    private fun updateAggMultiplier() {
        aggMultiplier = _domOptions.value.aggregation.multiplier.roundToLong()
    }

    private fun restartSubscription(options: DomOptions) {
        subscriptionJob?.cancel()
        subscriptionJob = viewModelScope.launch {
            updateAggMultiplier()
            subscribeToBookWindows(options)
        }
    }

    private suspend fun subscribeToBookWindows(options: DomOptions) {
        bidsByPrice.clear(); asksByPrice.clear()
        levels.clear()
        _bestPrices.value = BestPricesState()

        val provider = providerRegistry.get(options.provider) ?: run {
            println("❌ DOM: provider ${options.provider} not registered")
            return
        }
        val domAdapter = provider.dom
        val bookTickerAdapter = provider.bookTicker
        if (domAdapter == null || bookTickerAdapter == null) {
            println("❌ DOM: provider ${provider.config.displayName} has no DOM/bookTicker adapters")
            return
        }
        // Paper engine: ставки комиссий из данных активной биржи (если DOM в paper)
        if (_paperEnabled.value) {
            val rates = runCatching { provider.trading?.getFeeRates(options.symbol.symbol) }.getOrNull()
            PaperTrading.adapter.setFeeRates(options.symbol.symbol, rates)
        }
        val repository: DomRepository = DomRepositoryImpl(domAdapter, bookTickerAdapter)

        repository.subscribeToDomEvents(
            symbol = options.symbol.symbol,
            depth = options.depth.value
        ).catch { e ->
            println("❌ DOM Events Error: ${e.message}")
            e.printStackTrace()
        }.collect { event ->
            processDomEvent(event)
        }
    }

    // ── Обработка событий ──

    private fun processDomEvent(event: DomEvent) {
        when (event) {
            is DomEvent.BookWindow -> handleBookWindow(event)
            is DomEvent.BestPrices -> handleBestPrices(event)
        }
    }

    /** Окно полностью заменяет книгу (partial-стрим, абсолютные объёмы). */
    private fun handleBookWindow(event: DomEvent.BookWindow) {
        bidsByPrice.clear()
        asksByPrice.clear()
        event.bids.forEach { u -> bidsByPrice[u.price] = u.quantity }
        event.asks.forEach { u -> asksByPrice[u.price] = u.quantity }

        // Метаданные (tickSize/stepSize) могли ещё не прийти — окно пропускаем,
        // следующее (через 100мс) отрисует книгу целиком
        if (scaleReady) rebuildLevelsFromWindow()
    }

    private fun handleBestPrices(event: DomEvent.BestPrices) {
        // Paper engine: последняя цена — mark для исполнения бумажных ордеров
        if (event.lastPrice > 0.0) {
            PaperTrading.adapter.feedMarkPrice(_domOptions.value.symbol.symbol, event.lastPrice)
        }
        val bidTicks = toPriceTicksOrNull(event.bestBid)
        val askTicks = toPriceTicksOrNull(event.bestAsk)
        _bestPrices.value = BestPricesState(
            bestBid = event.bestBid,
            bestAsk = event.bestAsk,
            bestBidQuantity = event.bestBidQuantity,
            bestAskQuantity = event.bestAskQuantity,
            bestBidDisplayTicks = bidTicks?.let { bucketKey(it) },
            bestAskDisplayTicks = askTicks?.let { bucketKey(it) },
            lastPriceDisplayTicks = if (event.lastPrice > 0.0) {
                toPriceTicksOrNull(event.lastPrice)?.let { bucketKey(it) }
            } else null,
        )
    }

    // ── Пересборка и обрезка ──

    /** Пересборка уровней из последнего окна (новое окно, смена агрегации). */
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
        asksByPrice.forEach { (price, qty) ->
            val pt = toPriceTicks(price)
            val qs = toQtySteps(qty)
            if (qs <= 0L) return@forEach
            val key = bucketKey(pt)
            val cur = levels[key]?.askSteps ?: 0L
            levels[key] = DomLevel(key, askSteps = cur + qs)
        }
    }

    // ── Конвертация ──

    private fun bucketKey(priceTicks: Long): Long = priceTicks / aggMultiplier * aggMultiplier

    private fun toPriceTicks(price: Double): Long = (price / tickSize).roundToLong()
    private fun toQtySteps(qty: Double): Long = (qty / stepSize).roundToLong()
    private fun toPriceTicksOrNull(price: Double): Long? = if (scaleReady) toPriceTicks(price) else null

    // ── Команды (без изменений) ──

    fun executeCommand(command: TradingCommand?) {
        if (command != null) {
            viewModelScope.launch {
                if (!_isTradingEnabled.value) {
                    _lastCommandResult.value = CommandResult.TradingDisabled
                    return@launch
                }
                if (!command.canExecute()) {
                    _lastCommandResult.value = CommandResult.Error("Cannot execute command: ${command.getDescription()}")
                    return@launch
                }
                command.execute()
            }
        }
    }

    fun selectPrice(price: Double?) {
        if (price == null) {
            _selectedPrice.value = null
            return
        }
        // Подсветка выбранной цены нужна только при Confirm: ON (лимитный
        // ордер) — чтобы видеть цену до подтверждения. Повторный клик снимает.
        if (!_confirmOrders.value) return
        _selectedPrice.value = if (_selectedPrice.value == price) null else price
    }

    fun updateOrderQuantity(quantity: String) {
        _orderQuantity.value = quantity
    }

    fun setReduceOnly(reduceOnly: Boolean) {
        _reduceOnly.value = reduceOnly
    }

    fun setLimitOrderType(orderType: OrderType) {
        _limitOrderType.value = orderType
    }

    fun setLeverage(leverage: Int?) {
        _leverage.value = leverage?.takeIf { it > 0 }
    }

    /** Confirm: ON — выбор цены кликом и подтверждение ордеров. */
    fun setConfirmOrders(confirm: Boolean) {
        _confirmOrders.value = confirm
        if (!confirm) {
            _selectedPrice.value = null
            cancelPendingIntent()
        }
    }

    fun confirmPendingIntent() {
        val intent = _pendingIntent.value ?: return
        _pendingIntent.value = null
        _pendingIntentText.value = null
        executeIntent(intent)
    }

    fun cancelPendingIntent() {
        _pendingIntent.value = null
        _pendingIntentText.value = null
    }

    /** Закрыть snackbar-уведомление (крестик/таймаут). */
    fun dismissNotification(id: Long) {
        _notifications.value = _notifications.value.filterNot { it.id == id }
    }

    private fun notify(text: String) {
        val id = ++notificationSeq
        _notifications.value = (_notifications.value + DomNotification(id, text)).takeLast(5)
    }

    private fun resultCallback(): (CommandResult) -> Unit = { result ->
        _lastCommandResult.value = result
        notify(resultText(result))
    }

    private fun resultText(result: CommandResult): String = when (result) {
        is CommandResult.Success -> {
            val d = result.orderData
            when {
                d.symbol == "SYSTEM" && d.quantity > 0 -> "Closed ${d.quantity.toInt()} positions"
                d.symbol == "SYSTEM" -> "OK"
                else -> "${d.type} ${d.side} ${d.quantity}"
            }
        }
        is CommandResult.Error -> result.message
        CommandResult.TradingDisabled -> "Trading disabled"
    }

    private fun tradingAdapter() =
        providerRegistry.get(_domOptions.value.provider)?.effectiveTrading(_paperEnabled.value)

    /** Переключение paper/live этой DOM-панели. */
    fun setPaperEnabled(enabled: Boolean) {
        _paperEnabled.value = enabled
        ensureNoticesSubscription()
    }

    /** Подписка на уведомления активного адаптера (отказы движка). */
    private fun ensureNoticesSubscription() {
        noticesJob?.cancel()
        noticesJob = tradingAdapter()?.notices()?.let { flow ->
            viewModelScope.launch {
                flow.collect { text -> notify(text) }
            }
        }
    }

    /** Закрыть все открытые позиции символа панели (market reduce-only). */
    fun closeAllPositions() {
        viewModelScope.launch {
            val adapter = tradingAdapter() ?: return@launch
            val symbol = _domOptions.value.symbol.symbol
            val positions = runCatching { adapter.getPositions() }
                .getOrDefault(emptyList())
                .filter { it.symbol.uppercase() == symbol.uppercase() }
            if (positions.isEmpty()) {
                val result = CommandResult.Error("No open positions for $symbol")
                _lastCommandResult.value = result
                notify(resultText(result))
                return@launch
            }
            var closed = 0
            positions.forEach { p ->
                val r = runCatching { adapter.closePosition(p.symbol, p.positionId, p.quantity) }.getOrNull()
                if (r != null && r.success) closed++
            }
            val result = if (closed > 0) {
                CommandResult.Success(
                    OrderData("SYSTEM", com.aandios.nous.api.market.model.orderbook.OrderSide.BUY, OrderType.MARKET, quantity = closed.toDouble())
                )
            } else {
                CommandResult.Error("Failed to close positions")
            }
            _lastCommandResult.value = result
            notify(resultText(result))
        }
    }

    /** Отменить все ордера символа панели. */
    fun cancelAllOrders() {
        viewModelScope.launch {
            val adapter = tradingAdapter() ?: return@launch
            val symbol = _domOptions.value.symbol.symbol
            val ok = runCatching { adapter.cancelAllOrders(symbol) }.getOrDefault(false)
            val result = if (ok) {
                CommandResult.Success(
                    OrderData("SYSTEM", com.aandios.nous.api.market.model.orderbook.OrderSide.BUY, OrderType.MARKET, quantity = 0.0)
                )
            } else {
                CommandResult.Error("Failed to cancel orders")
            }
            _lastCommandResult.value = result
            notify(resultText(result))
        }
    }

    fun handleOrderIntent(intent: OrderIntent) {
        if (intent == OrderIntent.ToggleTrading) {
            val newValue = !_isTradingEnabled.value
            _isTradingEnabled.value = newValue
            val result = if (newValue) {
                CommandResult.Success(
                    OrderData("SYSTEM", com.aandios.nous.api.market.model.orderbook.OrderSide.BUY, OrderType.MARKET, quantity = 0.0)
                )
            } else {
                CommandResult.TradingDisabled
            }
            _lastCommandResult.value = result
            notify(if (newValue) "Trading ON" else "Trading OFF")
            return
        }
        // Confirm: ON — сначала строка подтверждения, затем исполнение
        if (_confirmOrders.value) {
            _pendingIntent.value = intent
            _pendingIntentText.value = intentDescription(intent)
            return
        }
        executeIntent(intent)
    }

    /** Построить и выполнить команду по интенту. */
    private fun executeIntent(intent: OrderIntent) {
        val adapter = tradingAdapter()
        val reduceOnly = _reduceOnly.value
        val callback = resultCallback()
        val command = when (intent) {
            is OrderIntent.MarketBuy -> BuyMarketCommand(intent.symbol, intent.quantity, reduceOnly, _leverage.value, adapter, callback)
            is OrderIntent.MarketSell -> SellMarketCommand(intent.symbol, intent.quantity, reduceOnly, _leverage.value, adapter, callback)
            is OrderIntent.LimitBuy -> BuyLimitCommand(intent.symbol, intent.price, intent.quantity, _limitOrderType.value, reduceOnly, _leverage.value, adapter, callback)
            is OrderIntent.LimitSell -> SellLimitCommand(intent.symbol, intent.price, intent.quantity, _limitOrderType.value, reduceOnly, _leverage.value, adapter, callback)
            is OrderIntent.BestBidBuy -> BuyBestBidCommand(intent.symbol, intent.bestBidPrice, intent.quantity, reduceOnly, _leverage.value, adapter, callback)
            is OrderIntent.BestAskSell -> SellBestAskCommand(intent.symbol, intent.bestAskPrice, intent.quantity, reduceOnly, _leverage.value, adapter, callback)
            OrderIntent.ToggleTrading -> null
        }
        executeCommand(command)
    }

    /** Текст подтверждения: MARKET BUY 0.01 · LIMIT SELL 0.01 @ 120.15. */
    private fun intentDescription(intent: OrderIntent): String = when (intent) {
        is OrderIntent.MarketBuy -> "MARKET BUY ${intent.quantity}"
        is OrderIntent.MarketSell -> "MARKET SELL ${intent.quantity}"
        is OrderIntent.LimitBuy -> "${_limitOrderType.value.name} BUY ${intent.quantity} @ ${
            com.aandios.nous.core.ui.format.SymbolFormatter.DEFAULT.formatPrice(intent.price)
        }"
        is OrderIntent.LimitSell -> "${_limitOrderType.value.name} SELL ${intent.quantity} @ ${
            com.aandios.nous.core.ui.format.SymbolFormatter.DEFAULT.formatPrice(intent.price)
        }"
        is OrderIntent.BestBidBuy -> "BEST BID BUY ${intent.quantity} @ ${
            com.aandios.nous.core.ui.format.SymbolFormatter.DEFAULT.formatPrice(intent.bestBidPrice)
        }"
        is OrderIntent.BestAskSell -> "BEST ASK SELL ${intent.quantity} @ ${
            com.aandios.nous.core.ui.format.SymbolFormatter.DEFAULT.formatPrice(intent.bestAskPrice)
        }"
        OrderIntent.ToggleTrading -> ""
    }

    // ── Загрузка метаданных ──

    private fun loadSymbols(providerId: String) {
        val symbolInfoAdapter = providerRegistry.get(providerId)?.symbolInfo ?: return
        val repository: SymbolInfoRepository = SymbolInfoRepositoryImpl(symbolInfoAdapter)
        viewModelScope.launch {
            try {
                val allSymbols = repository.getAllSymbolsInfo()
                val tradingSymbols = allSymbols
                    .filter { it.status == "TRADING" }
                    .map { TradingSymbol.fromSymbolInfo(it, providerId) }
                    .sortedBy { it.symbol }
                if (tradingSymbols.isNotEmpty()) _loadedSymbols.value = tradingSymbols
            } catch (e: Exception) {
                println("⚠️ Failed to load symbols from SymbolInfoRepository: ${e.message}")
            }
        }
    }

    private fun fetchSymbolMetadata(symbol: String) {
        val symbolInfoRepository = providerRegistry.get(_domOptions.value.provider)
            ?.symbolInfo?.let { SymbolInfoRepositoryImpl(it) }
            ?: return
        viewModelScope.launch {
            try {
                val info = symbolInfoRepository.getSymbolInfo(symbol) ?: return@launch
                tickSize = info.tickSize
                stepSize = info.stepSize
                _symbolTickSize.value = tickSize
                _symbolStepSize.value = stepSize
                // Метаданные могли прийти после первого окна — пересобираем
                // книгу из последнего окна, чтобы стакан появился сразу
                if (bidsByPrice.isNotEmpty() || asksByPrice.isNotEmpty()) {
                    rebuildLevelsFromWindow()
                }
            } catch (e: Exception) {
                println("❌ Failed to fetch metadata for $symbol: ${e.message}")
            }
        }
    }
}
