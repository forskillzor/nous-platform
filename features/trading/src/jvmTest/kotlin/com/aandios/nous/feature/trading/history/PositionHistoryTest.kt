/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trading.history

import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.trading.TradeFill
import com.aandios.nous.api.market.model.trading.TradeSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PositionHistoryTest {

    private fun fill(
        id: String,
        side: OrderSide,
        price: Double,
        quantity: Double,
        positionSide: TradeSide,
        closedQuantity: Double = 0.0,
        fee: Double = 0.0,
        pnl: Double = 0.0,
        timestamp: Long,
        symbol: String = "SOL",
    ) = TradeFill(
        id = id,
        symbol = symbol,
        side = side,
        price = price,
        quantity = quantity,
        fee = fee,
        pnl = pnl,
        timestamp = timestamp,
        positionSide = positionSide,
        closedQuantity = closedQuantity,
    )

    @Test
    fun `aggregates two entries and one exit into one closed position`() {
        val rows = buildPositionHistory(
            listOf(
                fill("exit", OrderSide.SELL, 7622.0, 0.024, TradeSide.BUY, 0.024, 0.016463, 0.2544, 3),
                fill("entry-1", OrderSide.BUY, 7606.5, 0.012, TradeSide.BUY, fee = 0.002738, timestamp = 1),
                fill("entry-2", OrderSide.BUY, 7616.3, 0.012, TradeSide.BUY, fee = 0.008225, timestamp = 2),
            ),
        )

        val row = rows.single()
        assertEquals("SOL", row.symbol)
        assertEquals(TradeSide.BUY, row.side)
        assertEquals(0.024, row.quantity, 1e-12)
        assertEquals(7611.4, row.avgEntryPrice, 1e-12)
        assertEquals(7622.0, row.avgExitPrice, 1e-12)
        assertEquals(0.027426, row.fee, 1e-12)
        assertEquals(0.2544, row.pnl, 1e-12)
        assertEquals(1, row.openedAt)
        assertEquals(3, row.closedAt)
    }

    @Test
    fun `partial exits are combined until the position is flat`() {
        val rows = buildPositionHistory(
            listOf(
                fill("entry", OrderSide.BUY, 100.0, 1.0, TradeSide.BUY, fee = 0.1, timestamp = 1),
                fill("exit-1", OrderSide.SELL, 110.0, 0.4, TradeSide.BUY, 0.4, 0.04, 4.0, 2),
                fill("exit-2", OrderSide.SELL, 120.0, 0.6, TradeSide.BUY, 0.6, 0.06, 12.0, 3),
            ),
        )

        val row = rows.single()
        assertEquals(1.0, row.quantity, 1e-12)
        assertEquals(100.0, row.avgEntryPrice, 1e-12)
        assertEquals(116.0, row.avgExitPrice, 1e-12)
        assertEquals(0.2, row.fee, 1e-12)
        assertEquals(16.0, row.pnl, 1e-12)
    }

    @Test
    fun `open positions and closes without entries are omitted`() {
        val rows = buildPositionHistory(
            listOf(
                fill("entry", OrderSide.BUY, 100.0, 1.0, TradeSide.BUY, timestamp = 1),
                fill("partial", OrderSide.SELL, 110.0, 0.4, TradeSide.BUY, 0.4, timestamp = 2),
                fill("orphan", OrderSide.SELL, 120.0, 1.0, TradeSide.SELL, 1.0, timestamp = 3),
            ),
        )

        assertTrue(rows.isEmpty())
    }

    @Test
    fun `closed positions are ordered newest first`() {
        val rows = buildPositionHistory(
            listOf(
                fill("entry-a", OrderSide.BUY, 100.0, 1.0, TradeSide.BUY, timestamp = 1, symbol = "AAA"),
                fill("exit-a", OrderSide.SELL, 110.0, 1.0, TradeSide.BUY, 1.0, timestamp = 2, symbol = "AAA"),
                fill("entry-b", OrderSide.BUY, 200.0, 2.0, TradeSide.BUY, timestamp = 3, symbol = "BBB"),
                fill("exit-b", OrderSide.SELL, 210.0, 2.0, TradeSide.BUY, 2.0, timestamp = 4, symbol = "BBB"),
            ),
        )

        assertEquals(listOf("BBB", "AAA"), rows.map { it.symbol })
    }

    @Test
    fun `split close for bybit opens the remainder on the opposite side`() {
        val rows = buildPositionHistory(
            listOf(
                fill("entry", OrderSide.BUY, 100.0, 1.0, TradeSide.BUY, fee = 0.1, timestamp = 1),
                fill(
                    "flip",
                    OrderSide.SELL,
                    110.0,
                    1.2,
                    TradeSide.BUY,
                    closedQuantity = 1.0,
                    fee = 0.12,
                    pnl = 10.0,
                    timestamp = 2,
                ),
            ),
        )

        val row = rows.single()
        assertEquals(TradeSide.BUY, row.side)
        assertEquals(1.0, row.quantity, 1e-12)
        assertEquals(100.0, row.avgEntryPrice, 1e-12)
        assertEquals(110.0, row.avgExitPrice, 1e-12)
        assertEquals(0.2, row.fee, 1e-12)
        assertEquals(10.0, row.pnl, 1e-12)
    }
}
