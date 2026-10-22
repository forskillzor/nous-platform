/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.research.indicator.IndicatorDefinitions
import com.aandios.nous.research.indicator.IndicatorParamSpec
import com.aandios.nous.research.indicator.IndicatorType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class IndicatorLabelsTest {

    @Test
    fun titlesIncludeDefaultParameters() {
        val macd = IndicatorDefinitions.create(IndicatorType.MACD, "macd")
        val bollinger = IndicatorDefinitions.create(IndicatorType.BOLLINGER_BANDS, "bb")
        val stochastic = IndicatorDefinitions.create(IndicatorType.STOCHASTIC, "stoch")
        val sma = IndicatorDefinitions.create(IndicatorType.SMA, "sma")

        assertEquals("MACD 12/26/9", indicatorInstanceTitle(macd))
        assertEquals("Bollinger Bands 20 · 2.0", indicatorInstanceTitle(bollinger))
        assertEquals("Stochastic 14/3", indicatorInstanceTitle(stochastic))
        assertEquals("SMA 20", indicatorInstanceTitle(sma))
    }

    @Test
    fun paneTitleJoinsIndicatorTitles() {
        val pane = IndicatorDefinitions.defaultState().panes.single()

        assertEquals("Liquidations", indicatorPaneTitle(pane))
    }

    @Test
    fun integerParametersStepByOneAndClamp() {
        val period = IndicatorParamSpec(
            key = "period",
            label = "Period",
            defaultValue = 20.0,
            minValue = 2.0,
            maxValue = 50.0,
        )

        assertEquals(21.0, stepIndicatorParam(20.0, period, 1))
        assertEquals(19.0, stepIndicatorParam(20.0, period, -1))
        assertEquals(50.0, stepIndicatorParam(50.0, period, 1))
        assertEquals(2.0, stepIndicatorParam(2.0, period, -1))
    }

    @Test
    fun decimalParametersStepByTheirStep() {
        val deviation = IndicatorParamSpec(
            key = "stdDev",
            label = "Std dev",
            defaultValue = 2.0,
            minValue = 0.1,
            maxValue = 5.0,
            step = 0.1,
            integer = false,
        )

        assertEquals(2.1, stepIndicatorParam(2.0, deviation, 1))
        assertEquals(1.9, stepIndicatorParam(2.0, deviation, -1))
        assertEquals(2.0, stepIndicatorParam(2.03, deviation, 0))
    }

    @Test
    fun manualParametersParseClampAndRejectInvalidInput() {
        val period = IndicatorParamSpec(
            key = "period",
            label = "Period",
            defaultValue = 20.0,
            minValue = 2.0,
            maxValue = 50.0,
        )
        val deviation = IndicatorParamSpec(
            key = "stdDev",
            label = "Std dev",
            defaultValue = 2.0,
            minValue = 0.1,
            maxValue = 5.0,
            step = 0.1,
            integer = false,
        )

        assertEquals(30.0, parseIndicatorParam("30", period))
        assertEquals(2.0, parseIndicatorParam("0", period))
        assertEquals(50.0, parseIndicatorParam("999", period))
        assertEquals(2.4, parseIndicatorParam("2,35", deviation))
        assertNull(parseIndicatorParam("", period))
        assertNull(parseIndicatorParam("abc", period))
    }
}
