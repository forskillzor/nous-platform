/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class IndicatorOutputBuilderTest {

    private val period = IndicatorParamSpec(
        key = "period",
        label = "Period",
        defaultValue = 20.0,
        minValue = 2.0,
        maxValue = 50.0,
    )

    @Test
    fun buildsLineMetadataLabelsAndColorOverride() {
        val definition = definition(
            levels = listOf(IndicatorLevel(30f, "30")),
            zeroLine = true,
            bandFill = IndicatorBandFill("line", "line"),
        )
        val instance = IndicatorInstance(
            id = "sma",
            type = IndicatorType.SMA,
            params = mapOf("period" to 20.0),
            lineColors = mapOf("line" to 0xFF00FF00),
        )

        val output = IndicatorOutputBuilder.build(
            instance = instance,
            definition = definition,
            computation = IndicatorComputation(mapOf("line" to floatArrayOf(1f, 2f, 3f))),
        )

        val line = output.lines.single()
        assertEquals("SMA 20", line.label)
        assertEquals(0xFF00FF00, line.colorArgb)
        assertEquals(IndicatorColorRole.SECONDARY, line.colorRole)
        assertTrue(output.zeroLine)
        assertEquals(listOf(30f), output.levels.map { it.value })
        assertEquals(IndicatorBandFill("line", "line"), output.bandFill)
    }

    @Test
    fun autoRangeIncludesZeroWhenRequested() {
        val output = build(values = floatArrayOf(2f, 3f), includeZero = true)

        assertEquals(0f, output.range.min)
        assertEquals(3f, output.range.max)
    }

    @Test
    fun autoRangeKeepsPositiveMinimumWithoutZero() {
        val output = build(values = floatArrayOf(2f, 3f), includeZero = false)

        assertEquals(2f, output.range.min)
        assertEquals(3f, output.range.max)
    }

    @Test
    fun fixedRangeWinsOverAutoRange() {
        val output = build(
            values = floatArrayOf(2f, 3f),
            fixedRange = IndicatorValueRange(0f, 100f, fixed = true),
        )

        assertEquals(IndicatorValueRange(0f, 100f, fixed = true), output.range)
    }

    @Test
    fun computationRangeWinsOverDefinition() {
        val output = build(
            values = floatArrayOf(2f, 3f),
            fixedRange = IndicatorValueRange(0f, 100f, fixed = true),
            range = IndicatorValueRange(-5f, 5f),
        )

        assertEquals(IndicatorValueRange(-5f, 5f), output.range)
    }

    @Test
    fun histogramNaNsBecomeZero() {
        val output = build(
            values = floatArrayOf(Float.NaN, 2f),
            style = IndicatorLineStyle.HISTOGRAM,
        )

        assertEquals(0f, output.lines.single().values[0])
        assertEquals(2f, output.lines.single().values[1])
    }

    @Test
    fun lineNaNsStayNaN() {
        val output = build(
            values = floatArrayOf(Float.NaN, 2f),
            style = IndicatorLineStyle.LINE,
        )

        assertTrue(output.lines.single().values[0].isNaN())
    }

    @Test
    fun rejectsMismatchedLineKeys() {
        assertFailsWith<IllegalArgumentException> {
            IndicatorOutputBuilder.build(
                instance = IndicatorInstance(id = "sma", type = IndicatorType.SMA),
                definition = definition(),
                computation = IndicatorComputation(emptyMap()),
            )
        }
    }

    @Test
    fun rejectsTypeMismatch() {
        assertFailsWith<IllegalArgumentException> {
            IndicatorOutputBuilder.build(
                instance = IndicatorInstance(id = "ema", type = IndicatorType.EMA),
                definition = definition(),
                computation = IndicatorComputation(mapOf("line" to floatArrayOf(1f))),
            )
        }
    }

    @Test
    fun nonFiniteValuesFallBackToUnitRange() {
        val output = build(values = floatArrayOf(Float.NaN))

        assertEquals(IndicatorValueRange(0f, 1f), output.range)
    }

    private fun build(
        values: FloatArray,
        style: IndicatorLineStyle = IndicatorLineStyle.LINE,
        includeZero: Boolean = false,
        fixedRange: IndicatorValueRange? = null,
        range: IndicatorValueRange? = null,
    ): IndicatorOutput = IndicatorOutputBuilder.build(
        instance = IndicatorInstance(
            id = "sma",
            type = IndicatorType.SMA,
            params = mapOf("period" to 20.0),
        ),
        definition = definition(
            style = style,
            includeZero = includeZero,
            fixedRange = fixedRange,
        ),
        computation = IndicatorComputation(mapOf("line" to values), range = range),
    )

    private fun definition(
        style: IndicatorLineStyle = IndicatorLineStyle.LINE,
        includeZero: Boolean = false,
        fixedRange: IndicatorValueRange? = null,
        levels: List<IndicatorLevel> = emptyList(),
        zeroLine: Boolean = false,
        bandFill: IndicatorBandFill? = null,
    ): IndicatorDefinition = IndicatorDefinition(
        type = IndicatorType.SMA,
        label = "SMA",
        placement = IndicatorPlacement.OVERLAY,
        parameters = listOf(period),
        lines = listOf(
            IndicatorLineSpec(
                id = "line",
                label = "Line",
                labelTemplate = "SMA {period}",
                style = style,
                colorRole = IndicatorColorRole.SECONDARY,
                histogramColor = HistogramColorMode.CANDLE,
                colorLabel = "Custom",
            ),
        ),
        levels = levels,
        zeroLine = zeroLine,
        bandFill = bandFill,
        fixedRange = fixedRange,
        includeZero = includeZero,
        titleTemplate = "SMA {period}",
    )
}
