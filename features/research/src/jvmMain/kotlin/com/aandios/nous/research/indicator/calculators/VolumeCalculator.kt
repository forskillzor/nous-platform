/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator.calculators

import com.aandios.nous.research.indicator.HistogramColorMode
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
import org.ta4j.core.indicators.averages.SMAIndicator
import org.ta4j.core.indicators.helpers.VolumeIndicator

class VolumeCalculator : IndicatorCalculator {

    override val type: IndicatorType = IndicatorType.VOLUME

    override val definition = IndicatorDefinition(
        type = type,
        label = "Volume",
        placement = IndicatorPlacement.PANE,
        parameters = listOf(periodSpec(20, label = "MA period")),
        lines = listOf(
            IndicatorLineSpec(
                id = "volume",
                label = "Volume",
                style = IndicatorLineStyle.HISTOGRAM,
                colorRole = IndicatorColorRole.NEUTRAL,
                histogramColor = HistogramColorMode.CANDLE,
                colorLabel = "Volume",
            ),
            IndicatorLineSpec(
                id = "volume_ma",
                label = "Vol MA",
                labelTemplate = "Vol MA {period}",
                colorRole = IndicatorColorRole.SECONDARY,
                colorLabel = "MA",
            ),
        ),
        includeZero = true,
        titleTemplate = "Volume {period}",
    )

    override val requiredInputs: Set<InputKind> = emptySet()

    override fun compute(
        input: IndicatorInput,
        params: Map<String, Double>,
    ): IndicatorComputation {
        val period = Ta4jSupport.intParam(params, "period", 20)
        val series = Ta4jSupport.buildSeries(input.candles)
        val volume = VolumeIndicator(series)
        return IndicatorComputation(
            mapOf(
                "volume" to Ta4jSupport.values(series, volume),
                "volume_ma" to Ta4jSupport.values(series, SMAIndicator(volume, period)),
            ),
        )
    }
}
