/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SearchMatchingTest {

    private data class Item(
        val primary: String,
        val meta: List<String> = emptyList(),
    )

    private fun fields(item: Item) = SearchFields(item.primary, item.meta)

    @Test
    fun blankQueryReturnsEveryItemWithoutMatches() {
        val items = listOf(Item("BTCUSDT"), Item("ETHUSDT"))

        val outcome = searchItems(items, "   ", ::fields)

        assertNull(outcome.error)
        assertEquals(items, outcome.hits.map { it.item })
        assertTrue(outcome.hits.all { it.match == null })
    }

    @Test
    fun primaryMatchesRankExactPrefixSubstringThenMeta() {
        val items = listOf(
            Item("ETHUSDT", listOf("BTC")),
            Item("WBTCUSDT"),
            Item("BTCUSDT"),
            Item("BTC"),
        )

        val outcome = searchItems(items, "btc", ::fields)
        val ordered = outcome.hits.sortedWith(searchHitComparator<Item>())

        assertEquals(
            listOf("BTC", "BTCUSDT", "WBTCUSDT", "ETHUSDT"),
            ordered.map { it.item.primary },
        )
        assertEquals(
            listOf(
                MatchQuality.EXACT,
                MatchQuality.PREFIX,
                MatchQuality.SUBSTRING,
                MatchQuality.META,
            ),
            ordered.map { it.match?.quality },
        )
        assertEquals(listOf(0..2, 0..2, 1..3, null), ordered.map { it.match?.range })
    }

    @Test
    fun regexQueryHighlightsOnlyMatchedFragment() {
        val outcome = searchItems(listOf(Item("Bollinger Bands")), "bands$", ::fields)

        assertEquals(1, outcome.hits.size)
        assertEquals(10..14, outcome.hits.single().match?.range)
        assertEquals(MatchQuality.SUBSTRING, outcome.hits.single().match?.quality)
    }

    @Test
    fun emptyRegexMatchHasNoHighlightRange() {
        val outcome = searchItems(listOf(Item("BTCUSDT")), "^", ::fields)
        val match = outcome.hits.single().match

        assertEquals(MatchQuality.EMPTY, match?.quality)
        assertNull(match?.range)
        assertEquals(Int.MAX_VALUE, match?.position)
        assertEquals(Int.MAX_VALUE, match?.matchLength)
    }

    @Test
    fun invalidRegexReturnsErrorAndNoHits() {
        val outcome = searchItems(listOf(Item("BTCUSDT")), "[", ::fields)

        assertTrue(outcome.hits.isEmpty())
        assertTrue(!outcome.error.isNullOrBlank())
    }

    @Test
    fun precompiledRegexOverloadUsesTheSameMatchingRules() {
        val regex = (compileSearchRegex("btc") as SearchRegexResult.Ok).regex

        val hits = searchItems(listOf(Item("BTCUSDT"), Item("ETHUSDT")), regex, ::fields)

        assertEquals(listOf("BTCUSDT"), hits.map { it.item.primary })
        assertEquals(0..2, hits.single().match?.range)
    }
}
