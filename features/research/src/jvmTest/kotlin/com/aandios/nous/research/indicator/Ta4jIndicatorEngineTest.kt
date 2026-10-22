/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

import com.aandios.nous.api.market.model.Candle
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Ta4jIndicatorEngineTest {

    @Test
    fun smaMatchesSimpleAverage() {
        val values = compute(IndicatorType.SMA, mapOf("period" to 3.0), candles(10))
            .lines.single().values

        assertEquals(100.5f, values[2], 0.01f)
        assertEquals(101.0f, values[3], 0.01f)
    }

    @Test
    fun emaWarmsUpAndStaysFinite() {
        val values = compute(IndicatorType.EMA, mapOf("period" to 10.0), candles(80))
            .lines.single().values

        assertFalse(values.last().isNaN())
        assertTrue(values.last() > 100f)
    }

    @Test
    fun bollingerBandsAreOrdered() {
        val output = compute(IndicatorType.BOLLINGER_BANDS, mapOf("period" to 20.0), candles(80))
        val upper = output.lines.first { it.id == "upper" }.values
        val middle = output.lines.first { it.id == "middle" }.values
        val lower = output.lines.first { it.id == "lower" }.values

        assertEquals(3, output.lines.size)
        assertEquals(IndicatorBandFill("upper", "lower"), output.bandFill)
        assertTrue(upper[40] > middle[40])
        assertTrue(middle[40] > lower[40])
    }

    @Test
    fun vwapTracksVolumeWeightedPrice() {
        val values = compute(IndicatorType.VWAP, mapOf("period" to 20.0), candles(80))
            .lines.single().values

        assertFalse(values.last().isNaN())
        assertTrue(values.last() > 100f)
    }

    @Test
    fun keltnerChannelsAreOrdered() {
        val output = compute(IndicatorType.KELTNER_CHANNELS, mapOf("period" to 20.0), candles(80))
        val upper = output.lines.first { it.id == "upper" }.values
        val middle = output.lines.first { it.id == "middle" }.values
        val lower = output.lines.first { it.id == "lower" }.values

        assertTrue(upper[50] > middle[50])
        assertTrue(middle[50] > lower[50])
        assertEquals(IndicatorBandFill("upper", "lower", IndicatorColorRole.SECONDARY), output.bandFill)
    }

    @Test
    fun rsiIsHighForMonotonicRise() {
        val output = compute(IndicatorType.RSI, mapOf("period" to 14.0), candles(80))
        val values = output.lines.single().values

        assertTrue(values.last() > 99f)
        assertEquals(IndicatorValueRange(0f, 100f, fixed = true), output.range)
        assertEquals(listOf(30f, 70f), output.levels.map { it.value })
    }

    @Test
    fun macdHasThreeLinesAndSymmetricRange() {
        val output = compute(IndicatorType.MACD, candles = candles(120))

        assertEquals(3, output.lines.size)
        assertTrue(output.zeroLine)
        assertTrue(output.range.min < 0f)
        assertTrue(output.range.max > 0f)
        assertFalse(output.lines.first { it.id == "histogram" }.values.last().isNaN())
    }

    @Test
    fun atrIsPositiveAndIncludesZero() {
        val output = compute(IndicatorType.ATR, mapOf("period" to 14.0), candles(80))
        val values = output.lines.single().values

        assertTrue(values.last() > 0f)
        assertTrue(output.range.min <= 0f)
    }

    @Test
    fun stochasticHonorsKAndDPeriods() {
        val output = compute(
            IndicatorType.STOCHASTIC,
            mapOf("kPeriod" to 14.0, "dPeriod" to 3.0),
            candles(80),
        )

        val k = output.lines.first { it.id == "k" }.values
        val d = output.lines.first { it.id == "d" }.values

        assertFalse(k.last().isNaN())
        assertFalse(d.last().isNaN())
        assertTrue(k.last() in 0f..100f)
        assertTrue(d.last() in 0f..100f)
        assertEquals(IndicatorValueRange(0f, 100f, fixed = true), output.range)
    }

    @Test
    fun volumeUsesCandleVolumes() {
        val source = candles(40)
        val output = compute(IndicatorType.VOLUME, mapOf("period" to 5.0), source)
        val volume = output.lines.first { it.id == "volume" }

        assertEquals(source.first().volume, volume.values.first(), 0.001f)
        assertEquals(IndicatorColorRole.NEUTRAL, volume.colorRole)
        assertEquals(IndicatorLineStyle.HISTOGRAM, volume.style)
        assertEquals(HistogramColorMode.CANDLE, volume.histogramColor)
        assertFalse(output.lines.first { it.id == "volume_ma" }.values.last().isNaN())
    }

    @Test
    fun adxStaysWithinFixedRange() {
        val output = compute(IndicatorType.ADX, mapOf("period" to 14.0), candles(80))
        val adx = output.lines.first { it.id == "adx" }.values.last()

        assertEquals(3, output.lines.size)
        assertFalse(adx.isNaN())
        assertTrue(adx in 0f..100f)
        assertEquals(listOf(25f), output.levels.map { it.value })
    }

    @Test
    fun definitionsExposeEveryTypeAndDefaults() {
        assertEquals(
            IndicatorType.entries.toSet(),
            IndicatorDefinitions.all.map { it.type }.toSet(),
        )

        for (definition in IndicatorDefinitions.all) {
            for (param in definition.parameters) {
                assertTrue(param.defaultValue in param.minValue..param.maxValue)
            }
            val instance = IndicatorDefinitions.create(definition.type, "test")
            assertEquals(definition.type, instance.type)
            assertEquals(
                definition.parameters.map { it.key }.toSet(),
                instance.params.keys,
            )
        }
    }

    @Test
    fun defaultStateContainsLiquidationsPane() {
        val state = IndicatorDefinitions.defaultState()

        assertEquals(IndicatorDefinitions.LIQUIDATIONS_PANE_ID, state.activePaneId)
        assertEquals(
            listOf(IndicatorType.LIQUIDATIONS),
            state.panes.single().indicators.map { it.type },
        )
    }

    @Test
    fun liquidationsAreNotComputedByTheEngine() {
        val instance = IndicatorInstance(
            id = "test",
            type = IndicatorType.LIQUIDATIONS,
        )

        assertTrue(
            Ta4jIndicatorEngine().compute(candles(10), listOf(instance)).isEmpty(),
        )
    }

    @Test
    fun emptyCandlesProduceNoOutput() {
        val instance = IndicatorDefinitions.create(IndicatorType.SMA, "test")

        assertTrue(Ta4jIndicatorEngine().compute(emptyList(), listOf(instance)).isEmpty())
    }

    private fun compute(
        type: IndicatorType,
        overrides: Map<String, Double> = emptyMap(),
        candles: List<Candle> = candles(80),
    ): IndicatorOutput {
        val instance = IndicatorInstance(
            id = "test",
            type = type,
            params = IndicatorDefinitions.defaultParams(type) + overrides,
        )
        return Ta4jIndicatorEngine()
            .compute(candles, listOf(instance))
            .getValue("test")
    }

    private fun candles(count: Int): List<Candle> {
        val start = 1_700_000_000_000L
        return List(count) { index ->
            val close = 100f + index * 0.5f
            Candle(
                open = close - 0.25f,
                high = close + 1f,
                low = close - 1f,
                close = close,
                timestamp = start + index * 300_000L,
                volume = 1_000f + index,
            )
        }
    }
}
