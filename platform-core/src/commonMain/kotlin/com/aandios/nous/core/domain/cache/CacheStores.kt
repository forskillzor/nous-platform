/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.domain.cache

import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.FootprintCandle

/**
 * Кэш свечей на диске. Реализуется LocalStorage (SQLite).
 * Ключ: exchange + symbol + timeframe.
 */
interface CandleCacheStore {
    suspend fun saveCandles(exchange: String, symbol: String, timeframe: String, candles: List<Candle>)
    suspend fun getCandles(exchange: String, symbol: String, timeframe: String, limit: Int = 500): List<Candle>
}

/**
 * Кэш footprint-свечей на диске. Ключ: exchange + symbol.
 */
interface FootprintCacheStore {
    suspend fun saveFootprintCandles(exchange: String, symbol: String, candles: List<FootprintCandle>)
    suspend fun getFootprintCandles(exchange: String, symbol: String, limit: Int = 200): List<FootprintCandle>
}
