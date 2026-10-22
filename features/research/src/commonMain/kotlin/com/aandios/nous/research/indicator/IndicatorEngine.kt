/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

import com.aandios.nous.api.market.model.Candle

/** Computes indicator values for the chart. Implementations may be JVM-only. */
interface IndicatorEngine {
    /**
     * Returns outputs keyed by [IndicatorInstance.id]. Indicators that are
     * rendered outside the engine (liquidations) are omitted.
     */
    fun compute(
        candles: List<Candle>,
        instances: List<IndicatorInstance>,
    ): Map<String, IndicatorOutput>
}
