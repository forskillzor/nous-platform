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
import com.aandios.nous.research.indicator.IndicatorLineSpec
import com.aandios.nous.research.indicator.IndicatorLineStyle
import com.aandios.nous.research.indicator.IndicatorPlacement
import com.aandios.nous.research.indicator.IndicatorType
import com.aandios.nous.research.indicator.InputKind
import org.ta4j.core.indicators.MACDIndicator
import org.ta4j.core.indicators.helpers.ClosePriceIndicator
import kotlin.math.max

class MacdCalculator : IndicatorCalculator {

    override val type: IndicatorType = IndicatorType.MACD

    override val definition = IndicatorDefinition(
        type = type,
        label = "MACD",
        placement = IndicatorPlacement.PANE,
        parameters = listOf(
            periodSpec(12, key = "shortPeriod", label = "Fast"),
            periodSpec(26, key = "longPeriod", label = "Slow"),
            periodSpec(9, key = "signalPeriod", label = "Signal"),
        ),
        lines = listOf(
            IndicatorLineSpec(
                id = "macd",
                label = "MACD",
                colorLabel = "MACD",
            ),
            IndicatorLineSpec(
                id = "signal",
                label = "Signal",
                style = IndicatorLineStyle.DASHED,
                colorRole = IndicatorColorRole.SECONDARY,
                colorLabel = "Signal",
            ),
            IndicatorLineSpec(
                id = "histogram",
                label = "Histogram",
                style = IndicatorLineStyle.HISTOGRAM,
                colorRole = IndicatorColorRole.NEUTRAL,
                colorLabel = "Histogram",
            ),
        ),
        zeroLine = true,
        titleTemplate = "MACD {shortPeriod}/{longPeriod}/{signalPeriod}",
    )

    override val requiredInputs: Set<InputKind> = emptySet()

    override fun compute(
        input: IndicatorInput,
        params: Map<String, Double>,
    ): IndicatorComputation {
        val fast = Ta4jSupport.intParam(params, "shortPeriod", 12)
        val slow = max(Ta4jSupport.intParam(params, "longPeriod", 26), fast + 1)
        val signalPeriod = Ta4jSupport.intParam(params, "signalPeriod", 9)
        val series = Ta4jSupport.buildSeries(input.candles)
        val close = ClosePriceIndicator(series)
        val macd = MACDIndicator(close, fast, slow)
        val macdLine = Ta4jSupport.values(series, macd)
        val signal = Ta4jSupport.values(series, macd.getSignalLine(signalPeriod))
        val histogram = Ta4jSupport.values(series, macd.getHistogram(signalPeriod))
        return IndicatorComputation(
            lines = mapOf(
                "macd" to macdLine,
                "signal" to signal,
                "histogram" to histogram,
            ),
            range = Ta4jSupport.symmetricRange(macdLine, signal, histogram),
        )
    }
}
