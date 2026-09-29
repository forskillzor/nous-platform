/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.data.repository

import com.aandios.nous.api.market.adapters.ChartAdapter
import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.core.data.timeseries.CandleSeriesSource
import com.aandios.nous.core.domain.repository.ChartRepository
import com.aandios.nous.core.domain.timeseries.TimeSeriesSource

class ChartRepositoryImpl(
    private val chartAdapter: ChartAdapter,
) : ChartRepository {

    override fun candleSource(ticker: String, timeframe: String): TimeSeriesSource<Candle> =
        CandleSeriesSource(
            chartAdapter = chartAdapter,
            symbol = ticker.replace("/", ""),
            timeframe = timeframe,
        )
}
