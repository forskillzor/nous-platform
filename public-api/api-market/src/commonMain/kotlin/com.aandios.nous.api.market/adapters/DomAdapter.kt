/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.adapters

import com.aandios.nous.api.market.model.orderbook.BookWindowLevels
import kotlinx.coroutines.flow.Flow

interface DomAdapter: MarketAdapter {
    /**
     * Подписка на окна стакана (самодостаточные срезы топ-N уровней).
     *
     * [depth] — число СЫРЫХ уровней: DOM передаёт `depth × aggregation`
     * (например, agg 10× и depth 20 → 200). Поставщик может отдать более
     * широкое окно — обрезка до запрошенной глубины выполняется потребителем.
     *
     * Наружу всегда отдаются полные окна (snapshot-семантика), независимо от
     * внутреннего режима провайдера (partial/diff).
     */
    suspend fun subscribeToBookWindow(symbol: String, depth: Int): Flow<BookWindowLevels>
}
