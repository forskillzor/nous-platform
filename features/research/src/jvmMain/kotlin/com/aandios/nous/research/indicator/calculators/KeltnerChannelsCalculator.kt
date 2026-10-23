/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator.calculators

import com.aandios.nous.research.indicator.IndicatorBandFill
import com.aandios.nous.research.indicator.IndicatorCalculator
import com.aandios.nous.research.indicator.IndicatorColorRole
import com.aandios.nous.research.indicator.IndicatorComputation
import com.aandios.nous.research.indicator.IndicatorDefinition
import com.aandios.nous.research.indicator.IndicatorInput
import com.aandios.nous.research.indicator.IndicatorLineSpec
import com.aandios.nous.research.indicator.IndicatorLineStyle
import com.aandios.nous.research.indicator.IndicatorPlacement
import com.aandios.nous.research.indicator.IndicatorType
import com.aandios.nous.research.indicator.InputKind
import org.ta4j.core.indicators.keltner.KeltnerChannelFacade

class KeltnerChannelsCalculator : IndicatorCalculator {

    override val type: IndicatorType = IndicatorType.KELTNER_CHANNELS

    override val definition = IndicatorDefinition(
        type = type,
        label = "Keltner Channels",
        placement = IndicatorPlacement.OVERLAY,
        parameters = listOf(
            periodSpec(20),
            decimalSpec("multiplier", "Multiplier", 2.0),
        ),
        lines = listOf(
            IndicatorLineSpec(
                id = "upper",
                label = "Upper",
                colorRole = IndicatorColorRole.SECONDARY,
                colorLabel = "Upper",
            ),
            IndicatorLineSpec(
                id = "middle",
                label = "Middle",
                style = IndicatorLineStyle.DASHED,
                colorRole = IndicatorColorRole.NEUTRAL,
                colorLabel = "Middle",
            ),
            IndicatorLineSpec(
                id = "lower",
                label = "Lower",
                colorRole = IndicatorColorRole.SECONDARY,
                colorLabel = "Lower",
            ),
        ),
        bandFill = IndicatorBandFill("upper", "lower", IndicatorColorRole.SECONDARY),
        titleTemplate = "Keltner Channels {period} · {multiplier}",
    )

    override val requiredInputs: Set<InputKind> = emptySet()

    override fun compute(
        input: IndicatorInput,
        params: Map<String, Double>,
    ): IndicatorComputation {
        val period = Ta4jSupport.intParam(params, "period", 20)
        val multiplier = Ta4jSupport.doubleParam(params, "multiplier", 2.0).coerceAtLeast(0.1)
        val series = Ta4jSupport.buildSeries(input.candles)
        val facade = KeltnerChannelFacade(series, period, period, multiplier)
        return IndicatorComputation(
            mapOf(
                "upper" to Ta4jSupport.values(series, facade.upper()),
                "middle" to Ta4jSupport.values(series, facade.middle()),
                "lower" to Ta4jSupport.values(series, facade.lower()),
            ),
        )
    }
}
