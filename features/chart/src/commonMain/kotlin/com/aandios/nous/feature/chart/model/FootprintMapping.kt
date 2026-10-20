/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.model

import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.FootprintCandle

/**
 * «Каркасная» свеча из footprint-свечи: используется для временной шкалы,
 * скролла, зума, crosshair и расчёта диапазона — чтобы footprint не нуждался
 * в отдельном движке.
 *
 * high/low берутся из minPrice/maxPrice, а если они отсутствуют (нули) —
 * из уровней bid/ask.
 */
fun FootprintCandle.toSkeletonCandle(): Candle {
    var levelMax = Float.NEGATIVE_INFINITY
    var levelMin = Float.POSITIVE_INFINITY
    if (!high.isFinite() || high <= 0f || !low.isFinite() || low <= 0f) {
        for (level in levels) {
            val price = level.priceFloat
            if (!price.isFinite()) continue
            if (price > levelMax) levelMax = price
            if (price < levelMin) levelMin = price
        }
    }
    return Candle(
        open = open,
        high = high.takeIf { it.isFinite() && it > 0f } ?: levelMax.takeIf { it.isFinite() } ?: 0f,
        close = close,
        low = low.takeIf { it.isFinite() && it > 0f } ?: levelMin.takeIf { it.isFinite() } ?: 0f,
        timestamp = startTime,
        volume = maxVolume,
    )
}
