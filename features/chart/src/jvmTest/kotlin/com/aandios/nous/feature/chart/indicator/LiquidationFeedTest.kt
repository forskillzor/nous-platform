/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.indicator

import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.api.market.model.trading.TradeSide
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiquidationFeedTest {

    private fun order(
        timestamp: Long,
        quantity: Double = 1.0,
        side: TradeSide = TradeSide.SELL,
    ) = LiquidationOrder(
        symbol = "BTCUSDT",
        price = 62_000.0,
        quantity = quantity,
        timestamp = timestamp,
        side = side,
    )

    private fun bucket(
        start: Long,
        end: Long,
        longVolume: Double = 0.0,
        shortVolume: Double = 0.0,
    ): LiquidationBucket = LiquidationBucket(
        startTime = start,
        endTime = end,
        orders = buildList {
            if (longVolume > 0.0) {
                add(order(start, longVolume, TradeSide.SELL))
            }
            if (shortVolume > 0.0) {
                add(order(start, shortVolume, TradeSide.BUY))
            }
        },
    )

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `loads visible aggregate history and appends live events`() = runTest {
        val history = FakeHistory { _, _, _, _, _, _ ->
            listOf(bucket(0L, 900_000L, longVolume = 2.0))
        }
        val live = MutableSharedFlow<LiquidationOrder>(extraBufferCapacity = 8)
        val feed = LiquidationFeed(
            scope = this,
            symbol = "BTCUSDT",
            exchange = "Binance",
            timeframe = "15m",
            liveAdapter = FakeLive(live),
            historySource = history,
            clock = { testScheduler.currentTime },
        )

        feed.setVisibleRange(0L, 900_000L)
        feed.start()
        runCurrent()

        assertEquals(listOf(order(0L, 2.0)), feed.orders.value)
        val firstCall = history.calls.single()
        assertEquals("Binance", firstCall.exchange)
        assertEquals("BTCUSDT", firstCall.symbol)
        assertEquals("15m", firstCall.timeframe)

        val liveOrder = order(600_000L, 3.0, TradeSide.BUY)
        live.emit(liveOrder)
        runCurrent()

        assertEquals(listOf(order(0L, 2.0), liveOrder), feed.orders.value)
        feed.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `loads missing history when the visible range moves`() = runTest {
        val history = FakeHistory { _, _, _, from, _, _ ->
            listOf(bucket(from, from + 900_000L, longVolume = 1.0))
        }
        val feed = LiquidationFeed(
            scope = this,
            symbol = "BTCUSDT",
            exchange = "Binance",
            timeframe = "15m",
            liveAdapter = null,
            historySource = history,
            clock = { testScheduler.currentTime },
        )

        feed.setVisibleRange(0L, 900_000L)
        feed.start()
        runCurrent()
        assertEquals(1, history.calls.size)

        feed.setVisibleRange(9_000_000L, 9_900_000L)
        runCurrent()

        assertTrue(history.calls.size >= 2)
        assertTrue(history.calls.last().from < 9_000_000L)
        assertTrue(feed.orders.value.size >= 2)
        feed.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `minute reconciliation replaces a closed bucket and removes live duplicates`() = runTest {
        var calls = 0
        val history = FakeHistory { _, _, _, _, _, _ ->
            calls++
            val volume = if (calls == 1) 1.0 else 3.0
            listOf(bucket(0L, 900_000L, longVolume = volume))
        }
        val live = MutableSharedFlow<LiquidationOrder>(extraBufferCapacity = 8)
        val feed = LiquidationFeed(
            scope = this,
            symbol = "BTCUSDT",
            exchange = "Binance",
            timeframe = "15m",
            liveAdapter = FakeLive(live),
            historySource = history,
            clock = { testScheduler.currentTime },
        )

        feed.setVisibleRange(0L, 900_000L)
        feed.start()
        runCurrent()
        live.emit(order(600_000L, 2.0))
        runCurrent()
        assertEquals(2, feed.orders.value.size)

        advanceTimeBy(61_000L)
        runCurrent()

        assertEquals(listOf(order(0L, 3.0)), feed.orders.value)
        assertTrue(history.calls.size >= 2)
        feed.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `history works without a live adapter`() = runTest {
        val history = FakeHistory { _, _, _, _, _, _ ->
            listOf(bucket(0L, 900_000L, shortVolume = 4.0))
        }
        val feed = LiquidationFeed(
            scope = this,
            symbol = "BTCUSDT",
            exchange = "Binance",
            timeframe = "15m",
            liveAdapter = null,
            historySource = history,
            clock = { testScheduler.currentTime },
        )

        feed.setVisibleRange(0L, 900_000L)
        feed.start()
        runCurrent()

        assertEquals(listOf(order(0L, 4.0, TradeSide.BUY)), feed.orders.value)
        feed.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `failed range load keeps already loaded history`() = runTest {
        var calls = 0
        val history = FakeHistory { _, _, _, _, _, _ ->
            calls++
            if (calls > 1) error("server down")
            listOf(bucket(0L, 900_000L, longVolume = 1.0))
        }
        val feed = LiquidationFeed(
            scope = this,
            symbol = "BTCUSDT",
            exchange = "Binance",
            timeframe = "15m",
            liveAdapter = null,
            historySource = history,
            clock = { testScheduler.currentTime },
        )

        feed.setVisibleRange(0L, 900_000L)
        feed.start()
        runCurrent()
        assertEquals(1, feed.orders.value.size)

        feed.setVisibleRange(9_000_000L, 9_900_000L)
        runCurrent()

        assertEquals(listOf(order(0L, 1.0)), feed.orders.value)
        feed.close()
    }

    private class FakeLive(
        private val flow: MutableSharedFlow<LiquidationOrder>,
    ) : LiquidationAdapter {
        override fun subscribeToLiquidations(symbol: String) = flow

        override suspend fun getHistoricalLiquidations(
            symbol: String,
            startTime: Long?,
            endTime: Long?,
            limit: Int,
        ): List<LiquidationOrder> = emptyList()
    }

    private data class HistoryCall(
        val exchange: String,
        val symbol: String,
        val timeframe: String,
        val from: Long,
        val to: Long,
        val limit: Int,
    )

    private class FakeHistory(
        private val block: (
            exchange: String,
            symbol: String,
            timeframe: String,
            from: Long,
            to: Long,
            limit: Int,
        ) -> List<LiquidationBucket>,
    ) : LiquidationHistorySource {
        val calls = mutableListOf<HistoryCall>()

        override suspend fun getAggregates(
            exchange: String,
            symbol: String,
            timeframe: String,
            from: Long,
            to: Long,
            limit: Int,
        ): List<LiquidationBucket> {
            calls += HistoryCall(exchange, symbol, timeframe, from, to, limit)
            return block(exchange, symbol, timeframe, from, to, limit)
        }
    }
}
