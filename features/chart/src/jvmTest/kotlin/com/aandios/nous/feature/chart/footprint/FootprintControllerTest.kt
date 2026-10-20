/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.footprint

import com.aandios.nous.api.market.adapters.TradesAdapter
import com.aandios.nous.api.market.model.trades.Trade
import com.aandios.nous.api.market.model.trading.TradeSide
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalCoroutinesApi::class)
class FootprintControllerTest {

    @Test
    fun `live snapshots are published at most every 100 ms`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val trades = MutableSharedFlow<Trade>(extraBufferCapacity = 8)
        var now = 100L
        val controller = FootprintController(
            footprintApiClient = null,
            tradesAdapter = FakeTradesAdapter(trades),
            processingDispatcher = dispatcher,
            clock = { now },
        )

        controller.start("BTCUSDT", "1m")
        runCurrent()

        trades.emit(trade(price = 100.0))
        runCurrent()
        assertEquals(1L, controller.state.value.liveCandle?.totalTicks)
        assertEquals(100f, controller.state.value.currentPrice)

        now = 150L
        trades.emit(trade(price = 101.0))
        runCurrent()
        assertEquals(1L, controller.state.value.liveCandle?.totalTicks)
        assertEquals(100f, controller.state.value.currentPrice)

        now = 200L
        trades.emit(trade(price = 102.0))
        runCurrent()
        assertEquals(3L, controller.state.value.liveCandle?.totalTicks)
        assertEquals(102f, controller.state.value.currentPrice)

        controller.dispose()
    }

    private fun trade(price: Double) = Trade(
        id = 1L,
        symbol = "BTCUSDT",
        price = price,
        quantity = 1.0,
        timestamp = 0L,
        isBuyerMaker = false,
        side = TradeSide.BUY,
    )

    private class FakeTradesAdapter(
        private val trades: Flow<Trade>,
    ) : TradesAdapter {
        override fun subscribeToTrades(symbol: String): Flow<Trade> = trades

        override suspend fun getRecentTrades(symbol: String, limit: Int): List<Trade> = emptyList()
    }
}
