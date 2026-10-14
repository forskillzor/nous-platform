/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.core.ui.theme.TerminalThemes
import kotlin.test.Test
import kotlin.test.assertEquals

class ChartConfigThemeTest {

    @Test
    fun `withTheme applies semantic colors and preserves chart options`() {
        val source = DefaultChartConfig.copy(showGrid = false, maxZoom = 8f)
        val tokens = TerminalThemes.Night.tokens

        val result = source.withTheme(tokens)

        assertEquals(tokens.colors.chartBackground, result.backgroundColor)
        assertEquals(tokens.colors.gridLine, result.gridColor)
        assertEquals(tokens.colors.gridLineMajor, result.majorGridColor)
        assertEquals(tokens.colors.axisText, result.axisTextColor)
        assertEquals(tokens.colors.bullish, result.currentPriceColor)
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
