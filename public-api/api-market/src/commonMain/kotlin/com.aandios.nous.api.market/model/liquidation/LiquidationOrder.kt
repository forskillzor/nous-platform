/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model.liquidation

import com.aandios.nous.api.market.model.trading.TradeSide
import kotlinx.serialization.Serializable

@Serializable
data class LiquidationOrder(
    val symbol: String,
    val price: Double,
    val quantity: Double,
    val timestamp: Long,
    val side: TradeSide,
    val orderType: String = ""
)
