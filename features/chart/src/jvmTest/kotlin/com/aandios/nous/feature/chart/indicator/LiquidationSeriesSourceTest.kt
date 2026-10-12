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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LiquidationSeriesSourceTest {
    @Test
    @OptIn(ExperimentalCoroutinesApi::class)
    fun `unsupported history still allows live liquidation updates`() = runTest {
        val liveOrder = LiquidationOrder(
            symbol = "BTCUSDT",
            price = 62_000.0,
            quantity = 0.5,
            timestamp = 1_720_000_000_000L,
            side = TradeSide.BUY,
        )
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
}
