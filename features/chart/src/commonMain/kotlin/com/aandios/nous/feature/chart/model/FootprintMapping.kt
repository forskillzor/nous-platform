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
    val levelPrices = levels.map { it.priceFloat }.filter { it.isFinite() }
    val highFromString = maxPrice.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
    val lowFromString = minPrice.toFloatOrNull()?.takeIf { it.isFinite() && it > 0f }
    return Candle(
        open = levels.firstOrNull()?.priceFloat ?: 0f,
        high = highFromString ?: (levelPrices.maxOrNull() ?: 0f),
        close = levels.lastOrNull()?.priceFloat ?: 0f,
        low = lowFromString ?: (levelPrices.minOrNull() ?: 0f),
        timestamp = startTime,
        volume = maxVolume,
    )
}
