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
 */
fun FootprintCandle.toSkeletonCandle(): Candle =
    Candle(
        open = open,
        high = high,
        close = close,
        low = low,
        timestamp = startTime,
        volume = maxVolume,
    )
