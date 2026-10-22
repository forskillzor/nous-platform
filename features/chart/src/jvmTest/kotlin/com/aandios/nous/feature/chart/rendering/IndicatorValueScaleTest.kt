/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.rendering

import androidx.compose.ui.geometry.Rect
import com.aandios.nous.feature.chart.ui.chart.indicatorPaneRange
import com.aandios.nous.research.indicator.IndicatorOutput
import com.aandios.nous.research.indicator.IndicatorType
import com.aandios.nous.research.indicator.IndicatorValueRange
import kotlin.test.Test
import kotlin.test.assertEquals

class IndicatorValueScaleTest {

    @Test
    fun `indicator value maps from pane y`() {
        val area = Rect(left = 0f, top = 10f, right = 100f, bottom = 110f)
        val rangeMin = -4f
        val rangeMax = 4f

        assertEquals(4f, indicatorValueAtY(area.top, area, rangeMin, rangeMax))
        assertEquals(0f, indicatorValueAtY(area.top + area.height / 2f, area, rangeMin, rangeMax))
        assertEquals(-4f, indicatorValueAtY(area.bottom, area, rangeMin, rangeMax))
        assertEquals(area.top + area.height / 2f, indicatorValueToY(0f, area, rangeMin, rangeMax))
    }

    @Test
    fun `indicator value formatting adapts to the pane range`() {
        assertEquals("75", formatIndicatorValue(75f, 0f, 100f))
        assertEquals("0.25", formatIndicatorValue(0.25f, -1f, 1f))
        assertEquals("-1.00", formatIndicatorValue(-1f, -1f, 1f))
        assertEquals("0.00123", formatIndicatorValue(0.00123f, 0f, 0.001f))
        assertEquals("0", formatIndicatorValue(0f, 1_000_000f, 2_000_000f))
        assertEquals("1.50M", formatIndicatorValue(1_500_000f, 1_000_000f, 2_000_000f))
    }

    @Test
    fun `indicator pane range merges output ranges`() {
        val outputs = listOf(
            IndicatorOutput(
                instanceId = "sma",
                type = IndicatorType.SMA,
                lines = emptyList(),
                range = IndicatorValueRange(-2f, 4f),
            ),
            IndicatorOutput(
                instanceId = "ema",
                type = IndicatorType.EMA,
                lines = emptyList(),
                range = IndicatorValueRange(1f, 8f),
            ),
        )

        assertEquals(-2f to 8f, indicatorPaneRange(outputs))
        assertEquals(0f to 1f, indicatorPaneRange(emptyList()))
    }
}
