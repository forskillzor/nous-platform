/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.api.market.adapters.SymbolInfoAdapter
import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.core.domain.repository.ChartRepository
import com.aandios.nous.core.domain.timeseries.TimeSeriesController
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
) : Disposable {
    private val viewModelScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _state = MutableStateFlow(ChartUiState())
    val state: StateFlow<ChartUiState> = _state.asStateFlow()

    private val _symbolInfoMap = MutableStateFlow<Map<String, SymbolInfo>>(emptyMap())

    private val footprintController = FootprintController(
        footprintApiClient = footprintApiClient,
        tradesAdapter = tradesAdapter,
    )
    private val persistor = stateStore?.let { ChartStatePersistor(it) }

    private var candleController: TimeSeriesController<Candle>? = null
    private var candleStateJob: Job? = null

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

    override fun dispose() {
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
                    )
                }
            } catch (e: Exception) {
                println("Failed to load symbols: ${e.message}")
            }
        }
    }

    private fun selectSymbol(symbol: String) {
        _state.update { s ->
            s.copy(
                currentSymbol = symbol,
                currentSymbolFormatter = _symbolInfoMap.value[symbol]?.let {
                    SymbolFormatter(it.tickSize, it.minQty)
                } ?: s.currentSymbolFormatter,
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
        val newMode = when (_state.value.chartMode) {
            ChartMode.CANDLESTICK -> ChartMode.FOOTPRINT
            ChartMode.FOOTPRINT -> ChartMode.CANDLESTICK
        }
        _state.update { it.copy(chartMode = newMode) }
        saveState()
        if (newMode == ChartMode.FOOTPRINT) {
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
                historyLoadCount = 0,
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
        candleStateJob?.cancel()
        candleController?.dispose()

        _state.update { it.copy(chartState = ChartState.Loading) }

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
            }
        }

        controller.start()
    }
}
