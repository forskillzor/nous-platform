/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

import kotlin.test.Test
import kotlin.test.assertEquals

class MexcDepthBookTest {

    @Test
    fun `reset builds window and sorts sides`() {
        val book = MexcDepthBook()
        book.reset(
            asks = listOf(listOf(101.0, 5.0), listOf(100.5, 2.0)),
            bids = listOf(listOf(99.5, 1.0), listOf(100.0, 3.0)),
            version = 10,
        )

        val window = book.window(20)
        assertEquals(listOf(100.0, 99.5), window.bids.map { it.price })
        assertEquals(listOf(100.5, 101.0), window.asks.map { it.price })
        assertEquals(3.0, window.bids[0].quantity)
        assertEquals(10L, book.version)
    }

    @Test
    fun `apply uses absolute quantities and removes zero levels`() {
        val book = MexcDepthBook()
        book.reset(
            asks = emptyList(),
            bids = listOf(listOf(100.0, 3.0), listOf(99.0, 1.0)),
            version = 1,
        )

        // Количества абсолютные: 100.0 снят (0), 99.0 заменён на 7
        assertEquals(
            MexcDepthBook.ApplyResult.APPLIED,
            book.apply(
                asks = listOf(listOf(100.5, 4.0)),
                bids = listOf(listOf(100.0, 0.0), listOf(99.0, 7.0)),
                version = 2,
            ),
        )

        val window = book.window(20)
        assertEquals(listOf(99.0), window.bids.map { it.price })
        assertEquals(7.0, window.bids[0].quantity)
        assertEquals(listOf(100.5), window.asks.map { it.price })
        assertEquals(2L, book.version)
    }

    @Test
    fun `ignores stale versions and detects gaps`() {
        val book = MexcDepthBook()
        book.reset(asks = emptyList(), bids = emptyList(), version = 5)

        assertEquals(MexcDepthBook.ApplyResult.IGNORED, book.apply(emptyList(), emptyList(), 5))
        assertEquals(MexcDepthBook.ApplyResult.IGNORED, book.apply(emptyList(), emptyList(), 4))
        // Разрыв последовательности: 7 вместо 6 — нужен ресинк
        assertEquals(MexcDepthBook.ApplyResult.GAP, book.apply(emptyList(), emptyList(), 7))
        assertEquals(5L, book.version)
    }

    @Test
    fun `window limits to requested depth and keeps sides apart`() {
        val book = MexcDepthBook()
        book.reset(
            asks = (1..30).map { listOf(100.0 + it, it.toDouble()) },
            bids = (1..30).map { listOf(100.0 - it, it.toDouble()) },
            version = 1,
        )

        val window = book.window(5)
        assertEquals(5, window.asks.size)
        assertEquals(5, window.bids.size)
        assertEquals(listOf(101.0, 102.0, 103.0, 104.0, 105.0), window.asks.map { it.price })
        assertEquals(listOf(99.0, 98.0, 97.0, 96.0, 95.0), window.bids.map { it.price })
    }
}
