/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.api.market.adapters.SymbolInfoAdapter
import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.core.domain.cache.CandleCacheStore
import com.aandios.nous.core.domain.cache.FootprintCacheStore
import com.aandios.nous.core.domain.repository.ChartRepository
import com.aandios.nous.core.domain.timeseries.TimeSeriesController
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
    private val chartRepository: ChartRepository,
    private val symbolInfoAdapter: SymbolInfoAdapter,
    private val footprintApiClient: FootprintApiClient? = null,
    private val tradesAdapter: TradesAdapter? = null,
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

    private val footprintController = FootprintController(
        footprintApiClient = footprintApiClient,
        tradesAdapter = tradesAdapter,
        footprintCache = footprintCache,
    )
    private val persistor = stateStore?.let { ChartStatePersistor(it) }

    private var candleController: TimeSeriesController<Candle>? = null
    private var candleStateJob: Job? = null
    private var lastCacheWriteMs = 0L

    // Последний снапшот для flush без троттла (смена символа/ТФ, dispose)
    private var lastSnapshot: List<Candle> = emptyList()
    private var lastSnapshotSymbol: String = ""
    private var lastSnapshotTimeframe: String = ""

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
                    )
                }
            }
        }
        loadSymbols()
        loadFootprintSymbols()
    }

    override fun dispose() {
        flushCache()
        candleStateJob?.cancel()
        candleController?.dispose()
        footprintController.dispose()
        viewModelScope.cancel()
    }

    fun dispatch(intent: ChartIntent) {
        when (intent) {
            is ChartIntent.SelectSymbol -> selectSymbol(intent.symbol)
            is ChartIntent.SelectTimeframe -> selectTimeframe(intent.timeframe)
            is ChartIntent.ToggleChartMode -> toggleChartMode()
            is ChartIntent.SelectChartMode -> selectChartMode(intent.mode)
            is ChartIntent.SetFpAggregation -> setFpAggregation(intent.level)
            is ChartIntent.LoadChart -> loadChart(
                ticker = intent.symbol ?: _state.value.currentSymbol,
                timeframe = intent.timeframe ?: _state.value.currentTimeframe,
            )
            is ChartIntent.LoadMoreHistory -> candleController?.loadMore()
            is ChartIntent.LoadMoreFootprintHistory -> footprintController.loadMore()
            is ChartIntent.RestoreState -> restoreState()
        }
    }

    private fun loadSymbols() {
        viewModelScope.launch {
            try {
                val allSymbols = symbolInfoAdapter.getAllSymbolsInfo()
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
            footprintController.start(_state.value.currentSymbol, _state.value.currentTimeframe)
        } else {
            footprintController.stop()
        }
    }

    private fun setFpAggregation(level: AggregationLevel) {
        _state.update { it.copy(fpAggregation = level) }
        saveState()
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
            )
        }
    }

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
            footprintController.start(_state.value.currentSymbol, _state.value.currentTimeframe)
        }
    }

    /**
     * Пересоздаёт контроллер свечей для символа/таймфрейма: история, realtime
     * и пагинация — в одном TimeSeriesController (см. platform-core).
     */
    private fun startCandleSeries(ticker: String, timeframe: String) {
        // Сохраняем данные текущего символа перед переключением (без троттла)
        flushCache()

        candleStateJob?.cancel()
        candleController?.dispose()

        _state.update { it.copy(chartState = ChartState.Loading) }

        // Быстрый показ из кэша, пока грузится свежая история с биржи.
        // Guard внутри update: кэш не может перетереть свежие данные
        viewModelScope.launch {
            val cache = candleCache ?: return@launch
            try {
                val cached = cache.getCandles(EXCHANGE, ticker, timeframe, CACHE_LIMIT)
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
            } catch (_: Exception) {
                // кэш не критичен для работы графика
            }
        }

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
                    )
                }
                scheduleCacheWrite(ticker, timeframe, series.items)
            }
        }

        controller.start()
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
        cacheScope.launch {
            try {
                cache.saveCandles(EXCHANGE, symbol, timeframe, snapshot)
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
        cacheScope.launch {
            try {
                cache.saveCandles(EXCHANGE, symbol, timeframe, snapshot)
            } catch (_: Exception) {
                // кэш не критичен для работы графика
            }
        }
    }

    companion object {
        private const val EXCHANGE = "Binance"
        private const val CACHE_LIMIT = 5500
        private const val CACHE_WRITE_INTERVAL_MS = 30_000L
    }
}
