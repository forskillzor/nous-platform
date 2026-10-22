/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

import com.aandios.nous.api.market.model.Candle
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeriesBuilder
import org.ta4j.core.Indicator
import org.ta4j.core.indicators.ATRIndicator
import org.ta4j.core.indicators.MACDIndicator
import org.ta4j.core.indicators.RSIIndicator
import org.ta4j.core.indicators.StochasticOscillatorDIndicator
import org.ta4j.core.indicators.StochasticOscillatorKIndicator
import org.ta4j.core.indicators.adx.ADXIndicator
import org.ta4j.core.indicators.adx.MinusDIIndicator
import org.ta4j.core.indicators.adx.PlusDIIndicator
import org.ta4j.core.indicators.averages.EMAIndicator
import org.ta4j.core.indicators.averages.SMAIndicator
import org.ta4j.core.indicators.bollinger.BollingerBandFacade
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import org.ta4j.core.indicators.helpers.VolumeIndicator
import org.ta4j.core.indicators.keltner.KeltnerChannelFacade
import org.ta4j.core.indicators.volume.VWAPIndicator
import org.ta4j.core.num.Num
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/** TA4J-backed indicator engine. Only this JVM source set depends on TA4J. */
class Ta4jIndicatorEngine : IndicatorEngine {

    override fun compute(
        candles: List<Candle>,
        instances: List<IndicatorInstance>,
    ): Map<String, IndicatorOutput> {
        if (candles.isEmpty()) return emptyMap()

        val computedTypes = instances.filter { it.type != IndicatorType.LIQUIDATIONS }
        if (computedTypes.isEmpty()) return emptyMap()

        val series = buildSeries(candles)
        val close = ClosePriceIndicator(series)
        val result = LinkedHashMap<String, IndicatorOutput>()

        for (instance in computedTypes) {
            val output = when (instance.type) {
                IndicatorType.SMA -> sma(series, close, instance)
                IndicatorType.EMA -> ema(series, close, instance)
                IndicatorType.BOLLINGER_BANDS -> bollinger(series, instance)
                IndicatorType.VWAP -> vwap(series, instance)
                IndicatorType.KELTNER_CHANNELS -> keltner(series, instance)
                IndicatorType.RSI -> rsi(series, close, instance)
                IndicatorType.MACD -> macd(series, close, instance)
                IndicatorType.ATR -> atr(series, instance)
                IndicatorType.STOCHASTIC -> stochastic(series, instance)
                IndicatorType.VOLUME -> volume(series, instance)
                IndicatorType.ADX -> adx(series, instance)
                IndicatorType.LIQUIDATIONS -> null
            }
            if (output != null) result[instance.id] = output
        }
        return result
    }

    private fun buildSeries(candles: List<Candle>): BarSeries {
        val duration = inferDuration(candles)
        val series = BaseBarSeriesBuilder()
            .withName("nous-chart")
            .build()

        for (candle in candles) {
            val begin = Instant.ofEpochMilli(candle.timestamp)
            series.barBuilder()
                .timePeriod(duration)
                .beginTime(begin)
                .endTime(begin.plus(duration))
                .openPrice(candle.open.toDouble())
                .highPrice(candle.high.toDouble())
                .lowPrice(candle.low.toDouble())
                .closePrice(candle.close.toDouble())
                .volume(candle.volume.toDouble())
                .add()
        }
        return series
    }

    private fun inferDuration(candles: List<Candle>): Duration {
        val deltas = candles
            .zipWithNext { first, second -> second.timestamp - first.timestamp }
            .filter { it > 0L }
            .sorted()
        val median = if (deltas.isEmpty()) 60_000L else deltas[deltas.size / 2]
        return Duration.ofMillis(median.coerceAtLeast(1_000L))
    }

    private fun sma(
        series: BarSeries,
        close: Indicator<Num>,
        instance: IndicatorInstance,
    ): IndicatorOutput {
        val period = intParam(instance, "period", 20)
        val line = line(
            id = "sma",
            label = "SMA $period",
            values = values(series, SMAIndicator(close, period)),
        )
        return output(instance, listOf(line))
    }

    private fun ema(
        series: BarSeries,
        close: Indicator<Num>,
        instance: IndicatorInstance,
    ): IndicatorOutput {
        val period = intParam(instance, "period", 20)
        val line = line(
            id = "ema",
            label = "EMA $period",
            values = values(series, EMAIndicator(close, period)),
        )
        return output(instance, listOf(line))
    }

    private fun bollinger(
        series: BarSeries,
        instance: IndicatorInstance,
    ): IndicatorOutput {
        val period = intParam(instance, "period", 20)
        val stdDev = doubleParam(instance, "stdDev", 2.0).coerceAtLeast(0.1)
        val facade = BollingerBandFacade(series, period, stdDev)
        val upper = line(
            id = "upper",
            label = "Upper",
            values = values(series, facade.upper()),
            colorRole = IndicatorColorRole.PRIMARY,
        )
        val middle = line(
            id = "middle",
            label = "Middle",
            values = values(series, facade.middle()),
            style = IndicatorLineStyle.DASHED,
            colorRole = IndicatorColorRole.SECONDARY,
        )
        val lower = line(
            id = "lower",
            label = "Lower",
            values = values(series, facade.lower()),
            colorRole = IndicatorColorRole.PRIMARY,
        )
        return output(
            instance = instance,
            lines = listOf(upper, middle, lower),
            bandFill = IndicatorBandFill("upper", "lower"),
        )
    }

    private fun vwap(
        series: BarSeries,
        instance: IndicatorInstance,
    ): IndicatorOutput {
        val period = intParam(instance, "period", 20)
        return output(
            instance = instance,
            lines = listOf(
                line(
                    id = "vwap",
                    label = "VWAP $period",
                    values = values(series, VWAPIndicator(series, period)),
                    colorRole = IndicatorColorRole.TERTIARY,
                ),
            ),
        )
    }

    private fun keltner(
        series: BarSeries,
        instance: IndicatorInstance,
    ): IndicatorOutput {
        val period = intParam(instance, "period", 20)
        val multiplier = doubleParam(instance, "multiplier", 2.0).coerceAtLeast(0.1)
        val facade = KeltnerChannelFacade(series, period, period, multiplier)
        val upper = line(
            id = "upper",
            label = "Upper",
            values = values(series, facade.upper()),
            colorRole = IndicatorColorRole.SECONDARY,
        )
        val middle = line(
            id = "middle",
            label = "Middle",
            values = values(series, facade.middle()),
            style = IndicatorLineStyle.DASHED,
            colorRole = IndicatorColorRole.NEUTRAL,
        )
        val lower = line(
            id = "lower",
            label = "Lower",
            values = values(series, facade.lower()),
            colorRole = IndicatorColorRole.SECONDARY,
        )
        return output(
            instance = instance,
            lines = listOf(upper, middle, lower),
            bandFill = IndicatorBandFill("upper", "lower", IndicatorColorRole.SECONDARY),
        )
    }

    private fun rsi(
        series: BarSeries,
        close: Indicator<Num>,
        instance: IndicatorInstance,
    ): IndicatorOutput {
        val period = intParam(instance, "period", 14)
        return output(
            instance = instance,
            lines = listOf(
                line(
                    id = "rsi",
                    label = "RSI $period",
                    values = values(series, RSIIndicator(close, period)),
                ),
            ),
            range = fixedRange(0f, 100f),
            levels = listOf(IndicatorLevel(30f, "30"), IndicatorLevel(70f, "70")),
        )
    }

    private fun macd(
        series: BarSeries,
        close: Indicator<Num>,
        instance: IndicatorInstance,
    ): IndicatorOutput {
        val fast = intParam(instance, "shortPeriod", 12)
        val slow = max(intParam(instance, "longPeriod", 26), fast + 1)
        val signalPeriod = intParam(instance, "signalPeriod", 9)
        val macd = MACDIndicator(close, fast, slow)
        val signal = values(series, macd.getSignalLine(signalPeriod))
        val histogram = values(series, macd.getHistogram(signalPeriod))
        val macdLine = values(series, macd)
        val maxAbs = maxOf(
            0.0001f,
            finiteMaxAbs(macdLine, signal, histogram),
        )
        return output(
            instance = instance,
            lines = listOf(
                line("macd", "MACD", macdLine),
                line(
                    id = "signal",
                    label = "Signal",
                    values = signal,
                    style = IndicatorLineStyle.DASHED,
                    colorRole = IndicatorColorRole.SECONDARY,
                ),
                line(
                    id = "histogram",
                    label = "Histogram",
                    values = histogram,
                    style = IndicatorLineStyle.HISTOGRAM,
                    colorRole = IndicatorColorRole.NEUTRAL,
                ),
            ),
            range = IndicatorValueRange(-maxAbs, maxAbs),
            zeroLine = true,
        )
    }

    private fun atr(
        series: BarSeries,
        instance: IndicatorInstance,
    ): IndicatorOutput {
        val period = intParam(instance, "period", 14)
        return output(
            instance = instance,
            lines = listOf(
                line(
                    id = "atr",
                    label = "ATR $period",
                    values = values(series, ATRIndicator(series, period)),
                ),
            ),
            includeZero = true,
        )
    }

    private fun stochastic(
        series: BarSeries,
        instance: IndicatorInstance,
    ): IndicatorOutput {
        val kPeriod = intParam(instance, "kPeriod", 14)
        val dPeriod = intParam(instance, "dPeriod", 3)
        val kIndicator = StochasticOscillatorKIndicator(series, kPeriod)
        val dIndicator = StochasticOscillatorDIndicator(
            SMAIndicator(kIndicator, dPeriod),
        )
        return output(
            instance = instance,
            lines = listOf(
                line("k", "K $kPeriod", values(series, kIndicator)),
                line(
                    id = "d",
                    label = "D $dPeriod",
                    values = values(series, dIndicator),
                    style = IndicatorLineStyle.DASHED,
                    colorRole = IndicatorColorRole.SECONDARY,
                ),
            ),
            range = fixedRange(0f, 100f),
            levels = listOf(IndicatorLevel(20f, "20"), IndicatorLevel(80f, "80")),
        )
    }

    private fun volume(
        series: BarSeries,
        instance: IndicatorInstance,
    ): IndicatorOutput {
        val period = intParam(instance, "period", 20)
        val volume = VolumeIndicator(series)
        return output(
            instance = instance,
            lines = listOf(
                line(
                    id = "volume",
                    label = "Volume",
                    values = values(series, volume),
                    style = IndicatorLineStyle.HISTOGRAM,
                    colorRole = IndicatorColorRole.NEUTRAL,
                    histogramColor = HistogramColorMode.CANDLE,
                ),
                line(
                    id = "volume_ma",
                    label = "Vol MA $period",
                    values = values(series, SMAIndicator(volume, period)),
                    colorRole = IndicatorColorRole.SECONDARY,
                ),
            ),
            includeZero = true,
        )
    }

    private fun adx(
        series: BarSeries,
        instance: IndicatorInstance,
    ): IndicatorOutput {
        val period = intParam(instance, "period", 14)
        return output(
            instance = instance,
            lines = listOf(
                line("adx", "ADX $period", values(series, ADXIndicator(series, period))),
                line(
                    id = "plus_di",
                    label = "+DI",
                    values = values(series, PlusDIIndicator(series, period)),
                    style = IndicatorLineStyle.DASHED,
                    colorRole = IndicatorColorRole.BULLISH,
                ),
                line(
                    id = "minus_di",
                    label = "-DI",
                    values = values(series, MinusDIIndicator(series, period)),
                    style = IndicatorLineStyle.DASHED,
                    colorRole = IndicatorColorRole.BEARISH,
                ),
            ),
            range = fixedRange(0f, 100f),
            levels = listOf(IndicatorLevel(25f, "25")),
        )
    }

    private fun output(
        instance: IndicatorInstance,
        lines: List<IndicatorLine>,
        range: IndicatorValueRange? = null,
        levels: List<IndicatorLevel> = emptyList(),
        zeroLine: Boolean = false,
        bandFill: IndicatorBandFill? = null,
        includeZero: Boolean = false,
    ): IndicatorOutput = IndicatorOutput(
        instanceId = instance.id,
        type = instance.type,
        lines = lines,
        range = range ?: autoRange(lines, includeZero),
        levels = levels,
        zeroLine = zeroLine,
        bandFill = bandFill,
    )

    private fun line(
        id: String,
        label: String,
        values: FloatArray,
        style: IndicatorLineStyle = IndicatorLineStyle.LINE,
        colorRole: IndicatorColorRole = IndicatorColorRole.PRIMARY,
        histogramColor: HistogramColorMode = HistogramColorMode.SIGN,
    ) = IndicatorLine(
        id = id,
        label = label,
        values = values,
        style = style,
        colorRole = colorRole,
        histogramColor = histogramColor,
    )

    private fun values(series: BarSeries, indicator: Indicator<Num>): FloatArray =
        FloatArray(series.barCount) { index ->
            val value = indicator.getValue(index).doubleValue()
            if (value.isFinite()) value.toFloat() else Float.NaN
        }

    private fun autoRange(
        lines: List<IndicatorLine>,
        includeZero: Boolean,
    ): IndicatorValueRange {
        var min = Float.POSITIVE_INFINITY
        var max = Float.NEGATIVE_INFINITY
        for (line in lines) {
            for (value in line.values) {
                if (!value.isFinite()) continue
                min = minOf(min, value)
                max = maxOf(max, value)
            }
        }
        if (!min.isFinite() || !max.isFinite()) return IndicatorValueRange(0f, 1f)
        if (includeZero) {
            min = minOf(min, 0f)
            max = maxOf(max, 0f)
        }
        if (max <= min) max = min + 1f
        return IndicatorValueRange(min, max)
    }

    private fun fixedRange(min: Float, max: Float) =
        IndicatorValueRange(min, max, fixed = true)

    private fun finiteMaxAbs(vararg arrays: FloatArray): Float {
        var maxAbs = 0f
        for (array in arrays) {
            for (value in array) {
                if (value.isFinite()) maxAbs = maxOf(maxAbs, abs(value))
            }
        }
        return maxAbs
    }

    private fun intParam(
        instance: IndicatorInstance,
        key: String,
        default: Int,
    ): Int = instance.params[key]?.roundToInt()?.coerceAtLeast(1) ?: default

    private fun doubleParam(
        instance: IndicatorInstance,
        key: String,
        default: Double,
    ): Double = instance.params[key]?.takeIf { it.isFinite() } ?: default
}
