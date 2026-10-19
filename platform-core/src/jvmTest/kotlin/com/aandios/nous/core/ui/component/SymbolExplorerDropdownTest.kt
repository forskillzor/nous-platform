/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class SymbolExplorerDropdownTest {

    private fun row(
        symbol: String,
        exchange: String = "binance",
        label: String = "BINANCE",
        perpetual: Boolean = true,
        quote: String? = "USDT",
        last: Double? = null,
        change: Double? = null,
        volume: Double? = null,
    ) = SymbolExplorerRow(
        symbol = symbol,
        displaySymbol = symbol + if (perpetual) ".P" else "",
        exchangeId = exchange,
        exchangeLabel = label,
        isPerpetual = perpetual,
        quoteAsset = quote,
        tickSize = 0.01,
        lastPrice = last,
        changePercent24h = change,
        quoteVolume24h = volume,
    )

    @Test
    fun `filters by watchlist perpetuals quote and exchange`() {
        val rows = listOf(
            row("BTCUSDT", volume = 1.0e9),
            row("ETHUSDT", volume = 5.0e8),
            row("SOLUSDT", exchange = "bybit", label = "BYBIT", volume = 2.0e8),
            row("BTCUSDC", quote = "USDC", volume = 3.0e8),
            row("BTCPERP-SPOT", perpetual = false, volume = 1.0e8),
        )

        val result = computeExplorerRows(
            rows = rows,
            activeListSymbols = setOf("BTCUSDT", "SOLUSDT"),
            perpetualsOnly = true,
            quote = "USDT",
            exchangeId = null,
            query = "",
            sort = SymbolExplorerSort.VOLUME,
            descending = true,
        )

        assertEquals(listOf("BTCUSDT", "SOLUSDT"), result.matches.map { it.row.symbol })
        assertNull(result.error)

        val byExchange = computeExplorerRows(
            rows = rows,
            activeListSymbols = null,
            perpetualsOnly = false,
            quote = null,
            exchangeId = "bybit",
            query = "",
            sort = SymbolExplorerSort.VOLUME,
            descending = true,
        )
        assertEquals(listOf("SOLUSDT"), byExchange.matches.map { it.row.symbol })
    }

    @Test
    fun `search matches display symbol and reports highlight range`() {
        val result = computeExplorerRows(
            rows = listOf(row("WBTCUSDT"), row("BTCUSDT"), row("ETHUSDT")),
            activeListSymbols = null,
            perpetualsOnly = true,
            quote = null,
            exchangeId = null,
            query = "BTC",
            sort = SymbolExplorerSort.VOLUME,
            descending = true,
        )

        assertEquals(listOf("BTCUSDT", "WBTCUSDT"), result.matches.map { it.row.symbol })
        assertEquals(0..2, result.matches.first().matchRange)
        assertEquals(1..3, result.matches[1].matchRange)
    }

    @Test
    fun `search matches exchange contract and quote fields without highlight`() {
        val rows = listOf(
            row("SOLUSDT", exchange = "bybit", label = "BYBIT"),
            row("BTCUSDT"),
        )

        val byExchange = computeExplorerRows(
            rows = rows, activeListSymbols = null, perpetualsOnly = true, quote = null,
            exchangeId = null, query = "bybit", sort = SymbolExplorerSort.VOLUME, descending = true,
        )
        assertEquals(listOf("SOLUSDT"), byExchange.matches.map { it.row.symbol })
        assertNull(byExchange.matches.single().matchRange)

        val byPerp = computeExplorerRows(
            rows = rows, activeListSymbols = null, perpetualsOnly = true, quote = null,
            exchangeId = null, query = "perp", sort = SymbolExplorerSort.VOLUME, descending = true,
        )
        assertEquals(2, byPerp.matches.size)
        assertNull(byPerp.matches.first().matchRange)
    }

    @Test
    fun `symbol sort with query ranks prefix before substring`() {
        val result = computeExplorerRows(
            rows = listOf(row("WBTCUSDT"), row("BTCUSDT")),
            activeListSymbols = null,
            perpetualsOnly = true,
            quote = null,
            exchangeId = null,
            query = "BTC",
            sort = SymbolExplorerSort.SYMBOL,
            descending = false,
        )

        assertEquals(listOf("BTCUSDT", "WBTCUSDT"), result.matches.map { it.row.symbol })
        assertEquals(0..2, result.matches[0].matchRange)
        assertEquals(1..3, result.matches[1].matchRange)

        // Направление ↓ инвертирует релевантность (как в Market Watch)
        val reversed = computeExplorerRows(
            rows = listOf(row("WBTCUSDT"), row("BTCUSDT")),
            activeListSymbols = null,
            perpetualsOnly = true,
            quote = null,
            exchangeId = null,
            query = "BTC",
            sort = SymbolExplorerSort.SYMBOL,
            descending = true,
        )
        assertEquals(listOf("WBTCUSDT", "BTCUSDT"), reversed.matches.map { it.row.symbol })
    }

    @Test
    fun `symbol sort with query puts meta matches after symbol matches`() {
        val rows = listOf(
            row("BYBITUSDT"),
            row("SOLUSDT", exchange = "bybit", label = "BYBIT"),
            row("ETHUSDT"),
        )

        val result = computeExplorerRows(
            rows = rows,
            activeListSymbols = null,
            perpetualsOnly = true,
            quote = null,
            exchangeId = null,
            query = "bybit",
            sort = SymbolExplorerSort.SYMBOL,
            descending = false,
        )

        assertEquals(listOf("BYBITUSDT", "SOLUSDT"), result.matches.map { it.row.symbol })
        assertEquals(0..4, result.matches[0].matchRange)
        assertNull(result.matches[1].matchRange)
    }

    @Test
    fun `non-symbol sorts ignore relevance and keep field order`() {
        val rows = listOf(
            row("WBTCUSDT", volume = 5.0e8),
            row("BTCUSDT", volume = 1.0e9),
        )

        val result = computeExplorerRows(
            rows = rows,
            activeListSymbols = null,
            perpetualsOnly = true,
            quote = null,
            exchangeId = null,
            query = "BTC",
            sort = SymbolExplorerSort.VOLUME,
            descending = true,
        )

        assertEquals(listOf("BTCUSDT", "WBTCUSDT"), result.matches.map { it.row.symbol })
    }

    @Test
    fun `invalid regex yields error and no matches`() {
        val result = computeExplorerRows(
            rows = listOf(row("BTCUSDT")), activeListSymbols = null, perpetualsOnly = true,
            quote = null, exchangeId = null, query = "[BTC",
            sort = SymbolExplorerSort.VOLUME, descending = true,
        )

        assertEquals(emptyList(), result.matches)
        assertEquals(true, result.error?.isNotBlank())
    }

    @Test
    fun `sorts by volume descending with nulls last`() {
        val rows = listOf(
            row("AUSDT", volume = null),
            row("BUSDT", volume = 2.0e8),
            row("CUSDT", volume = 5.0e8),
        )

        val sorted = sortExplorerRows(rows, SymbolExplorerSort.VOLUME, descending = true)

        assertEquals(listOf("CUSDT", "BUSDT", "AUSDT"), sorted.map { it.symbol })
    }

    @Test
    fun `symbol sort ascending is alphabetical`() {
        val rows = listOf(row("CUSDT"), row("AUSDT"), row("BUSDT"))

        assertEquals(
            listOf("AUSDT", "BUSDT", "CUSDT"),
            sortExplorerRows(rows, SymbolExplorerSort.SYMBOL, descending = false).map { it.symbol },
        )
        assertEquals(
            listOf("CUSDT", "BUSDT", "AUSDT"),
            sortExplorerRows(rows, SymbolExplorerSort.SYMBOL, descending = true).map { it.symbol },
        )
    }

    @Test
    fun `quote options put USDT first and exchange options sort by label`() {
        val rows = listOf(
            row("BTCUSDT", exchange = "mexc", label = "MEXC"),
            row("BTCUSDC", quote = "USDC", exchange = "binance", label = "BINANCE"),
            row("ETHUSDT", exchange = "binance", label = "BINANCE"),
        )

        assertEquals(listOf("USDT", "USDC"), explorerQuoteOptions(rows))
        assertEquals(
            listOf("binance" to "BINANCE", "mexc" to "MEXC"),
            explorerExchangeOptions(rows),
        )
    }

    @Test
    fun `footer text reflects count and active sort`() {
        assertEquals("48 CONTRACTS · SORTED BY VOLUME", explorerFooterText(48, SymbolExplorerSort.VOLUME))
        assertEquals("12 CONTRACTS · SORTED BY 24H %", explorerFooterText(12, SymbolExplorerSort.CHANGE))
        assertNotNull(explorerFooterText(1, SymbolExplorerSort.SYMBOL))
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
