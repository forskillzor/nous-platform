/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import androidx.compose.ui.graphics.Color
import com.aandios.nous.core.ui.theme.TerminalThemes
import kotlin.test.Test
import kotlin.test.assertEquals

class ChartConfigThemeTest {

    @Test
    fun `Nous theme uses screenshot palette and keeps chart accents distinct from market direction`() {
        val tokens = TerminalThemes.Nous.tokens

        val result = DefaultChartConfig.withTheme(tokens)

        assertEquals(TerminalThemes.Nous, TerminalThemes.available.first())
        assertEquals(TerminalThemes.Nous.colorScheme.primary, tokens.colors.accent)
        assertEquals(TerminalThemes.Nous.colorScheme.primary, result.currentPriceColor)
        assertEquals(Color(0xFF168B72), tokens.colors.bullish)
        assertEquals(Color(0xFFD45155), tokens.colors.bearish)
        assertEquals(Color(0xFF0E1724), result.backgroundColor)
        assertEquals(Color(0xFF223147), result.gridColor)
    }

    @Test
    fun `withTheme applies semantic colors and preserves chart options`() {
        val source = DefaultChartConfig.copy(showGrid = false, maxZoom = 8f)
        val tokens = TerminalThemes.Night.tokens

        val result = source.withTheme(tokens)

        assertEquals(tokens.colors.chartBackground, result.backgroundColor)
        assertEquals(tokens.colors.gridLine, result.gridColor)
        assertEquals(tokens.colors.gridLineMajor, result.majorGridColor)
        assertEquals(tokens.colors.axisText, result.axisTextColor)
        assertEquals(tokens.colors.accent, result.currentPriceColor)
        assertEquals(tokens.colors.crosshair, result.crosshairColor)
        assertEquals(tokens.colors.accent, result.accentColor)
        assertEquals(tokens.colors.pnlPlate, result.pnlPlateColor)
        assertEquals(tokens.colors.bullish, result.candleStyle.bullishColor)
        assertEquals(tokens.colors.bearish, result.candleStyle.bearishColor)
        assertEquals(tokens.colors.volumeBullish, result.footprintConfig.bidColor)
        assertEquals(tokens.colors.volumeBearish, result.footprintConfig.askColor)
        assertEquals(false, result.showGrid)
        assertEquals(8f, result.maxZoom)
    }
}
