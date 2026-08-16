/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.data.repository

import com.aandios.nous.api.market.adapters.SymbolInfoAdapter
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.core.domain.repository.SymbolInfoRepository

class SymbolInfoRepositoryImpl(
    private val symbolInfoAdapter: SymbolInfoAdapter
) : SymbolInfoRepository {

    override suspend fun getSymbolInfo(symbol: String): SymbolInfo? {
        return symbolInfoAdapter.getSymbolInfo(symbol)
    }

    override suspend fun getAllSymbolsInfo(): List<SymbolInfo> {
        return symbolInfoAdapter.getAllSymbolsInfo()
    }
}