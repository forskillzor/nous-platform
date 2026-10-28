/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance

import com.aandios.nous.api.market.model.orderbook.PriceUpdate
import kotlin.test.Test
import kotlin.test.assertEquals

class BinanceDepthBookTest {

    private fun level(price: Double, quantity: Double) = PriceUpdate(price, quantity)

    @Test
    fun `snapshot then aligned diff applies and zero removes a level`() {
        val book = BinanceDepthBook()
        book.reset(
            bids = listOf(level(100.0, 1.0), level(99.0, 2.0)),
            asks = listOf(level(101.0, 1.5)),
            lastUpdateId = 10,
        )

        // Стейл: u <= lastUpdateId.
        assertEquals(
            BinanceDepthBook.ApplyResult.IGNORED,
            book.apply(emptyList(), emptyList(), 5, 10, 4),
        )

        // Первое событие накрывает lastUpdateId + 1 (9..12); pu после
        // снапшота ещё не связан с ним.
        assertEquals(
            BinanceDepthBook.ApplyResult.APPLIED,
            book.apply(
                bids = listOf(level(100.0, 0.0), level(98.0, 3.0)),
                asks = emptyList(),
                firstUpdateId = 9,
                finalUpdateId = 12,
                previousFinalUpdateId = 8,
            ),
        )
        assertEquals(12, book.lastUpdateId)

        val window = book.window(5)
        assertEquals(listOf(99.0, 98.0), window.bids.map { it.price })
        assertEquals(listOf(1.5), window.asks.map { it.quantity })
        assertEquals(listOf(101.0), window.asks.map { it.price })
    }

    @Test
    fun `gap is reported when pu does not match the last applied id`() {
        val book = BinanceDepthBook()
        book.reset(emptyList(), emptyList(), lastUpdateId = 10)
        book.apply(emptyList(), emptyList(), 10, 12, 9)

        assertEquals(
            BinanceDepthBook.ApplyResult.GAP,
            book.apply(emptyList(), emptyList(), 13, 14, 11),
        )
        assertEquals(
            BinanceDepthBook.ApplyResult.APPLIED,
            book.apply(emptyList(), emptyList(), 13, 14, 12),
        )
    }

    @Test
    fun `first event after snapshot must cover lastUpdateId plus one`() {
        val book = BinanceDepthBook()
        book.reset(emptyList(), emptyList(), lastUpdateId = 10)

        assertEquals(
            BinanceDepthBook.ApplyResult.GAP,
            book.apply(emptyList(), emptyList(), 12, 14, 11),
        )
    }

    @Test
    fun `window trims to requested depth and keeps sides sorted`() {
        val book = BinanceDepthBook()
        book.reset(
            bids = (0 until 10).map { level(100.0 - it, 1.0) },
            asks = (0 until 10).map { level(101.0 + it, 1.0) },
            lastUpdateId = 1,
        )

        val window = book.window(3)
        assertEquals(listOf(100.0, 99.0, 98.0), window.bids.map { it.price })
        assertEquals(listOf(101.0, 102.0, 103.0), window.asks.map { it.price })
    }
}
