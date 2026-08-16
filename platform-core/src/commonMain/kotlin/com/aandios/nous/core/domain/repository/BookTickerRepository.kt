/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.domain.repository

import com.aandios.nous.api.market.model.BookTicker
import kotlinx.coroutines.flow.Flow

interface BookTickerRepository {

    fun getBookTicker(symbol: String): Flow<BookTicker>
}
