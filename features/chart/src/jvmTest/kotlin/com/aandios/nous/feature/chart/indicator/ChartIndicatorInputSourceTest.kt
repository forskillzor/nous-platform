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
    fun `setMarket loads history and live updates into snapshot`() = runTest {
        val history = order(1_500L, 2.0)
        val liveOrder = order(1_700L, 1.0)
        val adapter = FakeLiquidationAdapter(history = listOf(history))
        val source = ChartIndicatorInputSource(this)

        source.setMarket("BTCUSDT", adapter)
        runCurrent()
        assertEquals(listOf(history), source.snapshot(required, candles).liquidations)

        adapter.live.emit(liveOrder)
        runCurrent()
        assertEquals(listOf(history, liveOrder), source.snapshot(required, candles).liquidations)

        source.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `setMarket null clears the feed`() = runTest {
        val source = ChartIndicatorInputSource(this)
        source.setMarket("BTCUSDT", FakeLiquidationAdapter(history = listOf(order(1_500L, 2.0))))
        runCurrent()
        assertTrue(source.snapshot(required, candles).liquidations.isNotEmpty())

        source.setMarket("", null)
        assertTrue(source.snapshot(required, candles).liquidations.isEmpty())

        source.close()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `quick symbol switch does not leak old data`() = runTest {
        val first = FakeLiquidationAdapter(history = listOf(order(1_500L, 9.0)))
        val second = FakeLiquidationAdapter(history = listOf(order(1_600L, 3.0)))
        val source = ChartIndicatorInputSource(this)

        source.setMarket("BTCUSDT", first)
        runCurrent()
        source.setMarket("ETHUSDT", second)
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
        val source = ChartIndicatorInputSource(this)
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            source.updates().collect { received += it }
        }

        source.setMarket("BTCUSDT", FakeLiquidationAdapter(history = listOf(order(1_500L, 2.0))))
        runCurrent()

        assertTrue(received.isNotEmpty())
        collector.cancel()
        source.close()
    }

    @Test
    fun `snapshot omits feed when not required`() = runTest {
        val source = ChartIndicatorInputSource(this)
        source.setMarket("BTCUSDT", FakeLiquidationAdapter(history = listOf(order(1_500L, 2.0))))
        runCurrent()

        assertTrue(source.snapshot(emptySet(), candles).liquidations.isEmpty())
        source.close()
    }

    @Test
    fun `close is idempotent`() = runTest {
        val source = ChartIndicatorInputSource(this)
        source.close()
        source.close()

        source.setMarket("BTCUSDT", FakeLiquidationAdapter(history = listOf(order(1_500L, 2.0))))
        assertTrue(source.snapshot(required, candles).liquidations.isEmpty())
    }

    private fun order(timestamp: Long, quantity: Double): LiquidationOrder = LiquidationOrder(
        symbol = "BTCUSDT",
        price = 1.0,
        quantity = quantity,
        timestamp = timestamp,
        side = TradeSide.SELL,
    )

    private class FakeLiquidationAdapter(
        private val history: List<LiquidationOrder> = emptyList(),
    ) : LiquidationAdapter {
        val live = MutableSharedFlow<LiquidationOrder>(extraBufferCapacity = 8)

        override fun subscribeToLiquidations(symbol: String) = live

        override suspend fun getHistoricalLiquidations(
            symbol: String,
            startTime: Long?,
            endTime: Long?,
            limit: Int,
        ): List<LiquidationOrder> = history
    }
}
