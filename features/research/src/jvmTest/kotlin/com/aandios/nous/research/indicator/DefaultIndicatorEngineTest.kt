/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.trading.TradeSide
import com.aandios.nous.research.indicator.calculators.SmaCalculator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DefaultIndicatorEngineTest {

    @Test
    fun smaMatchesSimpleAverage() {
        val values = compute(IndicatorType.SMA, mapOf("period" to 3.0), testCandles(10))
            .lines.single().values

        assertEquals(100.5f, values[2], 0.01f)
        assertEquals(101.0f, values[3], 0.01f)
    }

    @Test
    fun emaWarmsUpAndStaysFinite() {
        val values = compute(IndicatorType.EMA, mapOf("period" to 10.0), testCandles(80))
            .lines.single().values

        assertFalse(values.last().isNaN())
        assertTrue(values.last() > 100f)
    }

    @Test
    fun bollingerBandsAreOrdered() {
        val output = compute(
            IndicatorType.BOLLINGER_BANDS,
            mapOf("period" to 20.0),
            testCandles(80),
        )
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
        val values = compute(IndicatorType.VWAP, mapOf("period" to 20.0), testCandles(80))
            .lines.single().values

        assertFalse(values.last().isNaN())
        assertTrue(values.last() > 100f)
    }

    @Test
    fun keltnerChannelsAreOrdered() {
        val output = compute(
            IndicatorType.KELTNER_CHANNELS,
            mapOf("period" to 20.0),
            testCandles(80),
        )
        val upper = output.lines.first { it.id == "upper" }.values
        val middle = output.lines.first { it.id == "middle" }.values
        val lower = output.lines.first { it.id == "lower" }.values

        assertTrue(upper[50] > middle[50])
        assertTrue(middle[50] > lower[50])
        assertEquals(
            IndicatorBandFill("upper", "lower", IndicatorColorRole.SECONDARY),
            output.bandFill,
        )
    }

    @Test
    fun rsiIsHighForMonotonicRise() {
        val output = compute(IndicatorType.RSI, mapOf("period" to 14.0), testCandles(80))
        val values = output.lines.single().values

        assertTrue(values.last() > 99f)
        assertEquals(IndicatorValueRange(0f, 100f, fixed = true), output.range)
        assertEquals(listOf(30f, 70f), output.levels.map { it.value })
    }

    @Test
    fun macdHasThreeLinesAndSymmetricRange() {
        val output = compute(IndicatorType.MACD, candles = testCandles(120))

        assertEquals(3, output.lines.size)
        assertTrue(output.zeroLine)
        assertTrue(output.range.min < 0f)
        assertTrue(output.range.max > 0f)
        assertFalse(output.lines.first { it.id == "histogram" }.values.last().isNaN())
    }

    @Test
    fun atrIsPositiveAndIncludesZero() {
        val output = compute(IndicatorType.ATR, mapOf("period" to 14.0), testCandles(80))
        val values = output.lines.single().values

        assertTrue(values.last() > 0f)
        assertTrue(output.range.min <= 0f)
    }

    @Test
    fun stochasticHonorsKAndDPeriods() {
        val output = compute(
            IndicatorType.STOCHASTIC,
            mapOf("kPeriod" to 14.0, "dPeriod" to 3.0),
            testCandles(80),
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
        val source = testCandles(40)
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
        val output = compute(IndicatorType.ADX, mapOf("period" to 14.0), testCandles(80))
        val adx = output.lines.first { it.id == "adx" }.values.last()

        assertEquals(3, output.lines.size)
        assertFalse(adx.isNaN())
        assertTrue(adx in 0f..100f)
        assertEquals(listOf(25f), output.levels.map { it.value })
    }

    @Test
    fun liquidationsAreVisibleOnEveryChartTimeframe() {
        val intervals = listOf(
            60_000L,        // 1m
            300_000L,       // 5m
            900_000L,       // 15m
            1_800_000L,     // 30m
            3_600_000L,     // 1h
            14_400_000L,    // 4h
            86_400_000L,    // 1d
            604_800_000L,   // 1w
        )

        for (interval in intervals) {
            val candles = List(10) { index ->
                Candle(
                    open = 100f,
                    high = 101f,
                    low = 99f,
                    close = 100f,
                    timestamp = TEST_CANDLE_START + index * interval,
                    volume = 1f,
                )
            }
            val output = compute(
                type = IndicatorType.LIQUIDATIONS,
                candles = candles,
                liquidations = listOf(
                    testLiquidation(candles.last().timestamp, 2.0, TradeSide.SELL),
                ),
            )

            assertEquals(
                2.0f,
                output.lines.single().values.last(),
                0.001f,
                "liquidation should be visible on interval $interval",
            )
        }
    }

    @Test
    fun liquidationsAreComputedFromFeed() {
        val candles = testCandles(10)
        val output = compute(
            type = IndicatorType.LIQUIDATIONS,
            candles = candles,
            liquidations = listOf(
                testLiquidation(candles.first().timestamp, 2.5, TradeSide.SELL),
                testLiquidation(candles.last().timestamp, 1.5, TradeSide.BUY),
            ),
        )

        val line = output.lines.single()
        assertEquals("liq", line.id)
        assertEquals(IndicatorLineStyle.HISTOGRAM, line.style)
        assertEquals(2.5f, line.values.first(), 0.001f)
        assertEquals(-1.5f, line.values.last(), 0.001f)
        assertTrue(output.range.min < 0f)
        assertTrue(output.range.max > 0f)
    }

    @Test
    fun emptyFeedSkipsLiquidationsOutput() {
        val instance = createIndicator(IndicatorType.LIQUIDATIONS, "liq")
        val outputs = DefaultIndicatorEngine(StaticIndicatorInputSource())
            .compute(testCandles(10), listOf(instance))

        assertTrue(outputs.isEmpty())
    }

    @Test
    fun oneFailingCalculatorDoesNotHideOthers() {
        val engine = DefaultIndicatorEngine(
            inputSource = StaticIndicatorInputSource(),
            calculators = listOf(SmaCalculator(), ThrowingEmaCalculator()),
        )
        val instances = listOf(
            createIndicator(IndicatorType.SMA, "sma"),
            createIndicator(IndicatorType.EMA, "ema"),
        )

        val outputs = engine.compute(testCandles(40), instances)

        assertTrue(outputs.containsKey("sma"))
        assertFalse(outputs.containsKey("ema"))
    }

    @Test
    fun emptyCandlesProduceNoOutput() {
        val instance = createIndicator(IndicatorType.SMA, "test")

        assertTrue(
            DefaultIndicatorEngine(StaticIndicatorInputSource())
                .compute(emptyList(), listOf(instance))
                .isEmpty(),
        )
    }

    private fun compute(
        type: IndicatorType,
        overrides: Map<String, Double> = emptyMap(),
        candles: List<Candle> = testCandles(80),
        liquidations: List<com.aandios.nous.api.market.model.liquidation.LiquidationOrder> = emptyList(),
    ): IndicatorOutput {
        val instance = createIndicator(type, "test").copy(
            params = defaultIndicatorParams(type) + overrides,
        )
        return DefaultIndicatorEngine(StaticIndicatorInputSource(liquidations))
            .compute(candles, listOf(instance))
            .getValue("test")
    }
}

private class ThrowingEmaCalculator : IndicatorCalculator {
    override val type = IndicatorType.EMA
    override val definition = IndicatorDefinition(
        type = type,
        label = "EMA",
        placement = IndicatorPlacement.PANE,
    )
    override val requiredInputs: Set<InputKind> = emptySet()

    override fun compute(
        input: IndicatorInput,
        params: Map<String, Double>,
    ): IndicatorComputation = error("boom")
}
