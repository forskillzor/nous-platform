/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator.calculators

import com.aandios.nous.research.indicator.IndicatorCalculator
import com.aandios.nous.research.indicator.IndicatorComputation
import com.aandios.nous.research.indicator.IndicatorDefinition
import com.aandios.nous.research.indicator.IndicatorInput
import com.aandios.nous.research.indicator.IndicatorLevel
import com.aandios.nous.research.indicator.IndicatorLineSpec
import com.aandios.nous.research.indicator.IndicatorPlacement
import com.aandios.nous.research.indicator.IndicatorType
import com.aandios.nous.research.indicator.IndicatorValueRange
import com.aandios.nous.research.indicator.InputKind
import org.ta4j.core.indicators.RSIIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator

class RsiCalculator : IndicatorCalculator {

    override val type: IndicatorType = IndicatorType.RSI

    override val definition = IndicatorDefinition(
        type = type,
        label = "RSI",
        placement = IndicatorPlacement.PANE,
        parameters = listOf(periodSpec(14)),
        lines = listOf(
            IndicatorLineSpec(
                id = "rsi",
                label = "RSI",
                labelTemplate = "RSI {period}",
                colorLabel = "RSI",
            ),
        ),
        levels = listOf(IndicatorLevel(30f, "30"), IndicatorLevel(70f, "70")),
        fixedRange = IndicatorValueRange(0f, 100f, fixed = true),
        titleTemplate = "RSI {period}",
    )

    override val requiredInputs: Set<InputKind> = emptySet()

    override fun compute(
        input: IndicatorInput,
        params: Map<String, Double>,
    ): IndicatorComputation {
        val period = Ta4jSupport.intParam(params, "period", 14)
        val series = Ta4jSupport.buildSeries(input.candles)
        val close = ClosePriceIndicator(series)
        return IndicatorComputation(
            mapOf("rsi" to Ta4jSupport.values(series, RSIIndicator(close, period))),
        )
    }
}
