/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.core.ui.format.plainDecimalFixed
import com.aandios.nous.research.indicator.IndicatorDefinitions
import com.aandios.nous.research.indicator.IndicatorInstance
import com.aandios.nous.research.indicator.IndicatorPaneState
import com.aandios.nous.research.indicator.IndicatorParamSpec
import com.aandios.nous.research.indicator.IndicatorType
import kotlin.math.roundToInt

fun indicatorInstanceTitle(instance: IndicatorInstance): String {
    val definition = IndicatorDefinitions.definition(instance.type)
    fun value(key: String): Double? = instance.params[key]
    fun period(key: String = "period"): Int? = value(key)?.roundToInt()
    return when (instance.type) {
        IndicatorType.SMA,
        IndicatorType.EMA,
        IndicatorType.VWAP,
        IndicatorType.RSI,
        IndicatorType.ATR,
        IndicatorType.ADX,
        IndicatorType.VOLUME,
        -> period()?.let { "${definition.label} $it" } ?: definition.label

        IndicatorType.BOLLINGER_BANDS ->
            period()?.let { "${definition.label} $it · ${formatIndicatorParam(value("stdDev") ?: 2.0, false)}" }
                ?: definition.label

        IndicatorType.KELTNER_CHANNELS ->
            period()?.let {
                "${definition.label} $it · ${formatIndicatorParam(value("multiplier") ?: 2.0, false)}"
            } ?: definition.label

        IndicatorType.MACD -> {
            val fast = period("shortPeriod") ?: 12
            val slow = period("longPeriod") ?: 26
            val signal = period("signalPeriod") ?: 9
            "${definition.label} $fast/$slow/$signal"
        }

        IndicatorType.STOCHASTIC -> {
            val k = period("kPeriod") ?: 14
            val d = period("dPeriod") ?: 3
            "${definition.label} $k/$d"
        }

        IndicatorType.LIQUIDATIONS -> definition.label
    }
}

fun indicatorPaneTitle(pane: IndicatorPaneState): String =
    pane.indicators.joinToString(" · ") { indicatorInstanceTitle(it) }

fun formatIndicatorParam(value: Double, integer: Boolean): String =
    if (integer) value.roundToInt().toString() else plainDecimalFixed(value, 1)

fun stepIndicatorParam(
    value: Double,
    spec: IndicatorParamSpec,
    direction: Int,
): Double {
    val step = if (spec.integer) 1.0 else spec.step.coerceAtLeast(0.1)
    val next = (value + direction * step).coerceIn(spec.minValue, spec.maxValue)
    return if (spec.integer) next.roundToInt().toDouble() else (next * 10.0).roundToInt() / 10.0
}

/** Parses manual parameter input; returns null for blank/invalid values. */
fun parseIndicatorParam(input: String, spec: IndicatorParamSpec): Double? {
    val parsed = input.trim().replace(',', '.').toDoubleOrNull() ?: return null
    if (!parsed.isFinite()) return null
    val clamped = parsed.coerceIn(spec.minValue, spec.maxValue)
    return if (spec.integer) {
        clamped.roundToInt().toDouble()
    } else {
        (clamped * 10.0).roundToInt() / 10.0
    }
}
