/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.api.market.adapters.SymbolInfoAdapter
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.core.domain.cache.CandleCacheStore
import com.aandios.nous.core.domain.repository.ChartRepository
import com.aandios.nous.core.domain.timeseries.TimeSeriesSource
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

    private class FakeCandleSource(
        private val initial: List<Candle>,
        private val live: Flow<Candle> = emptyFlow(),
    ) : TimeSeriesSource<Candle> {
        override suspend fun loadInitial(): List<Candle> = initial
        override suspend fun loadBefore(beforeTimestamp: Long, limit: Int): List<Candle> = emptyList()
        override fun liveUpdates(): Flow<Candle> = live
        override fun mergeItem(items: List<Candle>, update: Candle): List<Candle> = items + update
        override fun timestampOf(item: Candle): Long = item.timestamp
    }

    private class FakeChartRepository(
        private val sources: Map<String, List<Candle>>,
    ) : ChartRepository {
        override fun candleSource(ticker: String, timeframe: String): TimeSeriesSource<Candle> =
            FakeCandleSource(sources["$ticker/$timeframe"] ?: emptyList())
    }

    private class FakeSymbolInfoAdapter : SymbolInfoAdapter {
        override suspend fun getSymbolInfo(symbol: String): SymbolInfo? = null
    }

    private fun createViewModel(
        cache: FakeCandleCacheStore,
        repository: ChartRepository,
        testDispatcher: TestDispatcher,
    ) = ChartViewModel(
        chartRepository = repository,
        symbolInfoAdapter = FakeSymbolInfoAdapter(),
        candleCache = cache,
        cacheDispatcher = testDispatcher,
    )

    @Test
    fun `initial load saves candles to cache`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val dispatcher = StandardTestDispatcher(testScheduler)
            val cache = FakeCandleCacheStore()
            val repository = FakeChartRepository(mapOf("BTCUSDT/1h" to (0 until 5).map { candle(it) }))
            val vm = createViewModel(cache, repository, dispatcher)

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
            val repository = FakeChartRepository(emptyMap()) // сеть «пуста»
            val vm = createViewModel(cache, repository, dispatcher)

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
            val repository = FakeChartRepository(mapOf("BTCUSDT/1h" to (0 until 5).map { candle(it) }))
            val vm = createViewModel(cache, repository, dispatcher)

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
