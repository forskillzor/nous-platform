/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model

import kotlinx.serialization.Serializable

@Serializable
data class MarketTickerStats(
    val symbol: String,
    val lastPrice: Double,
    val changePercent24h: Double,
    val quoteVolume24h: Double,
)
