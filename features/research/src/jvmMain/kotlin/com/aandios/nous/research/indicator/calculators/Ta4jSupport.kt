/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator.calculators

import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.research.indicator.IndicatorValueRange
import org.ta4j.core.BarSeries
import org.ta4j.core.BaseBarSeriesBuilder
import org.ta4j.core.Indicator
import org.ta4j.core.num.Num
import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

internal object Ta4jSupport {

    fun buildSeries(candles: List<Candle>): BarSeries {
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

    fun inferDuration(candles: List<Candle>): Duration {
        val deltas = candles
            .zipWithNext { first, second -> second.timestamp - first.timestamp }
            .filter { it > 0L }
            .sorted()
        val median = if (deltas.isEmpty()) 60_000L else deltas[deltas.size / 2]
        return Duration.ofMillis(median.coerceAtLeast(1_000L))
    }

    fun inferDurationMillis(candles: List<Candle>): Long = inferDuration(candles).toMillis()

    fun values(series: BarSeries, indicator: Indicator<Num>): FloatArray =
        FloatArray(series.barCount) { index ->
            val value = indicator.getValue(index).doubleValue()
            if (value.isFinite()) value.toFloat() else Float.NaN
        }

    fun symmetricRange(vararg arrays: FloatArray): IndicatorValueRange {
        var maxAbs = 0f
        for (array in arrays) {
            for (value in array) {
                if (value.isFinite()) maxAbs = maxOf(maxAbs, abs(value))
            }
        }
        val bound = maxOf(0.0001f, maxAbs)
        return IndicatorValueRange(-bound, bound)
    }

    fun intParam(params: Map<String, Double>, key: String, default: Int): Int =
        params[key]?.roundToInt()?.coerceAtLeast(1) ?: default

    fun doubleParam(params: Map<String, Double>, key: String, default: Double): Double =
        params[key]?.takeIf { it.isFinite() } ?: default
}
