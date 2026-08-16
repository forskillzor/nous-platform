/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BinanceWebSocketResponse(
    @SerialName("e") val eventType: String = "",  // "kline"
    @SerialName("E") val eventTime: Long = 0,     // Event time
    @SerialName("s") val symbol: String = "",     // Symbol
    @SerialName("k") val kline: BinanceKlineData  // Kline data
)