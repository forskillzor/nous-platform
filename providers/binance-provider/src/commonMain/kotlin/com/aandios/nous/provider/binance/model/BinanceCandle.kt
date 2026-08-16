/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.model

import com.aandios.nous.api.market.model.Candle
import kotlinx.serialization.Serializable

@Serializable
data class BinanceCandle(
    val openTime: Long,
    val open: String,
    val high: String,
    val low: String,
    val close: String,
    val volume: String,
    val closeTime: Long,
    val quoteAssetVolume: String,
    val numberOfTrades: Int,
    val takerBuyBaseAssetVolume: String,
    val takerBuyQuoteAssetVolume: String
) {
    fun toCandle(): Candle {
        return Candle(
            open = open.toFloat(),
            high = high.toFloat(),
            low = low.toFloat(),
            close = close.toFloat(),
            timestamp = openTime,
            volume = volume.toFloat(),

            )
    }
}
