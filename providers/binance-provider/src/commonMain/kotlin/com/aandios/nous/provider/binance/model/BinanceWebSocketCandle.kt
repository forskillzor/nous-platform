/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.model

import com.aandios.nous.api.market.model.Candle

data class BinanceWebSocketCandle(
    val symbol: String,
    val openTime: Long,
    val closeTime: Long,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: String,
    val isClosed: Boolean
) {
    fun toCandle(): Candle {
        return Candle(
            open = open.toFloatOrNull() ?: 0f,
            high = high.toFloatOrNull() ?: 0f,
            low = low.toFloatOrNull() ?: 0f,
            close = close.toFloatOrNull() ?: 0f,
            timestamp = openTime,
            volume = volume.toFloatOrNull() ?: 0f
        )
    }
}