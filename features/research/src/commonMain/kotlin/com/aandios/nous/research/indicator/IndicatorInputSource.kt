/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

import com.aandios.nous.api.market.model.Candle
import kotlinx.coroutines.flow.Flow

/**
 * Supplies an immutable input snapshot for one computation. Implementations
 * cache live feeds and expose [updates] so the chart can request a refresh
 * without waiting for a new candle.
 */
interface IndicatorInputSource {

    fun snapshot(
        required: Set<InputKind>,
        candles: List<Candle>,
    ): IndicatorInput

    fun updates(): Flow<Unit>
}
