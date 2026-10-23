/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.core.ui.format.plainDecimalFixed
import com.aandios.nous.research.indicator.IndicatorInstance
import com.aandios.nous.research.indicator.IndicatorPaneState
import com.aandios.nous.research.indicator.IndicatorParamSpec
import com.aandios.nous.research.indicator.indicatorDefinition
import com.aandios.nous.research.indicator.renderIndicatorTemplate
import kotlin.math.roundToInt

fun indicatorInstanceTitle(instance: IndicatorInstance): String {
    val definition = indicatorDefinition(instance.type)
    return renderIndicatorTemplate(
        template = definition.titleTemplate,
        params = instance.params,
        parameters = definition.parameters,
    ).ifBlank { definition.label }
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
