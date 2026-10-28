/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.indicator

import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.research.indicator.InputKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ChartIndicatorInputSourceTest {

    private val candles = listOf(
        Candle(open = 1f, high = 1f, low = 1f, close = 1f, timestamp = 1_000L, volume = 1f),
        Candle(open = 1f, high = 1f, low = 1f, close = 1f, timestamp = 2_000L, volume = 1f),
    )

    private val required = setOf(InputKind.LIQUIDATIONS)

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `setMarket loads aggregate history and live updates into snapshot`() = runTest {
        val historyOrder = order(1_500L, 2.0)
        val liveOrder = order(1_700L, 1.0)
        val adapter = FakeLiquidationAdapter()
        val history = FakeHistory(listOf(bucket(0L, 900_000L, historyOrder)))
        val source = ChartIndicatorInputSource(this, history)

        source.setMarket("BTCUSDT", adapter, "Binance", "15m")
        source.setVisibleRange(0L, 900_000L)
        runCurrent()
        assertEquals(listOf(historyOrder), source.snapshot(required, candles).liquidations)

        adapter.live.emit(liveOrder)
        runCurrent()
        assertEquals(listOf(historyOrder, liveOrder), source.snapshot(required, candles).liquidations)

        source.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `setMarket null clears the feed`() = runTest {
        val source = ChartIndicatorInputSource(
            this,
            FakeHistory(listOf(bucket(0L, 900_000L, order(1_500L, 2.0)))),
        )
        source.setMarket("BTCUSDT", FakeLiquidationAdapter(), "Binance", "15m")
        source.setVisibleRange(0L, 900_000L)
        runCurrent()
        assertTrue(source.snapshot(required, candles).liquidations.isNotEmpty())

        source.setMarket("", null)
        assertTrue(source.snapshot(required, candles).liquidations.isEmpty())

        source.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `quick symbol switch does not leak old data`() = runTest {
        val first = FakeLiquidationAdapter()
        val second = FakeLiquidationAdapter()
        val history = FakeHistoryBySymbol { symbol ->
            if (symbol == "BTCUSDT") {
                listOf(bucket(0L, 900_000L, order(1_500L, 9.0)))
            } else {
                listOf(bucket(0L, 900_000L, order(1_600L, 3.0)))
            }
        }
        val source = ChartIndicatorInputSource(this, history)

        source.setMarket("BTCUSDT", first, "Binance", "15m")
        source.setVisibleRange(0L, 900_000L)
        runCurrent()
        source.setMarket("ETHUSDT", second, "Binance", "15m")
        runCurrent()
        first.live.emit(order(1_700L, 8.0))
        runCurrent()

        assertEquals(listOf(order(1_600L, 3.0)), source.snapshot(required, candles).liquidations)
        source.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `updates emit when feed changes`() = runTest {
        val received = mutableListOf<Unit>()
        val source = ChartIndicatorInputSource(
            this,
            FakeHistory(listOf(bucket(0L, 900_000L, order(1_500L, 2.0)))),
        )
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source.updates().collect { received += it }
        }

        source.setMarket("BTCUSDT", FakeLiquidationAdapter(), "Binance", "15m")
        source.setVisibleRange(0L, 900_000L)
        runCurrent()

        assertTrue(received.isNotEmpty())
        collector.cancel()
        source.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `snapshot omits feed when not required`() = runTest {
        val source = ChartIndicatorInputSource(this, FakeHistory(emptyList()))
        source.setMarket("BTCUSDT", FakeLiquidationAdapter(), "Binance", "15m")
        source.setVisibleRange(0L, 900_000L)
        runCurrent()

        assertTrue(source.snapshot(emptySet(), candles).liquidations.isEmpty())
        source.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `market-data history is used with the active exchange and timeframe without a live adapter`() =
        runTest {
            var requestedExchange: String? = null
            var requestedTimeframe: String? = null
            val historyOrder = order(1_500L, 2.0)
            val history = object : LiquidationHistorySource {
                override suspend fun getAggregates(
                    exchange: String,
                    symbol: String,
                    timeframe: String,
                    from: Long,
                    to: Long,
                    limit: Int,
                ): List<LiquidationBucket> {
                    requestedExchange = exchange
                    requestedTimeframe = timeframe
                    return listOf(bucket(0L, 900_000L, historyOrder))
                }
            }
            val source = ChartIndicatorInputSource(this, history)
            source.setMarket("BTCUSDT", adapter = null, exchange = "Binance", timeframe = "15m")
            source.setVisibleRange(0L, 900_000L)
            runCurrent()

            assertEquals("Binance", requestedExchange)
            assertEquals("15m", requestedTimeframe)
            assertEquals(listOf(historyOrder), source.snapshot(required, candles).liquidations)
            source.close()
        }

    @Test
    fun `close is idempotent`() = runTest {
        val source = ChartIndicatorInputSource(this, FakeHistory(emptyList()))
        source.close()
        source.close()

        source.setMarket("BTCUSDT", FakeLiquidationAdapter(), "Binance", "15m")
        assertTrue(source.snapshot(required, candles).liquidations.isEmpty())
    }

    private fun order(timestamp: Long, quantity: Double): LiquidationOrder = LiquidationOrder(
        symbol = "BTCUSDT",
        price = 1.0,
        quantity = quantity,
        timestamp = timestamp,
        side = TradeSide.SELL,
    )

    private fun bucket(
        start: Long,
        end: Long,
        order: LiquidationOrder,
    ) = LiquidationBucket(startTime = start, endTime = end, orders = listOf(order))

    private class FakeLiquidationAdapter : LiquidationAdapter {
        val live = MutableSharedFlow<LiquidationOrder>(extraBufferCapacity = 8)

        override fun subscribeToLiquidations(symbol: String) = live

        override suspend fun getHistoricalLiquidations(
            symbol: String,
            startTime: Long?,
            endTime: Long?,
            limit: Int,
        ): List<LiquidationOrder> = emptyList()
    }

    private class FakeHistory(
        private val buckets: List<LiquidationBucket>,
    ) : LiquidationHistorySource {
        override suspend fun getAggregates(
            exchange: String,
            symbol: String,
            timeframe: String,
            from: Long,
            to: Long,
            limit: Int,
        ): List<LiquidationBucket> = buckets
    }

    private class FakeHistoryBySymbol(
        private val block: (symbol: String) -> List<LiquidationBucket>,
    ) : LiquidationHistorySource {
        override suspend fun getAggregates(
            exchange: String,
            symbol: String,
            timeframe: String,
            from: Long,
            to: Long,
            limit: Int,
        ): List<LiquidationBucket> = block(symbol)
    }
}
