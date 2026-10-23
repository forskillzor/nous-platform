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
import org.ta4j.core.indicators.averages.EMAIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator

class EmaCalculator : IndicatorCalculator {

    override val type: IndicatorType = IndicatorType.EMA

    override val definition = IndicatorDefinition(
        type = type,
        label = "EMA",
        placement = IndicatorPlacement.OVERLAY,
        parameters = listOf(periodSpec(20)),
        lines = listOf(
            IndicatorLineSpec(
                id = "ema",
                label = "EMA",
                labelTemplate = "EMA {period}",
                colorLabel = "Line",
            ),
        ),
        titleTemplate = "EMA {period}",
    )

    override val requiredInputs: Set<InputKind> = emptySet()

    override fun compute(
        input: IndicatorInput,
        params: Map<String, Double>,
    ): IndicatorComputation {
        val period = Ta4jSupport.intParam(params, "period", 20)
        val series = Ta4jSupport.buildSeries(input.candles)
        val close = ClosePriceIndicator(series)
        return IndicatorComputation(
            mapOf("ema" to Ta4jSupport.values(series, EMAIndicator(close, period))),
        )
    }
}
