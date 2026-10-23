/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.research.indicator.IndicatorRegistry
import com.aandios.nous.research.indicator.IndicatorType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndicatorMenuSearchTest {

    private val definitions = IndicatorRegistry.all.map { it.definition }

    @Test
    fun blankQueryKeepsEveryDefinition() {
        assertEquals(definitions, filterIndicatorDefinitions(definitions, ""))
        assertEquals(definitions, filterIndicatorDefinitions(definitions, "   "))
    }

    @Test
    fun searchMatchesLabelsCaseInsensitively() {
        val macd = filterIndicatorDefinitions(definitions, "mAcD")
        val bollinger = filterIndicatorDefinitions(definitions, "bollinger")

        assertEquals(listOf(IndicatorType.MACD), macd.map { it.type })
        assertEquals(listOf(IndicatorType.BOLLINGER_BANDS), bollinger.map { it.type })
    }

    @Test
    fun queryIsTrimmedBeforeMatching() {
        val sma = filterIndicatorDefinitions(definitions, "  SMa  ")

        assertTrue(sma.any { it.type == IndicatorType.SMA })
        assertTrue(sma.all { it.label.contains("sma", ignoreCase = true) })
    }

    @Test
    fun unmatchedQueryReturnsEmptyList() {
        assertEquals(emptyList(), filterIndicatorDefinitions(definitions, "no-such-indicator"))
    }
}
