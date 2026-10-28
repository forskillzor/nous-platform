/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.ui

import com.aandios.nous.api.market.model.trades.Trade
import com.aandios.nous.api.market.model.trading.TradeSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TradesHistoryBufferTest {

    private var nextId = 0L

    private fun trade(quantity: Double) = Trade(
        id = nextId++,
        symbol = "BTCUSDT",
        price = 100.0,
        quantity = quantity,
        timestamp = nextId,
        isBuyerMaker = false,
        side = TradeSide.BUY,
    )

    @Test
    fun `feed is capped while raw keeps older trades`() {
        val buffer = TradesHistoryBuffer(maxRaw = 10, maxFeed = 3, maxFiltered = 5)
        repeat(5) { buffer.add(trade(1.0), filterActive = false) { true } }

        assertEquals(listOf(4L, 3L, 2L), buffer.feedSnapshot().map { it.id })
        assertEquals(5, buffer.rawSize)
    }

    @Test
    fun `filtered retention is applied after filtering and survives feed eviction`() {
        val buffer = TradesHistoryBuffer(maxRaw = 50, maxFeed = 3, maxFiltered = 2)
        val quantities = listOf(1.0, 10.0, 1.0, 20.0, 1.0)
        quantities.forEach { qty ->
            buffer.add(trade(qty), filterActive = true) { it.quantity >= 10.0 }
        }

        assertEquals(3, buffer.feedSnapshot().size)
        // 10.0 уже вытеснена из feed, но остаётся в filtered.
        assertEquals(listOf(20.0, 10.0), buffer.filteredSnapshot().map { it.quantity })
    }

    @Test
    fun `reseed rebuilds filtered from raw not from feed`() {
        val buffer = TradesHistoryBuffer(maxRaw = 50, maxFeed = 2, maxFiltered = 10)
        listOf(1.0, 100.0, 2.0, 3.0).forEach { qty ->
            buffer.add(trade(qty), filterActive = false) { true }
        }
        assertEquals(listOf(3.0, 2.0), buffer.feedSnapshot().map { it.quantity })

        buffer.reseed(filterActive = true) { it.quantity >= 100.0 }

        assertEquals(listOf(100.0), buffer.filteredSnapshot().map { it.quantity })
    }

    @Test
    fun `reseed with All clears filtered but keeps raw`() {
        val buffer = TradesHistoryBuffer(maxRaw = 10, maxFeed = 3, maxFiltered = 5)
        repeat(3) { buffer.add(trade(10.0), filterActive = true) { true } }
        assertTrue(buffer.filteredSnapshot().isNotEmpty())

        buffer.reseed(filterActive = false) { true }

        assertTrue(buffer.filteredSnapshot().isEmpty())
        assertEquals(3, buffer.rawSize)
    }

    @Test
    fun `clear resets all levels`() {
        val buffer = TradesHistoryBuffer(maxRaw = 10, maxFeed = 3, maxFiltered = 5)
        repeat(3) { buffer.add(trade(10.0), filterActive = true) { true } }

        buffer.clear()

        assertEquals(0, buffer.rawSize)
        assertTrue(buffer.feedSnapshot().isEmpty())
        assertTrue(buffer.filteredSnapshot().isEmpty())
    }
}
