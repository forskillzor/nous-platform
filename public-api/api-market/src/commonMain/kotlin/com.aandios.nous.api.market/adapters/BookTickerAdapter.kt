/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.adapters

import com.aandios.nous.api.market.model.BookTicker
import kotlinx.coroutines.flow.Flow

interface BookTickerAdapter: MarketAdapter {
    fun subscribeToBookTicker(symbol: String): Flow<BookTicker>
    suspend fun getBookTickerRest(symbol: String): BookTicker?
}