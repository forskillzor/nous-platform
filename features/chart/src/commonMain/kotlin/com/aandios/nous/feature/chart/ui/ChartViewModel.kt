/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.core.data.repository.ChartRepositoryImpl
import com.aandios.nous.core.data.repository.SymbolInfoRepositoryImpl
import com.aandios.nous.core.domain.cache.CandleCacheStore
import com.aandios.nous.core.domain.cache.FootprintCacheStore
import com.aandios.nous.core.domain.repository.ChartRepository
import com.aandios.nous.core.domain.timeseries.TimeSeriesController
import com.aandios.nous.core.domain.timeseries.Timeframes
import com.aandios.nous.core.currentTimeMillis
import com.aandios.nous.core.Disposable
import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.feature.chart.footprint.FootprintApiClient
import com.aandios.nous.feature.chart.footprint.FootprintController
import com.aandios.nous.feature.dom.domain.model.AggregationLevel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.coroutines.cancellation.CancellationException

class ChartViewModel(
    private val providerRegistry: ProviderRegistry,
    private val footprintApiClient: FootprintApiClient? = null,
    stateStore: StateStore? = null,
    private val candleCache: CandleCacheStore? = null,
    footprintCache: FootprintCacheStore? = null,
    cacheDispatcher: CoroutineDispatcher = Dispatchers.Default,
) : Disposable {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    // Отдельный scope для записей кэша: не блокирует Main и переживает dispose панели
    private val cacheScope = CoroutineScope(cacheDispatcher + SupervisorJob())

    private val _state = MutableStateFlow(ChartUiState())
    val state: StateFlow<ChartUiState> = _state.asStateFlow()

    private val _symbolInfoMap = MutableStateFlow<Map<String, SymbolInfo>>(emptyMap())

    // ── Chart trading (вкл/выкл) ──

    private val _tradingEnabled = MutableStateFlow(false)
    val tradingEnabled: StateFlow<Boolean> = _tradingEnabled.asStateFlow()

    /** Количество для chart-ордеров (null — minQty инструмента). */
    private val _tradingQuantity = MutableStateFlow<Double?>(null)
    val tradingQuantity: StateFlow<Double?> = _tradingQuantity.asStateFlow()

    private val _confirmOrders = MutableStateFlow(false)
    val confirmOrders: StateFlow<Boolean> = _confirmOrders.asStateFlow()

    private val _openOrders = MutableStateFlow<List<Order>>(emptyList())
    val openOrders: StateFlow<List<Order>> = _openOrders.asStateFlow()

    private val _lastTradingMessage = MutableStateFlow<String?>(null)
    val lastTradingMessage: StateFlow<String?> = _lastTradingMessage.asStateFlow()

    private val footprintCacheStore: FootprintCacheStore? = footprintCache
    private var footprintController: FootprintController? = null
    private val persistor = stateStore?.let { ChartStatePersistor(it) }

    private var candleController: TimeSeriesController<Candle>? = null
    private var candleStateJob: Job? = null
    private var lastCacheWriteMs = 0L

    // Последний снапшот для flush без троттла (смена символа/ТФ, dispose)
    private var lastSnapshot: List<Candle> = emptyList()
    private var lastSnapshotSymbol: String = ""
    private var lastSnapshotTimeframe: String = ""

    /** Текущий провайдер данных (по умолчанию — первый из реестра). */
    private var activeProviderId: String = providerRegistry.first()?.providerId.orEmpty()

    init {
        _state.update { it.copy(currentProviderId = activeProviderId) }
        loadSymbols()
        loadFootprintSymbols()
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
        _state.update { it.copy(currentProviderId = providerId, symbols = emptyList()) }
        saveState()
        loadSymbols()
        loadChart(ticker = _state.value.currentSymbol, timeframe = _state.value.currentTimeframe)
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
                        symbols = trading.map { it.symbol }.sorted(),
                        // Set formatter for current symbol
                        currentSymbolFormatter = map[s.currentSymbol]?.let {
                            SymbolFormatter(it.tickSize, it.minQty)
                        } ?: s.currentSymbolFormatter,
                        currentSymbolInfo = map[s.currentSymbol] ?: s.currentSymbolInfo,
                    )
                }
            } catch (e: Exception) {
                println("Failed to load symbols: ${e.message}")
            }
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
        saveTradingState()
        if (enabled) refreshOpenOrders()
        else _openOrders.value = emptyList()
    }

    fun setTradingQuantity(quantity: Double?) {
        _tradingQuantity.value = quantity?.takeIf { it > 0 }
        saveTradingState()
    }

    fun setConfirmOrders(confirm: Boolean) {
        _confirmOrders.value = confirm
        saveTradingState()
    }

    fun clearTradingMessage() {
        _lastTradingMessage.value = null
    }

    /** Перечитать открытые ордера текущего символа (для линий на графике). */
    fun refreshOpenOrders() {
        val symbol = _state.value.currentSymbol
        viewModelScope.launch {
            val adapter = activeProvider()?.trading
            if (adapter == null) {
                _openOrders.value = emptyList()
                return@launch
            }
            val orders = runCatching { adapter.getOpenOrders(symbol) }.getOrDefault(emptyList())
            _openOrders.value = orders.filter { it.symbol.uppercase() == symbol.uppercase() }
        }
    }

    /**
     * Разместить лимитный ордер кликом по графику: ниже текущей цены — BUY,
     * выше — SELL. Количество — [_tradingQuantity] или minQty инструмента.
     */
    fun placeChartOrder(price: Double) {
        viewModelScope.launch {
            val adapter = activeProvider()?.trading
            if (adapter == null) {
                _lastTradingMessage.value = "Trading adapter not available"
                return@launch
            }
            val symbol = _state.value.currentSymbol
            val lastPrice = (chartLastPrice() ?: 0.0)
            val side = if (lastPrice > 0 && price >= lastPrice) OrderSide.SELL else OrderSide.BUY
            val quantity = _tradingQuantity.value
                ?: _state.value.currentSymbolInfo?.minQty?.takeIf { it > 0 }
                ?: 0.001
            val response = runCatching {
                adapter.placeOrder(
                    OrderRequest(
                        symbol = symbol,
                        side = side,
                        orderType = OrderType.LIMIT,
                        quantity = quantity,
                        price = price,
                    )
                )
            }.getOrNull()
            _lastTradingMessage.value = when {
                response == null -> "Order failed (network)"
                response.success -> "Limit ${side.name} $quantity @ $price → ${response.orderId}"
                else -> response.message ?: "Order failed"
            }
            refreshOpenOrders()
        }
    }

    /** Последняя цена для определения стороны chart-ордера. */
    fun chartLastPrice(): Double? = when (val s = _state.value.chartState) {
        is ChartState.Success -> s.currentPrice?.toDouble() ?: s.candles.lastOrNull()?.close?.toDouble()
        else -> null
    }

    private fun saveTradingState() {
        val persistor = persistor ?: return
        viewModelScope.launch {
            persistor.saveTrading(
                enabled = _tradingEnabled.value,
                confirmOrders = _confirmOrders.value,
                quantity = _tradingQuantity.value,
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
            _state.update { s ->
                s.copy(
                    currentSymbol = saved.symbol ?: s.currentSymbol,
                    currentTimeframe = saved.timeframe ?: s.currentTimeframe,
                    chartMode = saved.chartMode ?: s.chartMode,
                    fpAggregation = saved.fpAggregation ?: s.fpAggregation,
                    currentProviderId = activeProviderId,
                )
            }
            val trading = persistor.restoreTrading()
            _tradingEnabled.value = trading.enabled
            _confirmOrders.value = trading.confirmOrders
            _tradingQuantity.value = trading.quantity
            if (_tradingEnabled.value) refreshOpenOrders()
            loadSymbols()
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

        candleStateJob?.cancel()
        candleController?.dispose()

        _state.update { it.copy(chartState = ChartState.Loading) }

        val provider = activeProvider()
        val chartAdapter = provider?.chart
        if (chartAdapter == null) {
            _state.update {
                it.copy(chartState = ChartState.Error("Chart adapter not available for ${exchangeName()}"))
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
                    )
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

            _state.update { s ->
                if (cached.isNotEmpty() &&
                    s.currentSymbol == ticker &&
                    s.currentTimeframe == timeframe &&
                    s.chartState is ChartState.Loading
                ) {
                    s.copy(chartState = ChartState.Success(cached, cached.last().close))
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
