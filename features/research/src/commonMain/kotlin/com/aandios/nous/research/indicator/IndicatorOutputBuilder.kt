/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

/**
 * Converts declarative calculator output into renderable [IndicatorOutput].
 * Contract violations fail fast; the engine isolates them per instance.
 */
object IndicatorOutputBuilder {

    fun build(
        instance: IndicatorInstance,
        definition: IndicatorDefinition,
        computation: IndicatorComputation,
    ): IndicatorOutput {
        require(instance.type == definition.type) {
            "Instance ${instance.id} has type ${instance.type}, definition has ${definition.type}"
        }
        val expected = definition.lines.map { it.id }.toSet()
        require(computation.lines.keys == expected) {
            "Calculator for ${definition.type} returned lines ${computation.lines.keys}, expected $expected"
        }

        val lines = definition.lines.map { spec ->
            val raw = computation.lines.getValue(spec.id)
            val values = if (spec.style == IndicatorLineStyle.HISTOGRAM) {
                raw.histogramValues()
            } else {
                raw
            }
            IndicatorLine(
                id = spec.id,
                label = spec.labelTemplate
                    ?.let { renderIndicatorTemplate(it, instance.params, definition.parameters) }
                    ?.ifBlank { spec.label }
                    ?: spec.label,
                values = values,
                style = spec.style,
                colorRole = spec.colorRole,
                histogramColor = spec.histogramColor,
                colorArgb = instance.lineColors[spec.id],
            )
        }

        val range = computation.range
            ?: definition.fixedRange?.takeIf { it.fixed }
            ?: autoRange(lines, definition.includeZero)

        return IndicatorOutput(
            instanceId = instance.id,
            type = instance.type,
            lines = lines,
            range = range,
            levels = definition.levels,
            zeroLine = definition.zeroLine,
            bandFill = definition.bandFill,
        )
    }

    private fun FloatArray.histogramValues(): FloatArray {
        var hasNaN = false
        for (value in this) {
            if (value.isNaN()) {
                hasNaN = true
                break
            }
        }
        if (!hasNaN) return this
        return FloatArray(size) { index ->
            val value = this[index]
            if (value.isNaN()) 0f else value
        }
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
}
