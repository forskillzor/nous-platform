/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.domain.repository

import com.aandios.nous.api.market.model.trades.Trade
import kotlinx.coroutines.flow.Flow

interface TradesRepository {
    fun getTradesStream(symbol: String): Flow<Trade>
}