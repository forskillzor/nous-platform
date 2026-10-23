/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder

/** A value aligned to a source timestamp (funding rate, open interest). */
data class TimedValue(
    val timestamp: Long,
    val value: Float,
)

typealias FundingRatePoint = TimedValue
typealias OpenInterestPoint = TimedValue

/** Additional data feeds an indicator may require besides candles. */
enum class InputKind {
    CANDLES,
    LIQUIDATIONS,
    FUNDING_RATES,
    OPEN_INTEREST,
}

/**
 * Immutable input snapshot passed to calculators. Candles are always present;
 * feed lists are filled only when requested through [IndicatorInputSource].
 */
data class IndicatorInput(
    val candles: List<Candle> = emptyList(),
    val liquidations: List<LiquidationOrder> = emptyList(),
    val fundingRates: List<FundingRatePoint> = emptyList(),
    val openInterest: List<OpenInterestPoint> = emptyList(),
) {
    fun supports(required: Set<InputKind>): Boolean = required.all { kind ->
        when (kind) {
            InputKind.CANDLES -> candles.isNotEmpty()
            InputKind.LIQUIDATIONS -> liquidations.isNotEmpty()
            InputKind.FUNDING_RATES -> fundingRates.isNotEmpty()
            InputKind.OPEN_INTEREST -> openInterest.isNotEmpty()
        }
    }
}
