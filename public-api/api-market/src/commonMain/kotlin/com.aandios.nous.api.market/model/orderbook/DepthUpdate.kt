/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model.orderbook

import kotlinx.serialization.SerialName

data class DepthUpdate(
    val firstUpdateId: Long,
    val finalUpdateId: Long,
    val previousFinalUpdateId: Long,
    val bids: List<List<String>>,
    val asks: List<List<String>>
)
