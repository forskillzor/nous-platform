/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BinanceKlineData(
    @SerialName("t") val startTime: Long = 0,          // Kline start time
    @SerialName("T") val endTime: Long = 0,            // Kline end time
    @SerialName("s") val symbol: String = "",          // Symbol
    @SerialName("i") val interval: String = "",        // Interval
    @SerialName("f") val firstTradeId: Long = 0,       // First trade ID
    @SerialName("L") val lastTradeId: Long = 0,        // Last trade ID
    @SerialName("o") val open: String = "",            // Open price
    @SerialName("c") val close: String = "",           // Close price
    @SerialName("h") val high: String = "",            // High price
    @SerialName("l") val low: String = "",             // Low price
    @SerialName("v") val volume: String = "",          // Base asset volume
    @SerialName("n") val numberOfTrades: Int = 0,      // Number of trades
    @SerialName("x") val isClosed: Boolean = false,    // Is this kline closed?
    @SerialName("q") val quoteVolume: String = "",     // Quote asset volume
    @SerialName("V") val takerBuyBaseVolume: String = "",  // Taker buy base asset volume
    @SerialName("Q") val takerBuyQuoteVolume: String = "", // Taker buy quote asset volume
    @SerialName("B") val ignore: String = ""           // Ignore
)