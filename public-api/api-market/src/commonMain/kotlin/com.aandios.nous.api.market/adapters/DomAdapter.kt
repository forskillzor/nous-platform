/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.adapters

import com.aandios.nous.api.market.model.orderbook.BookWindowLevels
import kotlinx.coroutines.flow.Flow

interface DomAdapter: MarketAdapter {
    /**
     * Подписка на окна стакана (partial-стрим, самодостаточные срезы топ-N уровней).
     * Для глубины больше 20 поставщик может отдать более широкое окно —
     * обрезка до запрошенной глубины выполняется потребителем.
     */
    suspend fun subscribeToBookWindow(symbol: String, depth: Int): Flow<BookWindowLevels>
}
