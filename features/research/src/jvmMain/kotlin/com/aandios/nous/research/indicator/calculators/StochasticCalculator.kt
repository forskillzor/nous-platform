/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator.calculators

import com.aandios.nous.research.indicator.IndicatorCalculator
import com.aandios.nous.research.indicator.IndicatorColorRole
import com.aandios.nous.research.indicator.IndicatorComputation
import com.aandios.nous.research.indicator.IndicatorDefinition
import com.aandios.nous.research.indicator.IndicatorInput
import com.aandios.nous.research.indicator.IndicatorLevel
import com.aandios.nous.research.indicator.IndicatorLineSpec
import com.aandios.nous.research.indicator.IndicatorLineStyle
import com.aandios.nous.research.indicator.IndicatorPlacement
import com.aandios.nous.research.indicator.IndicatorType
import com.aandios.nous.research.indicator.IndicatorValueRange
import com.aandios.nous.research.indicator.InputKind
import org.ta4j.core.indicators.StochasticOscillatorDIndicator
import org.ta4j.core.indicators.StochasticOscillatorKIndicator
import org.ta4j.core.indicators.averages.SMAIndicator

class StochasticCalculator : IndicatorCalculator {

    override val type: IndicatorType = IndicatorType.STOCHASTIC

    override val definition = IndicatorDefinition(
        type = type,
        label = "Stochastic",
        placement = IndicatorPlacement.PANE,
        parameters = listOf(
            periodSpec(14, key = "kPeriod", label = "K"),
            periodSpec(3, key = "dPeriod", label = "D"),
        ),
        lines = listOf(
            IndicatorLineSpec(
                id = "k",
                label = "K",
                labelTemplate = "K {kPeriod}",
                colorLabel = "K",
            ),
            IndicatorLineSpec(
                id = "d",
                label = "D",
                labelTemplate = "D {dPeriod}",
                style = IndicatorLineStyle.DASHED,
                colorRole = IndicatorColorRole.SECONDARY,
                colorLabel = "D",
            ),
        ),
        levels = listOf(IndicatorLevel(20f, "20"), IndicatorLevel(80f, "80")),
        fixedRange = IndicatorValueRange(0f, 100f, fixed = true),
        titleTemplate = "Stochastic {kPeriod}/{dPeriod}",
    )

    override val requiredInputs: Set<InputKind> = emptySet()

    override fun compute(
        input: IndicatorInput,
        params: Map<String, Double>,
    ): IndicatorComputation {
        val kPeriod = Ta4jSupport.intParam(params, "kPeriod", 14)
        val dPeriod = Ta4jSupport.intParam(params, "dPeriod", 3)
        val series = Ta4jSupport.buildSeries(input.candles)
        val kIndicator = StochasticOscillatorKIndicator(series, kPeriod)
        val dIndicator = StochasticOscillatorDIndicator(
            SMAIndicator(kIndicator, dPeriod),
        )
        return IndicatorComputation(
            mapOf(
                "k" to Ta4jSupport.values(series, kIndicator),
                "d" to Ta4jSupport.values(series, dIndicator),
            ),
        )
    }
}
