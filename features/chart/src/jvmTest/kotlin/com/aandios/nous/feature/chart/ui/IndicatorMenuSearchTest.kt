/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.research.indicator.IndicatorRegistry
import com.aandios.nous.research.indicator.IndicatorType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class IndicatorMenuSearchTest {

    private val definitions = IndicatorRegistry.all.map { it.definition }

    @Test
    fun blankQueryKeepsEveryDefinition() {
        assertEquals(definitions, searchIndicatorDefinitions(definitions, "").matches.map { it.definition })
        assertEquals(definitions, searchIndicatorDefinitions(definitions, "   ").matches.map { it.definition })
        assertTrue(searchIndicatorDefinitions(definitions, "").matches.all { it.matchRange == null })
    }

    @Test
    fun searchMatchesLabelsCaseInsensitively() {
        val macd = searchIndicatorDefinitions(definitions, "mAcD").matches
        val bollinger = searchIndicatorDefinitions(definitions, "bollinger").matches

        assertEquals(listOf(IndicatorType.MACD), macd.map { it.definition.type })
        assertEquals(listOf(IndicatorType.BOLLINGER_BANDS), bollinger.map { it.definition.type })
        assertEquals(0..3, macd.single().matchRange)
        assertEquals(0..8, bollinger.single().matchRange)
    }

    @Test
    fun queryIsTrimmedBeforeMatching() {
        val sma = searchIndicatorDefinitions(definitions, "  SMa  ").matches

        assertTrue(sma.any { it.definition.type == IndicatorType.SMA })
        assertTrue(sma.all { it.definition.label.contains("sma", ignoreCase = true) })
        assertTrue(sma.all { it.matchRange != null })
    }

    @Test
    fun regexQueryHighlightsOnlyMatchedFragment() {
        val bands = searchIndicatorDefinitions(definitions, "bands$").matches

        assertEquals(listOf(IndicatorType.BOLLINGER_BANDS), bands.map { it.definition.type })
        assertEquals(10..14, bands.single().matchRange)
    }

    @Test
    fun invalidRegexReturnsErrorWithoutLiteralFallback() {
        val result = searchIndicatorDefinitions(definitions, "[")

        assertTrue(result.matches.isEmpty())
        assertTrue(!result.error.isNullOrBlank())
    }

    @Test
    fun unmatchedQueryReturnsEmptyList() {
        val result = searchIndicatorDefinitions(definitions, "no-such-indicator")

        assertTrue(result.matches.isEmpty())
        assertNull(result.error)
    }
}
