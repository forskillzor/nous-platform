/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.data.repository

import com.aandios.nous.api.market.adapters.BookTickerAdapter
import com.aandios.nous.api.market.model.BookTicker
import com.aandios.nous.core.domain.repository.BookTickerRepository
import kotlinx.coroutines.flow.Flow

class BookTickerRepositoryImpl(
    private val bookTicker: BookTickerAdapter
) : BookTickerRepository {

    override fun getBookTicker(symbol: String): Flow<BookTicker> {
        return bookTicker.subscribeToBookTicker(symbol)
    }
}