/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator.calculators

import com.aandios.nous.research.indicator.IndicatorCalculator
import com.aandios.nous.research.indicator.IndicatorComputation
import com.aandios.nous.research.indicator.IndicatorDefinition
import com.aandios.nous.research.indicator.IndicatorInput
import com.aandios.nous.research.indicator.IndicatorLineSpec
import com.aandios.nous.research.indicator.IndicatorPlacement
import com.aandios.nous.research.indicator.IndicatorType
import com.aandios.nous.research.indicator.InputKind
import org.ta4j.core.indicators.ATRIndicator

class AtrCalculator : IndicatorCalculator {

    override val type: IndicatorType = IndicatorType.ATR

    override val definition = IndicatorDefinition(
        type = type,
        label = "ATR",
        placement = IndicatorPlacement.PANE,
        parameters = listOf(periodSpec(14)),
        lines = listOf(
            IndicatorLineSpec(
                id = "atr",
                label = "ATR",
                labelTemplate = "ATR {period}",
                colorLabel = "ATR",
            ),
        ),
        includeZero = true,
        titleTemplate = "ATR {period}",
    )

    override val requiredInputs: Set<InputKind> = emptySet()

    override fun compute(
        input: IndicatorInput,
        params: Map<String, Double>,
    ): IndicatorComputation {
        val period = Ta4jSupport.intParam(params, "period", 14)
        val series = Ta4jSupport.buildSeries(input.candles)
        return IndicatorComputation(
            mapOf("atr" to Ta4jSupport.values(series, ATRIndicator(series, period))),
        )
    }
}
