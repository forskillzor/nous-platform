/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.api.market.adapters.TradingAdapter
import com.aandios.nous.api.market.adapters.replaceOrder
import com.aandios.nous.api.market.paper.PaperTrading
import com.aandios.nous.api.market.paper.effectiveTrading
import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.ContractType
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.api.market.model.trading.OrderStatus
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.core.data.repository.ChartRepositoryImpl
import com.aandios.nous.core.data.repository.SymbolInfoRepositoryImpl
import com.aandios.nous.core.domain.cache.CandleCacheStore
import com.aandios.nous.core.domain.cache.FootprintCacheStore
import com.aandios.nous.core.domain.repository.ChartRepository
import com.aandios.nous.core.domain.timeseries.TimeSeriesController
import com.aandios.nous.core.domain.timeseries.Timeframes
import com.aandios.nous.core.market.MarketOverviewStore
import com.aandios.nous.core.currentTimeMillis
import com.aandios.nous.core.Disposable
import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.core.ui.component.SymbolExplorerRow
import com.aandios.nous.core.ui.component.toSymbolExplorerRow
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.feature.chart.footprint.FootprintApiClient
import com.aandios.nous.feature.chart.footprint.FootprintController
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.cancellation.CancellationException

/** Уведомление chart-трейдинга (snackbar поверх графика под тулбаром). */
data class ChartNotification(val id: Long, val text: String)

class ChartViewModel(
    private val providerRegistry: ProviderRegistry,
    private val footprintApiClient: FootprintApiClient? = null,
    private val stateStore: StateStore? = null,
    private val candleCache: CandleCacheStore? = null,
    footprintCache: FootprintCacheStore? = null,
    private val marketOverviewStore: MarketOverviewStore? = null,
    cacheDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Disposable {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    // Отдельный scope для записей кэша: не блокирует Main и переживает dispose панели
    private val cacheScope = CoroutineScope(cacheDispatcher + SupervisorJob())

    private val _state = MutableStateFlow(ChartUiState())
    val state: StateFlow<ChartUiState> = _state.asStateFlow()

    private val _symbolInfoMap = MutableStateFlow<Map<String, SymbolInfo>>(emptyMap())

    // ── Расширенный список символов (explorer dropdown в тайтле) ──

    /**
     * Строки всех бирж из общего каталога рынка ([MarketOverviewStore]):
     * прелоад при старте приложения, REST-цикл и кэш — внутри стора,
     * панель только маппит их в форму explorer-строки.
     */
    val explorerRows: StateFlow<List<SymbolExplorerRow>> =
        (marketOverviewStore?.state?.map { state -> state.rows.map { it.toSymbolExplorerRow() } }
            ?: flowOf(emptyList()))
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Символ, выбранный в explorer у ДРУГОЙ биржи: применяется после loadSymbols. */
    private var pendingExplorerSymbol: String? = null

    // ── Chart trading (вкл/выкл) ──

    private val _tradingEnabled = MutableStateFlow(false)
    val tradingEnabled: StateFlow<Boolean> = _tradingEnabled.asStateFlow()

    /** Количество для chart-ордеров (null — minQty инструмента). */
    private val _tradingQuantity = MutableStateFlow<Double?>(null)
    val tradingQuantity: StateFlow<Double?> = _tradingQuantity.asStateFlow()

    private val _confirmOrders = MutableStateFlow(false)
    val confirmOrders: StateFlow<Boolean> = _confirmOrders.asStateFlow()

    /** Тип ордера для chart trading (LIMIT/POST_ONLY/IOC/FOK/MARKET). */
    private val _chartOrderType = MutableStateFlow(OrderType.LIMIT)
    val chartOrderType: StateFlow<OrderType> = _chartOrderType.asStateFlow()

    /** Reduce-only для chart-ордеров. */
    private val _reduceOnly = MutableStateFlow(false)
    val reduceOnly: StateFlow<Boolean> = _reduceOnly.asStateFlow()

    /** Плечо для новых ордеров (null — дефолт биржи). */
    private val _chartLeverage = MutableStateFlow<Int?>(null)
    val chartLeverage: StateFlow<Int?> = _chartLeverage.asStateFlow()

    /** Режим маржи для новых ордеров: 1 isolated, 2 cross. */
    private val _chartMarginMode = MutableStateFlow(2)
    val chartMarginMode: StateFlow<Int> = _chartMarginMode.asStateFlow()

    private val _takeProfitPrice = MutableStateFlow<Double?>(null)
    val takeProfitPrice: StateFlow<Double?> = _takeProfitPrice.asStateFlow()

    private val _stopLossPrice = MutableStateFlow<Double?>(null)
    val stopLossPrice: StateFlow<Double?> = _stopLossPrice.asStateFlow()

    /** Ордер, ожидающий подтверждения (Confirm orders: ON). */
    private val _pendingOrder = MutableStateFlow<OrderRequest?>(null)
    val pendingOrder: StateFlow<OrderRequest?> = _pendingOrder.asStateFlow()

    private val _openOrders = MutableStateFlow<List<Order>>(emptyList())
    val openOrders: StateFlow<List<Order>> = _openOrders.asStateFlow()

    /** Открытые позиции текущего символа (линии/бейджи на графике). */
    private val _positions = MutableStateFlow<List<Position>>(emptyList())
    val positions: StateFlow<List<Position>> = _positions.asStateFlow()

    /** Отображение ордеров/позиций на графике (Options dropdown). */
    private val _showOrders = MutableStateFlow(false)
    val showOrders: StateFlow<Boolean> = _showOrders.asStateFlow()

    private val _showPositions = MutableStateFlow(false)
    val showPositions: StateFlow<Boolean> = _showPositions.asStateFlow()

    /** Свёрнута ли панель chart trading (персистится на панель). */
    private val _panelCollapsed = MutableStateFlow(false)
    val panelCollapsed: StateFlow<Boolean> = _panelCollapsed.asStateFlow()

    /**
     * Paper-режим ЭТОЙ панели графика (не глобальный): свой флаг,
     * свой персист, свой источник ордеров/позиций.
     */
    private val _paperEnabled = MutableStateFlow(false)
    val paperEnabled: StateFlow<Boolean> = _paperEnabled.asStateFlow()

    /** Живые обновления ордеров активного адаптера (отмена/исполнение). */
    private var ordersLiveJob: Job? = null
    private var positionsLiveJob: Job? = null
    private var balancesLiveJob: Job? = null

    /** Балансы активного адаптера (paper или live) — маржа/свободно. */
    private val _balances = MutableStateFlow<List<Balance>>(emptyList())
    val balances: StateFlow<List<Balance>> = _balances.asStateFlow()
    private var ordersLiveAdapter: TradingAdapter? = null

    // Уведомления chart-трейдинга: snackbar-стек поверх графика (ChartWindow)
    private val _tradingMessages = MutableStateFlow<List<ChartNotification>>(emptyList())
    val tradingMessages: StateFlow<List<ChartNotification>> = _tradingMessages.asStateFlow()
    private var notificationSeq = 0L

    private val footprintCacheStore: FootprintCacheStore? = footprintCache
    private var footprintController: FootprintController? = null
    private var persistor = stateStore?.let { ChartStatePersistor(it) }

    private var candleController: TimeSeriesController<Candle>? = null
    private var candleStateJob: Job? = null
    private var lastCacheWriteMs = 0L

    // Последний снапшот для flush без троттла (смена символа/ТФ, dispose)
    private var lastSnapshot: List<Candle> = emptyList()
    private var lastSnapshotSymbol: String = ""
    private var lastSnapshotTimeframe: String = ""

    // Candle-серия продолжает жить в footprint-режиме, но её обновления
    // не публикуются в UI; при возврате в CANDLESTICK отдаём последний снапшот.
    private var latestCandleState: ChartState? = null
    private var latestCandleHasMoreHistory: Boolean = true

    /** Текущий провайдер данных (по умолчанию — первый из реестра). */
    private var activeProviderId: String = providerRegistry.first()?.providerId.orEmpty()

    init {
        _state.update { it.copy(currentProviderId = activeProviderId) }
        loadSymbols()
        loadFootprintSymbols()
    }

    /**
     * Привязка панели (workspace+panel): торговые настройки этой панели
     * персистятся отдельно (chart_{workspace}_{panel}_...), затем
     * восстанавливаются. Вызывается из ChartWindow при появлении панели.
     */
    fun attachPanel(workspaceId: String?, panelId: String?) {
        val store = stateStore ?: return
        val prefix = if (workspaceId != null && panelId != null) {
            "chart_${workspaceId}_${panelId}_"
        } else ""
        persistor = ChartStatePersistor(store, prefix)
        viewModelScope.launch { restoreTradingState() }
    }

    override fun dispose() {
        flushCache()
        candleStateJob?.cancel()
        candleController?.dispose()
        footprintController?.dispose()
        viewModelScope.cancel()
    }

    fun dispatch(intent: ChartIntent) {
        when (intent) {
            is ChartIntent.SelectSymbol -> selectSymbol(intent.symbol)
            is ChartIntent.SelectTimeframe -> selectTimeframe(intent.timeframe)
            is ChartIntent.SelectProvider -> selectProvider(intent.providerId)
            is ChartIntent.SelectContractType -> selectContractType(intent.type)
            is ChartIntent.ToggleChartMode -> toggleChartMode()
            is ChartIntent.SelectChartMode -> selectChartMode(intent.mode)
            is ChartIntent.SetFpAggregation -> setFpAggregation(intent.level)
            is ChartIntent.LoadChart -> loadChart(
                ticker = intent.symbol ?: _state.value.currentSymbol,
                timeframe = intent.timeframe ?: _state.value.currentTimeframe,
            )
            is ChartIntent.LoadMoreHistory -> candleController?.loadMore()
            is ChartIntent.LoadMoreFootprintHistory -> footprintController?.loadMore()
            is ChartIntent.RestoreState -> restoreState()
        }
    }

    // ── Провайдер ──

    private fun activeProvider() = providerRegistry.get(activeProviderId)

    /** Пространство имён биржи для кэша — displayName активного провайдера. */
    private fun exchangeName(): String =
        activeProvider()?.config?.displayName ?: activeProviderId

    private fun selectProvider(providerId: String) {
        val provider = providerRegistry.get(providerId) ?: return
        if (activeProviderId == providerId) return
        activeProviderId = providerId
        // Смена биржи = смена цели торговли — Trading выключаем
        setTradingEnabled(false)
        // Подписка на ордера была у старого адаптера — переподпишемся после рефреша
        ordersLiveJob?.cancel()
        ordersLiveJob = null
        ordersLiveAdapter = null
        _state.update { it.copy(currentProviderId = providerId, symbols = emptyList()) }
        saveState()
        loadSymbols()
        // Выбор из explorer: символ применится после загрузки списка (тут
        // промежуточная загрузка старого символа на новой бирже не нужна)
        if (pendingExplorerSymbol == null) {
            loadChart(ticker = _state.value.currentSymbol, timeframe = _state.value.currentTimeframe)
        }
        refreshOpenOrders()
    }

    /** USDT-M / COIN-M: фильтр списка символов + fallback текущего символа. */
    private fun selectContractType(type: ContractType) {
        if (_state.value.contractType == type) return
        _state.update { it.copy(contractType = type) }
        saveState()
        applyContractTypeFilter()
        ensureSymbolMatchesContractType()
    }

    /** Символы провайдера выбранного типа контракта (для dropdown). */
    private fun applyContractTypeFilter() {
        val type = _state.value.contractType
        val filtered = _symbolInfoMap.value.values
            .filter { type.matches(it) }
            .map { it.symbol }
            .sorted()
        _state.update { it.copy(symbols = filtered) }
    }

    /** Если текущий символ не того типа — выбрать первый подходящий. */
    private fun ensureSymbolMatchesContractType() {
        val s = _state.value
        val info = s.currentSymbolInfo ?: _symbolInfoMap.value[s.currentSymbol] ?: return
        if (s.contractType.matches(info)) return
        s.symbols.firstOrNull()?.let { selectSymbol(it) }
    }

    private fun loadSymbols() {
        val symbolInfoAdapter = activeProvider()?.symbolInfo ?: return
        viewModelScope.launch {
            try {
                val allSymbols = SymbolInfoRepositoryImpl(symbolInfoAdapter).getAllSymbolsInfo()
                val trading = allSymbols.filter { it.status == "TRADING" }
                val map = trading.associateBy { it.symbol }
                _symbolInfoMap.value = map
                _state.update { s ->
                    s.copy(
                        symbols = trading
                            .filter { s.contractType.matches(it) }
                            .map { it.symbol }
                            .sorted(),
                        // Set formatter for current symbol
                        currentSymbolFormatter = map[s.currentSymbol]?.let {
                            SymbolFormatter(it.tickSize, it.minQty)
                        } ?: s.currentSymbolFormatter,
                        currentSymbolInfo = map[s.currentSymbol] ?: s.currentSymbolInfo,
                    )
                }
                ensurePaperCompatibility()
                applyPendingExplorerSymbol()
            } catch (e: Exception) {
                println("Failed to load symbols: ${e.message}")
                // Список не загрузился — не оставляем chart пустым после
                // переключения биржи из explorer
                if (pendingExplorerSymbol != null) {
                    pendingExplorerSymbol = null
                    loadChart(ticker = _state.value.currentSymbol, timeframe = _state.value.currentTimeframe)
                }
            }
        }
    }

    /** Paper-движок линейный: для COIN-M (inverse) paper выключаем. */
    private fun ensurePaperCompatibility() {
        if (_paperEnabled.value && _state.value.currentSymbolInfo?.isInverse == true) {
            setPaperEnabled(false)
            notify("Paper trading недоступен для COIN-M — выключен")
        }
    }

    private fun selectSymbol(symbol: String) {
        _state.update { s ->
            val info = _symbolInfoMap.value[symbol]
            s.copy(
                currentSymbol = symbol,
                currentSymbolFormatter = info?.let {
                    SymbolFormatter(it.tickSize, it.minQty)
                } ?: s.currentSymbolFormatter,
                currentSymbolInfo = info ?: s.currentSymbolInfo,
            )
        }
        saveState()
        loadChart(ticker = symbol, timeframe = _state.value.currentTimeframe)
    }

    // ── Explorer (расширенный список символов) ──

    /**
     * Выбор строки из explorer: биржа (смена = Trading OFF, инвариант
     * безопасности), тип контракта по SymbolInfo и символ графика.
     */
    fun selectExplorerRow(row: SymbolExplorerRow) {
        if (row.exchangeId != activeProviderId) {
            pendingExplorerSymbol = row.symbol
            selectProvider(row.exchangeId)
            return
        }
        val info = _symbolInfoMap.value[row.symbol]
        if (info != null) {
            val type = ContractType.of(info)
            if (_state.value.contractType != type) {
                _state.update { it.copy(contractType = type) }
                saveState()
                applyContractTypeFilter()
            }
        }
        selectSymbol(row.symbol)
    }

    /** Применить отложенный выбор из explorer после загрузки символов. */
    private fun applyPendingExplorerSymbol() {
        val symbol = pendingExplorerSymbol ?: return
        pendingExplorerSymbol = null
        val info = _symbolInfoMap.value[symbol]
        if (info == null) {
            // Символа нет на новой бирже — открываем первый доступный
            _state.value.symbols.firstOrNull()?.let { selectSymbol(it) }
                ?: loadChart(ticker = _state.value.currentSymbol, timeframe = _state.value.currentTimeframe)
            return
        }
        val type = ContractType.of(info)
        if (_state.value.contractType != type) {
            _state.update { it.copy(contractType = type) }
            applyContractTypeFilter()
        }
        selectSymbol(symbol)
    }

    private fun selectTimeframe(timeframe: String) {
        _state.update { it.copy(currentTimeframe = timeframe) }
        saveState()
        loadChart(ticker = _state.value.currentSymbol, timeframe = timeframe)
    }

    private fun toggleChartMode() {
        selectChartMode(
            when (_state.value.chartMode) {
                ChartMode.CANDLESTICK -> ChartMode.FOOTPRINT
                ChartMode.FOOTPRINT -> ChartMode.CANDLESTICK
            }
        )
    }

    private fun selectChartMode(mode: ChartMode) {
        if (_state.value.chartMode == mode) return
        _state.update { it.copy(chartMode = mode) }
        saveState()
        if (mode == ChartMode.FOOTPRINT) {
            startFootprint(_state.value.currentSymbol, _state.value.currentTimeframe)
        } else {
            footprintController?.stop()
            latestCandleState?.let { latest ->
                _state.update {
                    it.copy(
                        chartState = latest,
                        hasMoreHistory = latestCandleHasMoreHistory,
                    )
                }
            }
        }
    }

    private fun setFpAggregation(level: AggregationLevel) {
        _state.update { it.copy(fpAggregation = level) }
        saveState()
    }

    // ── Chart trading ──

    fun setTradingEnabled(enabled: Boolean) {
        if (_tradingEnabled.value == enabled) return
        _tradingEnabled.value = enabled
        // Trading ON автоматически включает показ ордеров и позиций
        if (enabled) {
            _showOrders.value = true
            _showPositions.value = true
        }
        saveTradingState()
        // Trading off — только запрет размещения: открытые ордера остаются
        // видны на графике (просмотр), live-подписка продолжает работать
        if (enabled) refreshOpenOrders() else _pendingOrder.value = null
    }

    fun setTradingQuantity(quantity: Double?) {
        _tradingQuantity.value = quantity?.takeIf { it > 0 }
        saveTradingState()
    }

    fun setConfirmOrders(confirm: Boolean) {
        _confirmOrders.value = confirm
        saveTradingState()
    }

    /** Options: показывать ордера на графике. */
    fun setShowOrders(show: Boolean) {
        _showOrders.value = show
        saveTradingState()
    }

    /** Options: показывать позиции на графике. */
    fun setShowPositions(show: Boolean) {
        _showPositions.value = show
        saveTradingState()
    }

    /** Свернуть/развернуть панель chart trading (компактный вид). */
    fun setPanelCollapsed(collapsed: Boolean) {
        if (_panelCollapsed.value == collapsed) return
        _panelCollapsed.value = collapsed
        saveTradingState()
    }

    fun setChartOrderType(orderType: OrderType) {
        _chartOrderType.value = orderType
        saveTradingState()
    }

    fun setReduceOnly(reduceOnly: Boolean) {
        _reduceOnly.value = reduceOnly
        saveTradingState()
    }

    fun setChartLeverage(leverage: Int?) {
        _chartLeverage.value = leverage?.takeIf { it > 0 }
        saveTradingState()
    }

    fun setChartMarginMode(mode: Int) {
        if (mode != 1 && mode != 2) return
        _chartMarginMode.value = mode
        saveTradingState()
    }

    fun setTakeProfitPrice(price: Double?) {
        _takeProfitPrice.value = price?.takeIf { it > 0 }
    }

    fun setStopLossPrice(price: Double?) {
        _stopLossPrice.value = price?.takeIf { it > 0 }
    }

    /** Подтвердить отложенный ордер (Confirm orders: ON). */
    fun confirmPendingOrder() {
        if (!_tradingEnabled.value) {
            _pendingOrder.value = null
            return
        }
        val request = _pendingOrder.value ?: return
        _pendingOrder.value = null
        executeOrder(request)
    }

    fun cancelPendingOrder() {
        _pendingOrder.value = null
    }

    fun clearTradingMessage() {
        _tradingMessages.value = emptyList()
    }

    /** Показать уведомление (snackbar под тулбаром графика). */
    private fun notify(text: String) {
        val id = ++notificationSeq
        _tradingMessages.value = (_tradingMessages.value + ChartNotification(id, text)).takeLast(5)
    }

    /** Закрыть уведомление крестиком (или по таймауту в UI). */
    fun dismissTradingMessage(id: Long) {
        _tradingMessages.value = _tradingMessages.value.filterNot { it.id == id }
    }

    /**
     * Paper-режим ЭТОЙ панели: локальный флаг + персист (panel-scoped) +
     * переподписка на ордера/позиции другого адаптера. Глобальных флагов нет —
     * в одном workspace можно видеть paper и live одновременно.
     */
    fun setPaperEnabled(enabled: Boolean) {
        if (enabled && _state.value.currentSymbolInfo?.isInverse == true) {
            notify("Paper trading недоступен для COIN-M")
            return
        }
        if (_paperEnabled.value == enabled) return
        _paperEnabled.value = enabled
        viewModelScope.launch { persistor?.savePaperEnabled(enabled) }
        // Смена paper/live — Trading выключаем: включение только осознанно
        // (флаги показа ордеров/позиций не трогаем — как выбрано, так и живёт)
        setTradingEnabled(false)
        ordersLiveJob?.cancel()
        positionsLiveJob?.cancel()
        balancesLiveJob?.cancel()
        ordersLiveJob = null
        positionsLiveJob = null
        balancesLiveJob = null
        ordersLiveAdapter = null
        refreshOpenOrders()
        refreshPaperFees(_state.value.currentSymbol)
    }

    /** Торговый адаптер этой панели: paper или реальный (по флагу панели). */
    private fun activeTrading() = activeProvider()?.effectiveTrading(_paperEnabled.value)

    /** Восстановление торговых настроек панели (после attachPanel). */
    private suspend fun restoreTradingState() {
        val p = persistor ?: return
        val trading = p.restoreTrading()
        val paperEnabled = p.restorePaperEnabled()
        _paperEnabled.value = paperEnabled
        // Live-Trading не восстанавливаем: только в paper-режиме
        _tradingEnabled.value = trading.enabled && paperEnabled
        _confirmOrders.value = trading.confirmOrders
        _tradingQuantity.value = trading.quantity
        _chartOrderType.value = trading.orderType
        _reduceOnly.value = trading.reduceOnly
        _chartLeverage.value = trading.leverage
        _chartMarginMode.value = trading.marginMode
        // Показ ордеров/позиций — как выбрано пользователем, не трогаем
        _showOrders.value = trading.showOrders
        _showPositions.value = trading.showPositions
        _panelCollapsed.value = trading.panelCollapsed
        refreshOpenOrders()
        refreshPaperFees(_state.value.currentSymbol)
    }

    /**
     * Paper: ставки комиссий символа из данных активной биржи (метод
     * провайдер-агностик — каждый адаптер отдаёт свои ставки; null → 0%).
     */
    private fun refreshPaperFees(symbol: String) {
        if (!_paperEnabled.value) return
        val trading = activeProvider()?.trading ?: return
        viewModelScope.launch {
            val rates = runCatching { trading.getFeeRates(symbol) }.getOrNull()
            if (_paperEnabled.value) PaperTrading.adapter.setFeeRates(symbol, rates)
        }
    }

    /** Перечитать открытые ордера текущего символа (для линий на графике). */
    fun refreshOpenOrders() {
        val adapter = activeTrading()
            ?: run {
                _openOrders.value = emptyList()
                return
            }
        ensureOrdersLive(adapter)
        val symbol = _state.value.currentSymbol
        viewModelScope.launch {
            val orders = runCatching { adapter.getOpenOrders(symbol) }.getOrDefault(emptyList())
            _openOrders.value = orders.filter { it.symbol.uppercase() == symbol.uppercase() }
            val positions = runCatching { adapter.getPositions() }.getOrDefault(emptyList())
            _positions.value = positions.filter { it.symbol.equals(symbol, ignoreCase = true) }
            // Балансы активного адаптера — для строки маржи под панелью
            runCatching { adapter.getBalances() }.onSuccess { _balances.value = it }
        }
    }

    /**
     * Живые обновления активного адаптера: ордера (отмена/исполнение) и
     * позиции — сразу обновляют линии/бейджи на графике.
     */
    private fun ensureOrdersLive(adapter: TradingAdapter) {
        if (ordersLiveJob?.isActive == true && ordersLiveAdapter === adapter) return
        ordersLiveJob?.cancel()
        positionsLiveJob?.cancel()
        balancesLiveJob?.cancel()
        ordersLiveAdapter = adapter
        ordersLiveJob = adapter.subscribeToOrders()?.let { flow ->
            viewModelScope.launch {
                flow.collect { update -> onOrderUpdate(update) }
            }
        }
        positionsLiveJob = adapter.subscribeToPositions()?.let { flow ->
            viewModelScope.launch {
                flow.collect { update -> onPositionUpdate(update) }
            }
        }
        balancesLiveJob = adapter.subscribeToBalances()?.let { flow ->
            viewModelScope.launch {
                flow.collect { update -> onBalanceUpdate(update) }
            }
        }
        // Уведомления движка (причины отказов ордеров) — в snackbar
        adapter.notices()?.let { flow ->
            viewModelScope.launch {
                flow.collect { text -> notify(text) }
            }
        }
    }

    private fun onBalanceUpdate(update: Balance) {
        _balances.value = if (_balances.value.any { it.currency.equals(update.currency, true) }) {
            _balances.value.map { if (it.currency.equals(update.currency, true)) update else it }
        } else {
            _balances.value + update
        }
    }

    private fun onPositionUpdate(update: Position) {
        val symbol = _state.value.currentSymbol
        if (!update.symbol.equals(symbol, ignoreCase = true)) return
        _positions.value = if (update.quantity == 0.0) {
            _positions.value.filterNot { it.positionId == update.positionId }
        } else if (_positions.value.any { it.positionId == update.positionId }) {
            _positions.value.map { if (it.positionId == update.positionId) update else it }
        } else {
            _positions.value + update
        }
    }

    private fun onOrderUpdate(update: Order) {
        val symbol = _state.value.currentSymbol
        if (!update.symbol.equals(symbol, ignoreCase = true)) return
        _openOrders.value = if (update.status != OrderStatus.OPEN) {
            _openOrders.value.filterNot { it.orderId == update.orderId }
        } else if (_openOrders.value.any { it.orderId == update.orderId }) {
            _openOrders.value.map { if (it.orderId == update.orderId) update else it }
        } else {
            _openOrders.value + update
        }
    }

    /**
     * Разместить ордер кликом по графику: ниже текущей цены — BUY, выше — SELL.
     * Тип — выбранный в панели (для MARKET цена клика игнорируется),
     * количество — [_tradingQuantity] или minQty инструмента.
     */
    fun placeChartOrder(price: Double) {
        val lastPrice = chartLastPrice() ?: 0.0
        val side = if (lastPrice > 0 && price >= lastPrice) OrderSide.SELL else OrderSide.BUY
        val type = _chartOrderType.value
        submitOrConfirm(buildTradingRequest(side, type, if (type == OrderType.MARKET) 0.0 else price))
    }

    /** Рыночный ордер по кнопке Buy/Sell (последняя цена графика). */
    fun placeMarketOrder(side: OrderSide) {
        submitOrConfirm(buildTradingRequest(side, OrderType.MARKET, 0.0))
    }

    /** Лимитка по лучшей цене (bid для BUY, ask для SELL). */
    fun placeLimitAtBest(side: OrderSide, price: Double) {
        if (price <= 0) return
        submitOrConfirm(buildTradingRequest(side, OrderType.LIMIT, price))
    }

    /** Отменить все открытые ордера по символу графика. */
    fun cancelAllChartOrders() {
        if (!_tradingEnabled.value) return
        viewModelScope.launch {
            val adapter = activeTrading()
            if (adapter == null) {
                notify("Trading adapter not available")
                return@launch
            }
            val symbol = _state.value.currentSymbol
            val ok = runCatching { adapter.cancelAllOrders(symbol) }.getOrDefault(false)
            notify(if (ok) "All orders canceled ($symbol)" else "Failed to cancel orders")
            refreshOpenOrders()
        }
    }

    /** Закрыть одну позицию по рынку (✕ на бейдже позиции). */
    fun closeChartPosition(position: Position) {
        if (!_tradingEnabled.value) return
        viewModelScope.launch {
            val adapter = activeTrading()
            if (adapter == null) {
                notify("Trading adapter not available")
                return@launch
            }
            val result = runCatching {
                adapter.closePosition(position.symbol, position.positionId, position.quantity)
            }.getOrNull()
            val qty = _state.value.currentSymbolFormatter.formatVolume(position.quantity)
            notify(
                when {
                    result == null -> "Failed to close ${position.symbol}"
                    result.success -> "Closed ${position.symbol} $qty → ${result.orderId}"
                    else -> "Close failed: ${result.message}"
                }
            )
            refreshOpenOrders()
        }
    }

    /** Закрыть все позиции по символу графика (exchange+symbol). */
    fun closeAllChartPositions() {
        if (!_tradingEnabled.value) return
        viewModelScope.launch {
            val adapter = activeTrading()
            if (adapter == null) {
                notify("Trading adapter not available")
                return@launch
            }
            val symbol = _state.value.currentSymbol
            val positions = runCatching { adapter.getPositions() }.getOrDefault(emptyList())
                .filter { it.symbol.equals(symbol, ignoreCase = true) }
            if (positions.isEmpty()) {
                notify("No open positions ($symbol)")
                return@launch
            }
            var closed = 0
            positions.forEach { p ->
                val r = runCatching { adapter.closePosition(p.symbol, p.positionId, p.quantity) }.getOrNull()
                if (r?.success == true) closed++
            }
            notify("Closed $closed/${positions.size} positions ($symbol)")
            refreshOpenOrders()
        }
    }

    private fun buildTradingRequest(side: OrderSide, orderType: OrderType, price: Double): OrderRequest {
        val quantity = _tradingQuantity.value
            ?: _state.value.currentSymbolInfo?.minQty?.takeIf { it > 0 }
            ?: 0.001
        val formatter = _state.value.currentSymbolFormatter
        return OrderRequest(
            symbol = _state.value.currentSymbol,
            side = side,
            orderType = orderType,
            quantity = quantity,
            price = if (orderType == OrderType.MARKET) 0.0 else formatter.roundPrice(price),
            reduceOnly = _reduceOnly.value,
            leverage = _chartLeverage.value,
            marginMode = _chartMarginMode.value,
            stopLossPrice = _stopLossPrice.value?.let { formatter.roundPrice(it) },
            takeProfitPrice = _takeProfitPrice.value?.let { formatter.roundPrice(it) },
        )
    }

    private fun submitOrConfirm(request: OrderRequest) {
        // Trading выключен на графике — ордера не размещаются вообще
        if (!_tradingEnabled.value) {
            notify("Trading disabled")
            return
        }
        if (_confirmOrders.value) {
            _pendingOrder.value = request
        } else {
            executeOrder(request)
        }
    }

    private fun executeOrder(request: OrderRequest) {
        viewModelScope.launch {
            val adapter = activeTrading()
            if (adapter == null) {
                notify("Trading adapter not available")
                return@launch
            }
            val response = runCatching { adapter.placeOrder(request) }.getOrNull()
            notify(when {
                response == null -> "Order failed (network)"
                response.success -> {
                    val px = if (request.orderType == OrderType.MARKET) "market"
                    else "@ ${_state.value.currentSymbolFormatter.formatPrice(request.price)}"
                    "${request.orderType.name} ${request.side.name} ${request.quantity} $px → ${response.orderId}"
                }
                else -> response.message ?: "Order failed"
            })
            refreshOpenOrders()
        }
    }

    /** Отменить ордер с графика (✕ на бейдже). */
    fun cancelChartOrder(orderId: String) {
        if (!_tradingEnabled.value) return
        viewModelScope.launch {
            val adapter = activeTrading()
            if (adapter == null) {
                notify("Trading adapter not available")
                return@launch
            }
            val ok = runCatching { adapter.cancelOrder(orderId) }.getOrDefault(false)
            notify(if (ok) "Order $orderId canceled" else "Failed to cancel $orderId")
            refreshOpenOrders()
        }
    }

    /**
     * Перетаскивание ордера: старый отменяется, новый размещается по новой
     * цене (как в MEXC/TradingView — ордер «переезжает»).
     */
    fun moveChartOrder(order: Order, newPrice: Double) {
        if (!_tradingEnabled.value) return
        replaceChartOrder(order, newPrice, order.quantity)
    }

    /** Правка qty на бейдже: cancel+replace с новым количеством. */
    fun resizeChartOrder(order: Order, newQuantity: Double) {
        if (!_tradingEnabled.value) return
        replaceChartOrder(order, order.price, newQuantity)
    }

    private fun replaceChartOrder(order: Order, price: Double, quantity: Double) {
        viewModelScope.launch {
            val adapter = activeTrading()
            if (adapter == null) {
                notify("Trading adapter not available")
                return@launch
            }
            if (quantity <= 0) {
                notify("Quantity must be positive")
                return@launch
            }
            // Общая логика с DOM: cancel+replace
            val formatter = _state.value.currentSymbolFormatter
            val newPrice = if (order.orderType == OrderType.MARKET) 0.0 else formatter.roundPrice(price)
            val response = adapter.replaceOrder(
                order = order,
                price = newPrice,
                quantity = quantity,
                leverage = _chartLeverage.value,
                marginMode = _chartMarginMode.value,
            )
            notify(when {
                response == null -> "Replace failed (network)"
                response.success -> {
                    val px = if (order.orderType == OrderType.MARKET) "market"
                    else "@ ${formatter.formatPrice(newPrice)}"
                    "${order.orderType.name} ${order.side.name} $quantity $px → ${response.orderId}"
                }
                else -> response.message ?: "Replace failed"
            })
            refreshOpenOrders()
        }
    }

    /** Последняя цена для определения стороны chart-ордера. */
    fun chartLastPrice(): Double? {
        val s = _state.value
        if (s.chartMode == ChartMode.FOOTPRINT) {
            return s.footprintCurrentPrice?.toDouble()
                ?: s.liveFootprintCandle?.close?.takeIf { it > 0f }?.toDouble()
        }
        return when (val chart = s.chartState) {
            is ChartState.Success -> chart.currentPrice?.toDouble() ?: chart.candles.lastOrNull()?.close?.toDouble()
            else -> null
        }
    }

    private fun saveTradingState() {
        val persistor = persistor ?: return
        viewModelScope.launch {
            persistor.saveTrading(
                enabled = _tradingEnabled.value,
                confirmOrders = _confirmOrders.value,
                quantity = _tradingQuantity.value,
                orderType = _chartOrderType.value,
                reduceOnly = _reduceOnly.value,
                leverage = _chartLeverage.value,
                marginMode = _chartMarginMode.value,
                showOrders = _showOrders.value,
                showPositions = _showPositions.value,
                panelCollapsed = _panelCollapsed.value,
            )
        }
    }

    private fun saveState() {
        val persistor = persistor ?: return
        val current = _state.value
        viewModelScope.launch {
            persistor.save(
                symbol = current.currentSymbol,
                timeframe = current.currentTimeframe,
                chartMode = current.chartMode,
                fpAggregation = current.fpAggregation,
                providerId = activeProviderId,
                contractType = current.contractType,
            )
        }
    }

    private fun restoreState() {
        val persistor = persistor ?: return
        viewModelScope.launch {
            val saved = persistor.restore()
            // Провайдер применяем только если он реально зарегистрирован
            saved.providerId?.let { savedId ->
                if (providerRegistry.get(savedId) != null) activeProviderId = savedId
            }
            saved.contractType?.let { savedType ->
                if (_state.value.contractType != savedType) {
                    _state.update { it.copy(contractType = savedType) }
                }
            }
            _state.update { s ->
                s.copy(
                    currentSymbol = saved.symbol ?: s.currentSymbol,
                    currentTimeframe = saved.timeframe ?: s.currentTimeframe,
                    chartMode = saved.chartMode ?: s.chartMode,
                    fpAggregation = saved.fpAggregation ?: s.fpAggregation,
                    currentProviderId = activeProviderId,
                )
            }
            // Торговые настройки панели (включая paper/показ) — panel-scoped
            restoreTradingState()
            loadSymbols()
        }
    }

    /**
     * Восстановить только биржу (для workspace-панелей: символ/ТФ задаёт
     * PanelConfig, а провайдер живёт в персисте графика).
     */
    fun restoreProvider(preferredProviderId: String? = null) {
        val preferredId = preferredProviderId?.takeIf { providerRegistry.get(it) != null }
        val persistor = persistor ?: return
        viewModelScope.launch {
            val saved = persistor.restore()
            // Тип контрактов — тоже часть персиста графика
            saved.contractType?.let { savedType ->
                if (_state.value.contractType != savedType) {
                    _state.update { it.copy(contractType = savedType) }
                }
            }
            val targetId = preferredId
                ?: saved.providerId?.takeIf { providerRegistry.get(it) != null }
                ?: return@launch
            if (activeProviderId == targetId) return@launch
            selectProvider(targetId)
        }
    }

    private fun loadFootprintSymbols() {
        val api = footprintApiClient ?: return
        viewModelScope.launch {
            try {
                val instruments = api.getInstruments()
                _state.update { it.copy(symbolsWithFootprint = instruments.map { i -> i.symbol }.toSet()) }
            } catch (e: Exception) {
                println("Failed to load footprint symbols: ${e.message}")
            }
        }
    }

    private fun loadChart(ticker: String, timeframe: String) {
        _state.update {
            it.copy(
                currentSymbol = ticker,
                currentTimeframe = timeframe,
                hasMoreHistory = true,
                currentSymbolInfo = _symbolInfoMap.value[ticker] ?: it.currentSymbolInfo,
            )
        }
        startCandleSeries(ticker, timeframe)

        if (_state.value.chartMode == ChartMode.FOOTPRINT) {
            startFootprint(ticker, timeframe)
        }
    }

    /**
     * Пересоздаёт контроллер свечей для символа/таймфрейма/провайдера:
     * история, realtime и пагинация — в одном TimeSeriesController (см. platform-core).
     */
    private fun startCandleSeries(ticker: String, timeframe: String) {
        // Сохраняем данные текущего символа перед переключением (без троттла)
        flushCache()
        refreshPaperFees(ticker)

        candleStateJob?.cancel()
        candleController?.dispose()

        latestCandleState = ChartState.Loading
        latestCandleHasMoreHistory = true
        if (_state.value.chartMode != ChartMode.FOOTPRINT) {
            _state.update { it.copy(chartState = ChartState.Loading) }
        }

        val provider = activeProvider()
        val chartAdapter = provider?.chart
        if (chartAdapter == null) {
            val errorState = ChartState.Error("Chart adapter not available for ${exchangeName()}")
            latestCandleState = errorState
            latestCandleHasMoreHistory = false
            if (_state.value.chartMode != ChartMode.FOOTPRINT) {
                _state.update { it.copy(chartState = errorState) }
            }
            return
        }

        val repository: ChartRepository = ChartRepositoryImpl(chartAdapter = chartAdapter)
        val controller = TimeSeriesController(
            source = repository.candleSource(ticker, timeframe),
            scope = viewModelScope,
        )
        candleController = controller

        candleStateJob = viewModelScope.launch {
            controller.state.collect { series ->
                if (_state.value.currentSymbol != ticker || _state.value.currentTimeframe != timeframe) {
                    return@collect
                }
                val error = series.error
                val chartState = when {
                    error != null && series.items.isEmpty() -> ChartState.Error(error)
                    series.items.isNotEmpty() -> ChartState.Success(series.items, series.items.last().close)
                    series.loading -> ChartState.Loading
                    else -> _state.value.chartState
                }
                latestCandleState = chartState
                latestCandleHasMoreHistory = series.hasMore
                _state.update { s ->
                    if (s.chartMode == ChartMode.FOOTPRINT) {
                        s
                    } else {
                        s.copy(
                            chartState = chartState,
                            hasMoreHistory = series.hasMore,
                        )
                    }
                }
                // Paper engine: mark-цена из последней свечи (исполнение бумажных ордеров).
                // В footprint-режиме mark-цену ведёт footprint-контроллер.
                if (_state.value.chartMode != ChartMode.FOOTPRINT && series.items.isNotEmpty()) {
                    PaperTrading.adapter.feedMarkPrice(ticker, series.items.last().close.toDouble())
                }
                scheduleCacheWrite(ticker, timeframe, series.items)
            }
        }

        // Быстрый показ из кэша; если кэш свежий (последняя закрытая свеча —
        // текущий таймфрейм) — пропускаем REST loadInitial и стартуем сразу
        // с live-стрима: меньше запросов при открытии workspace с N графиками.
        viewModelScope.launch {
            val cache = candleCache
            val exchange = exchangeName()
            val cached = if (cache != null) {
                runCatching { cache.getCandles(exchange, ticker, timeframe, CACHE_LIMIT) }
                    .getOrDefault(emptyList())
            } else {
                emptyList()
            }

            val targetMatches = _state.value.currentSymbol == ticker &&
                _state.value.currentTimeframe == timeframe
            val cachedState = if (targetMatches && cached.isNotEmpty() && latestCandleState is ChartState.Loading) {
                ChartState.Success(cached, cached.last().close)
            } else {
                null
            }
            if (cachedState != null) latestCandleState = cachedState
            _state.update { s ->
                if (cachedState == null || s.currentSymbol != ticker || s.currentTimeframe != timeframe) {
                    s
                } else if (s.chartMode == ChartMode.FOOTPRINT) {
                    s
                } else if (s.chartState is ChartState.Loading) {
                    s.copy(chartState = cachedState)
                } else {
                    s
                }
            }

            val timeframeMs = Timeframes.millis(timeframe)
            val now = currentTimeMillis()
            val freshCache = cached.isNotEmpty() &&
                    cached.last().timestamp + timeframeMs > now - timeframeMs

            controller.start(skipInitialLoad = freshCache)
        }
    }

    /** Создаёт/перезапускает footprint-контроллер для активного провайдера. */
    private fun startFootprint(symbol: String, timeframe: String) {
        footprintController?.dispose()
        val controller = FootprintController(
            footprintApiClient = footprintApiClient,
            tradesAdapter = activeProvider()?.trades,
            footprintCache = footprintCacheStore,
            exchange = exchangeName(),
        )
        footprintController = controller
        viewModelScope.launch {
            controller.state.collect { fp ->
                _state.update {
                    it.copy(
                        footprintCandles = fp.candles,
                        liveFootprintCandle = fp.liveCandle,
                        footprintCurrentPrice = fp.currentPrice,
                        footprintLoading = fp.loading,
                        footprintError = fp.error,
                        hasMoreFootprintHistory = fp.hasMoreHistory,
                    )
                }
                // Paper engine: mark-цена footprint-режима
                fp.currentPrice?.let { PaperTrading.adapter.feedMarkPrice(symbol, it.toDouble()) }
            }
        }
        controller.start(symbol, timeframe)
    }

    /** Throttled-запись свечей в кэш (не чаще раза в 30 секунд). */
    private fun scheduleCacheWrite(symbol: String, timeframe: String, candles: List<Candle>) {
        val cache = candleCache ?: return
        if (candles.isEmpty()) return
        val now = currentTimeMillis()
        if (now - lastCacheWriteMs < CACHE_WRITE_INTERVAL_MS) return
        lastCacheWriteMs = now
        val snapshot = candles.takeLast(CACHE_LIMIT)
        lastSnapshot = snapshot
        lastSnapshotSymbol = symbol
        lastSnapshotTimeframe = timeframe
        val exchange = exchangeName()
        cacheScope.launch {
            try {
                cache.saveCandles(exchange, symbol, timeframe, snapshot)
            } catch (_: Exception) {
                // кэш не критичен для работы графика
            }
        }
    }

    /** Немедленная запись последнего снапшота (без троттла): смена символа/ТФ или dispose. */
    private fun flushCache() {
        val cache = candleCache ?: return
        if (lastSnapshot.isEmpty()) return
        val snapshot = lastSnapshot
        val symbol = lastSnapshotSymbol
        val timeframe = lastSnapshotTimeframe
        val exchange = exchangeName()
        cacheScope.launch {
            try {
                cache.saveCandles(exchange, symbol, timeframe, snapshot)
            } catch (_: Exception) {
                // кэш не критичен для работы графика
            }
        }
    }

    companion object {
        private const val CACHE_LIMIT = 5500
        private const val CACHE_WRITE_INTERVAL_MS = 30_000L
    }
}
