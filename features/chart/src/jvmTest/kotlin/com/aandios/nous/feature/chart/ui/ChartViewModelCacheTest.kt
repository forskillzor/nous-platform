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
}
