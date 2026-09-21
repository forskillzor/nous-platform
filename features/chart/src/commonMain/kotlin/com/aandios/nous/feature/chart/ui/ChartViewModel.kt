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
    private var currentJob: Job? = null
    private var isLoadingMore = false

    /** Старые свечи, подгруженные вручную; мержатся с realtime-эмиссиями репозитория. */
    private var historyPrefix: List<Candle> = emptyList()

    private val _state = MutableStateFlow(ChartUiState())
    val state: StateFlow<ChartUiState> = _state.asStateFlow()

    private val _symbolInfoMap = MutableStateFlow<Map<String, SymbolInfo>>(emptyMap())

    private val footprintController = FootprintController(
        footprintApiClient = footprintApiClient,
        tradesAdapter = tradesAdapter,
    )
    private val persistor = stateStore?.let { ChartStatePersistor(it) }

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
        currentJob?.cancel()
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
            is ChartIntent.LoadMoreHistory -> loadMoreHistory()
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
        isLoadingMore = false
        historyPrefix = emptyList()

        currentJob?.cancel()
        _state.update { it.copy(chartState = ChartState.Loading) }

        currentJob = viewModelScope.launch {
            try {
                chartRepository.getChart(ticker, timeframe)
                    .catch { e -> _state.update { it.copy(chartState = ChartState.Error(e.message ?: "Unknown error")) } }
                    .collect { candles ->
                        if (candles.isNotEmpty()) {
                            val merged = mergeWithHistory(candles)
                            _state.update {
                                it.copy(
                                    chartState = ChartState.Success(
                                        candles = merged,
                                        currentPrice = merged.last().close,
                                    )
                                )
                            }
                        }
                    }
            } catch (e: CancellationException) {
                println("Job cancelled: ${e.message}")
            } catch (e: Exception) {
                _state.update { it.copy(chartState = ChartState.Error(e.message ?: "Unknown error")) }
            }
        }

        if (_state.value.chartMode == ChartMode.FOOTPRINT) {
            footprintController.start(_state.value.currentSymbol, _state.value.currentTimeframe)
        }
    }

    /**
     * Realtime-эмиссии репозитория содержат только его собственную историю + live-свечу,
     * поэтому подгруженный вручную префикс нужно добавлять к каждой эмиссии.
     */
    private fun mergeWithHistory(candles: List<Candle>): List<Candle> {
        if (historyPrefix.isEmpty()) return candles
        return (historyPrefix + candles)
            .distinctBy { it.timestamp }
            .sortedBy { it.timestamp }
    }

    private fun loadMoreHistory() {
        if (isLoadingMore || !_state.value.hasMoreHistory) return
        isLoadingMore = true

        viewModelScope.launch {
            try {
                val chart = _state.value.chartState
                if (chart !is ChartState.Success) { isLoadingMore = false; return@launch }

                val oldestTime = chart.candles.firstOrNull()?.timestamp ?: run { isLoadingMore = false; return@launch }
                val endTime = oldestTime - 1

                val historicalCandles = chartRepository.loadHistoricalCandlesBefore(
                    ticker = _state.value.currentSymbol, timeframe = _state.value.currentTimeframe, endTime = endTime, limit = 200
                )
                if (historicalCandles.isEmpty()) {
                    _state.update { it.copy(hasMoreHistory = false) }
                    isLoadingMore = false
                    return@launch
                }

                val newCandles = (historicalCandles + chart.candles)
                    .distinctBy { it.timestamp }
                    .sortedBy { it.timestamp }
                val lastPrice = newCandles.last().close

                // Не отменяем realtime-подписку: префикс будет домешан к следующим эмиссиям.
                historyPrefix = (historicalCandles + historyPrefix)
                    .distinctBy { it.timestamp }
                    .sortedBy { it.timestamp }
                _state.update {
                    it.copy(
                        chartState = ChartState.Success(candles = newCandles, currentPrice = lastPrice),
                        historyLoadCount = historicalCandles.size,
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isLoadingMore = false
            }
        }
    }
}
