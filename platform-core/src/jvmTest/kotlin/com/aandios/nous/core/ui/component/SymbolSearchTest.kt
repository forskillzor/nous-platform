/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class SymbolSearchTest {
    @Test
    fun `ranks exact and prefix matches and returns the matched range`() {
        val result = searchSymbols(
            listOf("WBTCUSDT", "BTCUSDC", "BTCUSDT", "ETHUSDT"),
            "BTCUSDT|BTC",
        )

        assertEquals(listOf("BTCUSDT", "BTCUSDC", "WBTCUSDT"), result.matches.map { it.symbol })
        assertEquals(0..6, result.matches.first().matchRange)
        assertEquals(0..2, result.matches[1].matchRange)
        assertEquals(1..7, result.matches[2].matchRange)
    }

    @Test
    fun `reports invalid regex instead of silently treating it as a literal`() {
        val result = searchSymbols(listOf("BTCUSDT", "[BTCUSDT"), "[BTC")

        assertEquals(emptyList(), result.matches)
        assertEquals(true, result.error?.isNotBlank())
    }

    @Test
    fun `matches regex case insensitively`() {
        val result = searchSymbols(listOf("BTCUSDT", "ETHUSDT"), "btc(usdt)")

        assertEquals("BTCUSDT", result.matches.single().symbol)
        assertEquals(0..6, result.matches.single().matchRange)
    }

    @Test
    fun `highlight annotation covers only the matched substring`() {
        val accent = Color(0xFFF28C18)
        val text = highlightedSymbol("WBTCUSDT", 1..3, accent)

        assertEquals("WBTCUSDT", text.text)
        val span = assertNotNull(text.spanStyles.singleOrNull())
        assertEquals(1, span.start)
        assertEquals(4, span.end)
        // Подсветка совпадения идёт цветом темы, а не хардкодом.
        assertEquals(accent, span.item.color)
    }
}
