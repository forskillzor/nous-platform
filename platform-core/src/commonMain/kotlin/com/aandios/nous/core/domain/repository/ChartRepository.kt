/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.domain.repository

import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.core.domain.timeseries.TimeSeriesSource

interface ChartRepository {
    /**
     * Источник свечей для символа/таймфрейма: история + realtime + слияние.
     * Пагинация и live-подписка выполняются через TimeSeriesController.
     */
    fun candleSource(ticker: String, timeframe: String): TimeSeriesSource<Candle>
}
