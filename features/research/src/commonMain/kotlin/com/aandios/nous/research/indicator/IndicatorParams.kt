/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

import kotlin.math.roundToInt

private val TemplatePlaceholder = Regex("\\{([A-Za-z0-9_]+)}")

/**
 * Merges overrides into [current], clamps to the definition's specs and rounds
 * integer parameters. Unknown keys are dropped.
 */
fun mergeIndicatorParams(
    definition: IndicatorDefinition,
    current: Map<String, Double>,
    overrides: Map<String, Double>,
): Map<String, Double> {
    val merged = current + overrides
    return definition.parameters.associate { spec ->
        val raw = merged[spec.key] ?: spec.defaultValue
        val value = raw.coerceIn(spec.minValue, spec.maxValue)
        spec.key to if (spec.integer) {
            value.roundToInt().toDouble()
        } else {
            (value * 10.0).roundToInt() / 10.0
        }
    }
}

/** Replaces `{key}` placeholders with the formatted parameter value. */
fun renderIndicatorTemplate(
    template: String,
    params: Map<String, Double>,
    parameters: List<IndicatorParamSpec>,
): String {
    if (template.isEmpty()) return ""
    val specs = parameters.associateBy { it.key }
    return TemplatePlaceholder.replace(template) { match ->
        val key = match.groupValues[1]
        val spec = specs[key] ?: return@replace match.value
        formatTemplateValue(params[key] ?: spec.defaultValue, spec)
    }
}

fun formatTemplateValue(value: Double, spec: IndicatorParamSpec): String =
    if (spec.integer) {
        value.roundToInt().toString()
    } else {
        val rounded = (value * 10.0).roundToInt() / 10.0
        rounded.toString()
    }
