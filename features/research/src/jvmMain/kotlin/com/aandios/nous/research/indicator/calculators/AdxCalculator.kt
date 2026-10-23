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
import org.ta4j.core.indicators.adx.ADXIndicator
import org.ta4j.core.indicators.adx.MinusDIIndicator
import org.ta4j.core.indicators.adx.PlusDIIndicator

class AdxCalculator : IndicatorCalculator {

    override val type: IndicatorType = IndicatorType.ADX

    override val definition = IndicatorDefinition(
        type = type,
        label = "ADX",
        placement = IndicatorPlacement.PANE,
        parameters = listOf(periodSpec(14)),
        lines = listOf(
            IndicatorLineSpec(
                id = "adx",
                label = "ADX",
                labelTemplate = "ADX {period}",
                colorLabel = "ADX",
            ),
            IndicatorLineSpec(
                id = "plus_di",
                label = "+DI",
                style = IndicatorLineStyle.DASHED,
                colorRole = IndicatorColorRole.BULLISH,
                colorLabel = "+DI",
            ),
            IndicatorLineSpec(
                id = "minus_di",
                label = "-DI",
                style = IndicatorLineStyle.DASHED,
                colorRole = IndicatorColorRole.BEARISH,
                colorLabel = "-DI",
            ),
        ),
        levels = listOf(IndicatorLevel(25f, "25")),
        fixedRange = IndicatorValueRange(0f, 100f, fixed = true),
        titleTemplate = "ADX {period}",
    )

    override val requiredInputs: Set<InputKind> = emptySet()

    override fun compute(
        input: IndicatorInput,
        params: Map<String, Double>,
    ): IndicatorComputation {
        val period = Ta4jSupport.intParam(params, "period", 14)
        val series = Ta4jSupport.buildSeries(input.candles)
        return IndicatorComputation(
            mapOf(
                "adx" to Ta4jSupport.values(series, ADXIndicator(series, period)),
                "plus_di" to Ta4jSupport.values(series, PlusDIIndicator(series, period)),
                "minus_di" to Ta4jSupport.values(series, MinusDIIndicator(series, period)),
            ),
        )
    }
}
