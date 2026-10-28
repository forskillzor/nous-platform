/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.indicator

import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.core.domain.timeseries.TimeSeriesController
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LiquidationSeriesSourceTest {

    private fun order(timestamp: Long, quantity: Double = 1.0) = LiquidationOrder(
        symbol = "BTCUSDT",
        price = 62_000.0,
        quantity = quantity,
        timestamp = timestamp,
        side = TradeSide.BUY,
    )

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `unsupported history still allows live liquidation updates`() = runTest {
        val liveOrder = order(1_720_000_000_000L, 0.5)
        val adapter = object : LiquidationAdapter {
            override fun subscribeToLiquidations(symbol: String) = flowOf(liveOrder)

            override suspend fun getHistoricalLiquidations(
                symbol: String,
                startTime: Long?,
                endTime: Long?,
                limit: Int,
            ): List<LiquidationOrder> {
                throw UnsupportedOperationException("No public history endpoint")
            }
        }

        val controller = TimeSeriesController(
            source = LiquidationSeriesSource(adapter, "BTCUSDT"),
            scope = this,
        )
        controller.start()
        runCurrent()

        assertEquals(listOf(liveOrder), controller.state.value.items)
        assertNull(controller.state.value.error)
        controller.dispose()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `uses market-data history and appends live updates`() = runTest {
        val historyOrder = order(1_500L)
        val liveOrder = order(1_700L, 2.0)
        val live = MutableSharedFlow<LiquidationOrder>(extraBufferCapacity = 8)
        val adapter = object : LiquidationAdapter {
            override fun subscribeToLiquidations(symbol: String) = live

            override suspend fun getHistoricalLiquidations(
                symbol: String,
                startTime: Long?,
                endTime: Long?,
                limit: Int,
            ): List<LiquidationOrder> = error("provider history must not be used")
        }
        val history = FakeHistorySource { listOf(historyOrder) }
        val controller = TimeSeriesController(
            source = LiquidationSeriesSource(
                liveAdapter = adapter,
                symbol = "BTCUSDT",
                historySource = history,
                exchange = "Binance",
            ),
            scope = this,
        )
        controller.start()
        runCurrent()
        assertEquals(listOf(historyOrder), controller.state.value.items)

        live.emit(liveOrder)
        runCurrent()
        assertEquals(listOf(historyOrder, liveOrder), controller.state.value.items)
        controller.dispose()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `minute reconciliation adds missed server events and ignores duplicates`() = runTest {
        val historyOrder = order(1_500L)
        val missedOrder = order(70_000L, 3.0)
        val history = FakeHistorySource { call ->
            if (call == 1) listOf(historyOrder) else listOf(historyOrder, missedOrder)
        }
        val controller = TimeSeriesController(
            source = LiquidationSeriesSource(
                liveAdapter = null,
                symbol = "BTCUSDT",
                historySource = history,
                exchange = "Binance",
                clock = { testScheduler.currentTime },
            ),
            scope = this,
        )
        controller.start()
        runCurrent()
        assertEquals(listOf(historyOrder), controller.state.value.items)

        advanceTimeBy(60_000L + 1_000L)
        runCurrent()

        assertEquals(listOf(historyOrder, missedOrder), controller.state.value.items)
        assertTrue(history.calls >= 2)
        controller.dispose()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `market-data failure falls back to provider history`() = runTest {
        val fallback = order(2_000L)
        val adapter = object : LiquidationAdapter {
            override fun subscribeToLiquidations(symbol: String) = emptyFlow<LiquidationOrder>()

            override suspend fun getHistoricalLiquidations(
                symbol: String,
                startTime: Long?,
                endTime: Long?,
                limit: Int,
            ): List<LiquidationOrder> = listOf(fallback)
        }
        val history = FakeHistorySource { throw IllegalStateException("server down") }
        val controller = TimeSeriesController(
            source = LiquidationSeriesSource(
                liveAdapter = adapter,
                symbol = "BTCUSDT",
                historySource = history,
                exchange = "Binance",
            ),
            scope = this,
        )
        controller.start()
        runCurrent()

        assertEquals(listOf(fallback), controller.state.value.items)
        controller.dispose()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `history is available without a live adapter`() = runTest {
        val historyOrder = order(1_500L)
        val history = FakeHistorySource { listOf(historyOrder) }
        val controller = TimeSeriesController(
            source = LiquidationSeriesSource(
                liveAdapter = null,
                symbol = "BTCUSDT",
                historySource = history,
                exchange = "Binance",
            ),
            scope = this,
        )
        controller.start()
        runCurrent()

        assertEquals(listOf(historyOrder), controller.state.value.items)
        controller.dispose()
    }

    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `live duplicate of a history event is not counted twice`() = runTest {
        val historyOrder = order(1_500L)
        val live = MutableSharedFlow<LiquidationOrder>(extraBufferCapacity = 8)
        val adapter = object : LiquidationAdapter {
            override fun subscribeToLiquidations(symbol: String) = live

            override suspend fun getHistoricalLiquidations(
                symbol: String,
                startTime: Long?,
                endTime: Long?,
                limit: Int,
            ): List<LiquidationOrder> = emptyList()
        }
        val history = FakeHistorySource { listOf(historyOrder) }
        val controller = TimeSeriesController(
            source = LiquidationSeriesSource(
                liveAdapter = adapter,
                symbol = "BTCUSDT",
                historySource = history,
                exchange = "Binance",
            ),
            scope = this,
        )
        controller.start()
        runCurrent()

        live.emit(historyOrder)
        runCurrent()

        assertEquals(listOf(historyOrder), controller.state.value.items)
        controller.dispose()
    }

    private class FakeHistorySource(
        private val block: (call: Int) -> List<LiquidationOrder>,
    ) : LiquidationHistorySource {
        var calls = 0
            private set

        override suspend fun getLiquidations(
            exchange: String,
            symbol: String,
            from: Long,
            to: Long,
            limit: Int,
        ): List<LiquidationOrder> {
            calls++
            return block(calls)
        }
    }
}
