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
import com.aandios.nous.api.market.adapters.replaceOrder
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
private fun fmtQty(v: Double): String {
    var s = v.toString()
    if ('.' in s) s = s.trimEnd('0').trimEnd('.')
    return s
}

/** Уведомление DOM-панели (snackbar под заголовком). */
data class DomNotification(val id: Long, val text: String)

class DomViewModel(
    private val providerRegistry: ProviderRegistry,
    private val coroutineDispatcher: CoroutineDispatcher? = null,
    private val stateStore: com.aandios.nous.core.storage.StateStore? = null,
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

    /** id DOM-панели для panel-scoped персиста (paper). */
    private var panelKey: String? = null

    // ── Ордера/позиции символа панели (для отображения в лесенке) ──

    private val _orders = MutableStateFlow<List<com.aandios.nous.api.market.model.trading.Order>>(emptyList())
    val orders: StateFlow<List<com.aandios.nous.api.market.model.trading.Order>> = _orders.asStateFlow()

    private val _positions = MutableStateFlow<List<com.aandios.nous.api.market.model.trading.Position>>(emptyList())
    val positions: StateFlow<List<com.aandios.nous.api.market.model.trading.Position>> = _positions.asStateFlow()

    /** Живая mark-цена символа (для PnL позиции в лесенке). */
    private val _markPrice = MutableStateFlow(0.0)
    val markPrice: StateFlow<Double> = _markPrice.asStateFlow()

    private var ordersLiveJob: Job? = null
    private var positionsLiveJob: Job? = null
    private var liveAdapter: com.aandios.nous.api.market.adapters.TradingAdapter? = null

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
        refreshTradingState()
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
                qtyUserEdited = false
                fetchSymbolMetadata(newOptions.symbol.symbol)
                ensureNoticesSubscription()
            } else if (oldOptions.symbol != newOptions.symbol) {
                qtyUserEdited = false
                fetchSymbolMetadata(newOptions.symbol.symbol)
            }
            if (aggChanged && !subscriptionChanged) {
                rebuildLevelsFromWindow()
            }
            // Символ/провайдер могли смениться — перечитать ордера/позиции
            refreshTradingState()
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
        // Paper engine: mark-цена (last, иначе середина bid/ask) — чтобы
        // бумажные ордера исполнялись с любым провайдером
        val mark = when {
            event.lastPrice > 0.0 -> event.lastPrice
            event.bestBid > 0.0 && event.bestAsk > 0.0 -> (event.bestBid + event.bestAsk) / 2
            event.bestBid > 0.0 -> event.bestBid
            event.bestAsk > 0.0 -> event.bestAsk
            else -> 0.0
        }
        if (mark > 0.0) {
            _markPrice.value = mark
            PaperTrading.adapter.feedMarkPrice(_domOptions.value.symbol.symbol, mark)
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
        // Confirm: ON — клик выбирает цену (подсветка) для Limit Buy/Sell;
        // повторный клик снимает. Ордер — только после подтверждения.
        if (_confirmOrders.value) {
            _selectedPrice.value = if (_selectedPrice.value == price) null else price
            return
        }
        // Confirm: OFF — клик по уровню сразу размещает лимитку по этой цене:
        // выше рынка — SELL, ниже — BUY (как click-to-place в chart trading)
        val symbol = _domOptions.value.symbol.symbol
        val qty = _orderQuantity.value.toDoubleOrNull() ?: 0.0
        if (qty <= 0.0) return
        val ref = currentMarkFromBestPrices()
        val intent = if (ref > 0.0 && price >= ref) {
            OrderIntent.LimitSell(symbol, price, qty)
        } else {
            OrderIntent.LimitBuy(symbol, price, qty)
        }
        handleOrderIntent(intent)
    }

    fun updateOrderQuantity(quantity: String) {
        _orderQuantity.value = quantity
        qtyUserEdited = true
    }

    /** Пользователь правил qty — не перетираем его minQty инструмента. */
    private var qtyUserEdited = false

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
        stateStore?.let { store ->
            viewModelScope.launch {
                store.putString(paperStoreKey(), if (enabled) "1" else "0")
            }
        }
        // Адаптер сменился — переподписка и перечитка ордеров/позиций
        ordersLiveJob?.cancel()
        positionsLiveJob?.cancel()
        ordersLiveJob = null
        positionsLiveJob = null
        liveAdapter = null
        ensureNoticesSubscription()
        refreshTradingState()
    }

    /**
     * Привязка панели: paper-режим этой DOM-панели персистится отдельно
     * (как у графиков) и восстанавливается при появлении панели.
     */
    fun attachPanel(panelId: String?) {
        panelKey = panelId
        val store = stateStore ?: return
        viewModelScope.launch {
            val enabled = store.getString(paperStoreKey()) == "1"
            if (enabled != _paperEnabled.value) {
                _paperEnabled.value = enabled
                ensureNoticesSubscription()
                refreshTradingState()
            }
        }
    }

    private fun paperStoreKey(): String = "dom_paper_${panelKey ?: "default"}"

    /** Подписка на уведомления активного адаптера (отказы движка). */
    private fun ensureNoticesSubscription() {
        noticesJob?.cancel()
        noticesJob = tradingAdapter()?.notices()?.let { flow ->
            viewModelScope.launch {
                flow.collect { text -> notify(text) }
            }
        }
    }

    /**
     * Ордера и позиции символа панели (для отображения в лесенке) + живые
     * подписки адаптера: отмена/исполнение и позиции обновляют DOM сразу.
     */
    fun refreshTradingState() {
        val adapter = tradingAdapter()
        if (adapter == null) {
            _orders.value = emptyList()
            _positions.value = emptyList()
            return
        }
        ensureTradingLive(adapter)
        val symbol = _domOptions.value.symbol.symbol
        viewModelScope.launch {
            val orders = runCatching { adapter.getOpenOrders(symbol) }.getOrDefault(emptyList())
            _orders.value = orders.filter { it.symbol.equals(symbol, ignoreCase = true) }
            val positions = runCatching { adapter.getPositions() }.getOrDefault(emptyList())
            _positions.value = positions.filter { it.symbol.equals(symbol, ignoreCase = true) }
        }
    }

    private fun ensureTradingLive(adapter: com.aandios.nous.api.market.adapters.TradingAdapter) {
        if (ordersLiveJob?.isActive == true && liveAdapter === adapter) return
        ordersLiveJob?.cancel()
        positionsLiveJob?.cancel()
        liveAdapter = adapter
        ordersLiveJob = adapter.subscribeToOrders()?.let { flow ->
            viewModelScope.launch {
                flow.collect { update ->
                    val symbol = _domOptions.value.symbol.symbol
                    if (!update.symbol.equals(symbol, ignoreCase = true)) return@collect
                    _orders.value = when {
                        update.status != com.aandios.nous.api.market.model.trading.OrderStatus.OPEN ->
                            _orders.value.filterNot { it.orderId == update.orderId }
                        _orders.value.any { it.orderId == update.orderId } ->
                            _orders.value.map { if (it.orderId == update.orderId) update else it }
                        else -> _orders.value + update
                    }
                }
            }
        }
        positionsLiveJob = adapter.subscribeToPositions()?.let { flow ->
            viewModelScope.launch {
                flow.collect { update ->
                    val symbol = _domOptions.value.symbol.symbol
                    if (!update.symbol.equals(symbol, ignoreCase = true)) return@collect
                    _positions.value = when {
                        update.quantity == 0.0 ->
                            _positions.value.filterNot { it.positionId == update.positionId }
                        _positions.value.any { it.positionId == update.positionId } ->
                            _positions.value.map { if (it.positionId == update.positionId) update else it }
                        else -> _positions.value + update
                    }
                }
            }
        }
    }

    /** Отмена ордера из лесенки (крестик на бейдже ордера). */
    fun cancelDomOrder(orderId: String) {
        if (!_isTradingEnabled.value) return
        viewModelScope.launch {
            val adapter = tradingAdapter() ?: return@launch
            val ok = runCatching { adapter.cancelOrder(orderId) }.getOrDefault(false)
            notify(if (ok) "Order canceled" else "Failed to cancel order")
            refreshTradingState()
        }
    }

    /**
     * Правка qty на бейдже ордера в лесенке: cancel+replace (как в chart
     * trading) — старый ордер снимается, новый ставится с той же ценой.
     */
    fun resizeDomOrder(order: com.aandios.nous.api.market.model.trading.Order, quantity: Double) {
        replaceOrder(order, order.price, quantity)
    }

    /** Перемещение ордера драгом в лесенке: cancel+replace по новой цене. */
    fun moveDomOrder(order: com.aandios.nous.api.market.model.trading.Order, newPrice: Double) {
        replaceOrder(order, newPrice, order.quantity)
    }

    /** Общая логика с chart trading: cancel+replace через адаптер. */
    private fun replaceOrder(
        order: com.aandios.nous.api.market.model.trading.Order,
        price: Double,
        quantity: Double,
    ) {
        if (!_isTradingEnabled.value) return
        if (quantity <= 0.0 || price <= 0.0) return
        viewModelScope.launch {
            val adapter = tradingAdapter()
            if (adapter == null) {
                notify("Trading adapter not available")
                return@launch
            }
            val response = adapter.replaceOrder(
                order = order,
                price = price,
                quantity = quantity,
                leverage = _leverage.value,
            )
            notify(
                when {
                    response == null -> "Replace failed (network)"
                    response.success ->
                        "${order.orderType.name} ${order.side.name} $quantity @ $price → ${response.orderId}"
                    else -> response.message ?: "Replace failed"
                }
            )
            refreshTradingState()
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
    private fun executeIntent(intent: OrderIntent) {        // Paper: подстраховка mark-ценой из книги — ордер исполнится даже
        // если стрим последней цены ещё не дошёл (иначе "No price")
        if (_paperEnabled.value) {
            val symbol = intentSymbol(intent)
            val mark = currentMarkFromBestPrices()
            if (symbol != null && mark > 0.0) {
                viewModelScope.launch { PaperTrading.adapter.setMarkPrice(symbol, mark) }
            }
        }
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

    private fun intentSymbol(intent: OrderIntent): String? = when (intent) {
        is OrderIntent.MarketBuy -> intent.symbol
        is OrderIntent.MarketSell -> intent.symbol
        is OrderIntent.LimitBuy -> intent.symbol
        is OrderIntent.LimitSell -> intent.symbol
        is OrderIntent.BestBidBuy -> intent.symbol
        is OrderIntent.BestAskSell -> intent.symbol
        OrderIntent.ToggleTrading -> null
    }

    /** Mark-цена из книги: last, иначе середина bid/ask (paper-подстраховка). */
    private fun currentMarkFromBestPrices(): Double {
        val bid = _bestPrices.value.bestBid ?: 0.0
        val ask = _bestPrices.value.bestAsk ?: 0.0
        return when {
            bid > 0.0 && ask > 0.0 -> (bid + ask) / 2
            bid > 0.0 -> bid
            ask > 0.0 -> ask
            else -> 0.0
        }
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
                // Минимальный qty инструмента подтягивается в поле (если
                // пользователь ещё не правил qty вручную)
                if (!qtyUserEdited) {
                    val minQty = info.minQty.takeIf { it > 0.0 } ?: info.stepSize.takeIf { it > 0.0 }
                    minQty?.let { _orderQuantity.value = fmtQty(it) }
                }
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
