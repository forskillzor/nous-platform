/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator.calculators

import com.aandios.nous.api.market.model.trading.TradeSide
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
import com.aandios.nous.research.indicator.indexOfBucket

class LiquidationsCalculator : IndicatorCalculator {

    override val type: IndicatorType = IndicatorType.LIQUIDATIONS

    override val definition = IndicatorDefinition(
        type = type,
        label = "Liquidations",
        placement = IndicatorPlacement.PANE,
        lines = listOf(
            IndicatorLineSpec(
                id = "liq",
                label = "Liquidations",
                style = IndicatorLineStyle.HISTOGRAM,
                colorRole = IndicatorColorRole.NEUTRAL,
                histogramColor = HistogramColorMode.SIGN,
                colorLabel = "Liquidations",
            ),
        ),
        includeZero = true,
        titleTemplate = "Liquidations",
    )

    override val requiredInputs: Set<InputKind> = setOf(InputKind.LIQUIDATIONS)

    override fun compute(
        input: IndicatorInput,
        params: Map<String, Double>,
    ): IndicatorComputation {
        val values = FloatArray(input.candles.size)
        val timeframe = Ta4jSupport.inferDurationMillis(input.candles)
        for (order in input.liquidations) {
            val index = input.candles.indexOfBucket(order.timestamp, timeframe) ?: continue
            val signed = when (order.side) {
                TradeSide.SELL -> order.quantity.toFloat()
                TradeSide.BUY -> -order.quantity.toFloat()
            }
            values[index] += signed
        }
        return IndicatorComputation(mapOf("liq" to values))
    }
}
