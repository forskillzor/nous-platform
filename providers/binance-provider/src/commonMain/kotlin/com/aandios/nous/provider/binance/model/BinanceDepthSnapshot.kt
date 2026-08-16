/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.model

import com.aandios.nous.api.market.model.orderbook.DepthSnapshot
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BinanceDepthSnapshot(
    @SerialName("lastUpdateId") val lastUpdateId: Long,
    @SerialName("bids") val bids: List<List<String>>,
    @SerialName("asks") val asks: List<List<String>>
) {
    fun toDepthSnapshot() = DepthSnapshot(lastUpdateId, bids, asks)
}