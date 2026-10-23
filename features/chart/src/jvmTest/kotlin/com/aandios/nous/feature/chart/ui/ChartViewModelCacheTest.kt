/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.api.market.NetworkManager
import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.api.market.adapters.BookTickerAdapter
import com.aandios.nous.api.market.adapters.ChartAdapter
import com.aandios.nous.api.market.adapters.DomAdapter
import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.adapters.SymbolInfoAdapter
import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.api.market.adapters.TradingAdapter
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.core.domain.cache.CandleCacheStore
import com.aandios.nous.research.indicator.LIQUIDATIONS_PANE_ID
import com.aandios.nous.research.indicator.createIndicator
import com.aandios.nous.research.indicator.IndicatorPaneLayout
import com.aandios.nous.research.indicator.IndicatorType
import com.aandios.nous.research.indicator.effectivePaneHeightDp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class ChartViewModelCacheTest {

    private fun candle(i: Int) = Candle(
        open = 100f,
        high = 101f,
        close = 100.5f,
        low = 99f,
        timestamp = i * 60_000L,
        volume = 10f,
    )

    private class FakeCandleCacheStore : CandleCacheStore {
        data class Write(
            val exchange: String,
            val symbol: String,
            val timeframe: String,
            val candles: List<Candle>,
        )

        val writes = mutableListOf<Write>()
        var cached: Map<String, List<Candle>> = emptyMap()
        var lastGetKey: String? = null

        override suspend fun saveCandles(exchange: String, symbol: String, timeframe: String, candles: List<Candle>) {
            writes.add(Write(exchange, symbol, timeframe, candles))
        }

        override suspend fun getCandles(exchange: String, symbol: String, timeframe: String, limit: Int): List<Candle> {
            lastGetKey = "$exchange/$symbol/$timeframe"
            return cached["$symbol/$timeframe"] ?: emptyList()
        }
    }

    private class FakeChartAdapter(
        private val sources: Map<String, List<Candle>>,
    ) : ChartAdapter {
        override suspend fun getCandles(symbol: String, interval: String, limit: Int): List<Candle> =
            sources["$symbol/$interval"] ?: emptyList()

        override suspend fun getCandlesBefore(symbol: String, interval: String, endTime: Long, limit: Int): List<Candle> =
            emptyList()

        override fun subscribeToCandles(symbol: String, interval: String): Flow<Candle> = emptyFlow()
    }

    private class FakeSymbolInfoAdapter : SymbolInfoAdapter {
        override suspend fun getSymbolInfo(symbol: String): SymbolInfo? = null
    }

    /** Фейковый провайдер: только chart + symbolInfo, displayName как у Binance. */
    private class FakeProvider(
        private val chartAdapter: ChartAdapter,
        private val symbolInfoAdapter: SymbolInfoAdapter,
    ) : Provider {
        override val providerId = "binance-nous-0.0.1"
        override val providerName = "binance-nous"
        override val version = "0.0.1"
        override val config = ProviderConfig(displayName = "Binance")
        override val networkManager: NetworkManager get() = error("not used")
        override val trades: TradesAdapter? = null
        override val dom: DomAdapter? = null
        override val bookTicker: BookTickerAdapter? = null
        override val chart: ChartAdapter? = chartAdapter
        override val trading: TradingAdapter? = null
        override val symbolInfo: SymbolInfoAdapter? = symbolInfoAdapter
        override val liquidation: LiquidationAdapter? = null
    }

    private fun createViewModel(
        cache: FakeCandleCacheStore,
        sources: Map<String, List<Candle>>,
        testDispatcher: TestDispatcher,
    ) = ChartViewModel(
        providerRegistry = ProviderRegistry(
            listOf(FakeProvider(FakeChartAdapter(sources), FakeSymbolInfoAdapter()))
        ),
        candleCache = cache,
        cacheDispatcher = testDispatcher,
    )

    @Test
    fun `initial load saves candles to cache`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val cache = FakeCandleCacheStore()
            val vm = createViewModel(cache, mapOf("BTCUSDT/1h" to (0 until 5).map { candle(it) }), dispatcher)

            vm.dispatch(ChartIntent.LoadChart("BTCUSDT", "1h"))
            advanceUntilIdle()

            assertTrue(
                cache.writes.any {
                    it.exchange == "Binance" && it.symbol == "BTCUSDT" && it.timeframe == "1h" && it.candles.size == 5
                }
            )
            vm.dispose()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `cached candles are shown before fresh data arrives`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val cached = (0 until 3).map { candle(it) }
            val cache = FakeCandleCacheStore()
            cache.cached = mapOf("BTCUSDT/1h" to cached)
            val vm = createViewModel(cache, emptyMap(), dispatcher) // сеть «пуста»

            val states = mutableListOf<ChartUiState>()
            // Unconfined: записываем КАЖДУЮ эмиссию без conflation
            val collector = launch(UnconfinedTestDispatcher(testScheduler)) {
                vm.state.collect { states.add(it) }
            }

            vm.dispatch(ChartIntent.LoadChart("BTCUSDT", "1h"))
            advanceUntilIdle()

            assertTrue(
                states.any { state ->
                    state.chartState is ChartState.Success &&
                        (state.chartState as ChartState.Success).candles == cached
                }
            )
            assertEquals("Binance/BTCUSDT/1h", cache.lastGetKey)

            collector.cancel()
            vm.dispose()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `switching symbol flushes previous snapshot`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val cache = FakeCandleCacheStore()
            val vm = createViewModel(cache, mapOf("BTCUSDT/1h" to (0 until 5).map { candle(it) }), dispatcher)

            vm.dispatch(ChartIntent.LoadChart("BTCUSDT", "1h"))
            advanceUntilIdle()
            assertEquals(1, cache.writes.count { it.symbol == "BTCUSDT" })

            // У ETH пустой initial — новая запись не появится, но flush должен сохранить BTC
            vm.dispatch(ChartIntent.SelectSymbol("ETHUSDT"))
            advanceUntilIdle()

            assertTrue(
                cache.writes.count { it.symbol == "BTCUSDT" } >= 2,
                "ожидали flush предыдущего снапшота при смене символа",
            )
            vm.dispose()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `indicator intents add overlays and pane tabs`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val vm = createViewModel(FakeCandleCacheStore(), emptyMap(), dispatcher)

            vm.dispatch(ChartIntent.AddIndicatorOverlay(IndicatorType.SMA))
            vm.dispatch(ChartIntent.AddIndicatorToPane(IndicatorType.RSI, createPane = true))
            advanceUntilIdle()

            val indicators = vm.state.value.indicators
            assertEquals(1, indicators.overlays.size)
            assertEquals(IndicatorType.SMA, indicators.overlays.single().type)

            val rsiPane = indicators.panes.single { pane ->
                pane.indicators.any { it.type == IndicatorType.RSI }
            }
            assertEquals(rsiPane.id, indicators.activePaneId)
            assertEquals(14.0, rsiPane.indicators.single().params["period"])

            vm.dispose()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `indicator params are clamped and empty panes are removed`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val vm = createViewModel(FakeCandleCacheStore(), emptyMap(), dispatcher)

            vm.dispatch(ChartIntent.AddIndicatorToPane(IndicatorType.RSI, createPane = true))
            advanceUntilIdle()
            val rsiPane = vm.state.value.indicators.panes.single { pane ->
                pane.indicators.any { it.type == IndicatorType.RSI }
            }
            val rsi = rsiPane.indicators.single { it.type == IndicatorType.RSI }

            vm.dispatch(ChartIntent.UpdateIndicatorParams(rsi.id, mapOf("period" to 1000.0)))
            advanceUntilIdle()
            val updated = vm.state.value.indicators.panes
                .single { it.id == rsiPane.id }
                .indicators
                .single { it.id == rsi.id }
            assertEquals(500.0, updated.params["period"])

            vm.dispatch(ChartIntent.SelectIndicatorPane(LIQUIDATIONS_PANE_ID))
            vm.dispatch(ChartIntent.RemoveIndicator(rsi.id))
            advanceUntilIdle()

            val indicators = vm.state.value.indicators
            assertTrue(indicators.panes.none { it.id == rsiPane.id })
            assertEquals(LIQUIDATIONS_PANE_ID, indicators.activePaneId)

            vm.dispose()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `indicator panes keep layout height and order`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val vm = createViewModel(FakeCandleCacheStore(), emptyMap(), dispatcher)

            vm.dispatch(ChartIntent.AddIndicatorToPane(IndicatorType.RSI, createPane = true))
            vm.dispatch(ChartIntent.AddIndicatorToPane(IndicatorType.MACD, createPane = true))
            vm.dispatch(ChartIntent.AddIndicatorToPane(IndicatorType.ATR, createPane = true))
            vm.dispatch(ChartIntent.SetIndicatorPaneLayout(IndicatorPaneLayout.STACKED))
            advanceUntilIdle()

            val panes = vm.state.value.indicators.panes
            val first = panes.first { pane -> pane.indicators.any { it.type == IndicatorType.RSI } }

            vm.dispatch(ChartIntent.SetIndicatorPaneHeight(first.id, 999f, persist = false))
            advanceUntilIdle()
            assertEquals(240f, vm.state.value.indicators.panes.first { it.id == first.id }.heightDp)

            vm.dispatch(ChartIntent.SetIndicatorPaneHeight(first.id, 10f))
            advanceUntilIdle()
            assertEquals(40f, vm.state.value.indicators.panes.first { it.id == first.id }.heightDp)

            assertEquals(IndicatorPaneLayout.STACKED, vm.state.value.indicators.paneLayout)

            val lastPane = vm.state.value.indicators.panes.last()
            vm.dispatch(ChartIntent.MoveIndicatorPane(lastPane.id, 0))
            advanceUntilIdle()
            assertEquals(lastPane.id, vm.state.value.indicators.panes.first().id)

            vm.dispose()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `tab layout shares one height while stacked keeps per pane heights`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val vm = createViewModel(FakeCandleCacheStore(), emptyMap(), dispatcher)

            vm.dispatch(ChartIntent.AddIndicatorToPane(IndicatorType.RSI, createPane = true))
            vm.dispatch(ChartIntent.AddIndicatorToPane(IndicatorType.MACD, createPane = true))
            advanceUntilIdle()
            val rsiPane = vm.state.value.indicators.panes.first { pane ->
                pane.indicators.any { it.type == IndicatorType.RSI }
            }
            val macdPane = vm.state.value.indicators.panes.first { pane ->
                pane.indicators.any { it.type == IndicatorType.MACD }
            }

            vm.dispatch(ChartIntent.SetIndicatorPaneHeight(rsiPane.id, 130f))
            advanceUntilIdle()
            assertEquals(130f, vm.state.value.indicators.tabsHeightDp)
            assertEquals(130f, vm.state.value.indicators.effectivePaneHeightDp(rsiPane.id))
            assertEquals(80f, vm.state.value.indicators.panes.first { it.id == rsiPane.id }.heightDp)

            vm.dispatch(ChartIntent.SelectIndicatorPane(macdPane.id))
            advanceUntilIdle()
            assertEquals(130f, vm.state.value.indicators.effectivePaneHeightDp(macdPane.id))

            vm.dispatch(ChartIntent.SetIndicatorPaneLayout(IndicatorPaneLayout.STACKED))
            vm.dispatch(ChartIntent.SetIndicatorPaneHeight(rsiPane.id, 150f))
            vm.dispatch(ChartIntent.SetIndicatorPaneHeight(macdPane.id, 90f))
            advanceUntilIdle()
            assertEquals(130f, vm.state.value.indicators.tabsHeightDp)
            assertEquals(150f, vm.state.value.indicators.effectivePaneHeightDp(rsiPane.id))
            assertEquals(90f, vm.state.value.indicators.effectivePaneHeightDp(macdPane.id))

            vm.dispatch(ChartIntent.SetIndicatorPaneLayout(IndicatorPaneLayout.TABS))
            advanceUntilIdle()
            assertEquals(130f, vm.state.value.indicators.effectivePaneHeightDp(rsiPane.id))
            assertEquals(130f, vm.state.value.indicators.effectivePaneHeightDp(macdPane.id))

            vm.dispose()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `applying preset replaces indicators but keeps layout and heights`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val vm = createViewModel(FakeCandleCacheStore(), emptyMap(), dispatcher)

            vm.dispatch(ChartIntent.AddIndicatorToPane(IndicatorType.RSI, createPane = true))
            advanceUntilIdle()
            val rsiPane = vm.state.value.indicators.panes.first { pane ->
                pane.indicators.any { it.type == IndicatorType.RSI }
            }
            vm.dispatch(ChartIntent.SetIndicatorPaneLayout(IndicatorPaneLayout.STACKED))
            vm.dispatch(ChartIntent.SetIndicatorPaneHeight(rsiPane.id, 120f))
            advanceUntilIdle()

            val preset = IndicatorPreset(
                id = "preset-1",
                name = "Scalp",
                overlays = listOf(createIndicator(IndicatorType.EMA, "ema-preset")),
                panes = listOf(
                    IndicatorPresetPane(
                        listOf(createIndicator(IndicatorType.RSI, "rsi-preset")),
                    ),
                    IndicatorPresetPane(
                        listOf(createIndicator(IndicatorType.MACD, "macd-preset")),
                    ),
                ),
                activePaneIndex = 1,
            )
            vm.dispatch(ChartIntent.ApplyIndicatorPreset(preset))
            advanceUntilIdle()

            val indicators = vm.state.value.indicators
            assertEquals(IndicatorPaneLayout.STACKED, indicators.paneLayout)
            assertEquals(listOf(80f, 120f), indicators.panes.map { it.heightDp })
            assertEquals("ema-preset", indicators.overlays.single().id)
            assertEquals(1, indicators.panes.indexOfFirst { it.id == indicators.activePaneId })

            vm.dispose()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `selecting an existing indicator pane updates the active pane`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val vm = createViewModel(FakeCandleCacheStore(), emptyMap(), dispatcher)

            val defaultPane = vm.state.value.indicators.panes.single()
            vm.dispatch(ChartIntent.AddIndicatorToPane(IndicatorType.RSI, createPane = true))
            advanceUntilIdle()
            vm.dispatch(ChartIntent.SelectIndicatorPane(defaultPane.id))
            advanceUntilIdle()
            assertEquals(defaultPane.id, vm.state.value.indicators.activePaneId)

            vm.dispatch(ChartIntent.SelectIndicatorPane("missing-pane"))
            advanceUntilIdle()
            assertEquals(defaultPane.id, vm.state.value.indicators.activePaneId)

            vm.dispose()
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `indicator line colors are set and reset to the theme default`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val vm = createViewModel(FakeCandleCacheStore(), emptyMap(), dispatcher)

            vm.dispatch(ChartIntent.AddIndicatorToPane(IndicatorType.RSI, createPane = true))
            advanceUntilIdle()
            val pane = vm.state.value.indicators.panes.first { pane ->
                pane.indicators.any { it.type == IndicatorType.RSI }
            }
            val instance = pane.indicators.single()

            vm.dispatch(ChartIntent.SetIndicatorLineColor(instance.id, "rsi", 0xFFFF0000L))
            advanceUntilIdle()
            assertEquals(
                mapOf("rsi" to 0xFFFF0000L),
                vm.state.value.indicators.panes.first { it.id == pane.id }
                    .indicators.single().lineColors,
            )

            vm.dispatch(ChartIntent.SetIndicatorLineColor(instance.id, "rsi", null))
            advanceUntilIdle()
            assertTrue(
                vm.state.value.indicators.panes.first { it.id == pane.id }
                    .indicators.single().lineColors.isEmpty(),
            )

            vm.dispose()
        } finally {
            Dispatchers.resetMain()
        }
    }
}
