/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import com.aandios.nous.api.market.model.MarketTickerStats
import com.aandios.nous.core.market.MarketOverviewStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class MarketWatchRow(
    val exchangeId: String,
    val exchangeName: String,
    val symbol: String,
    val stats: MarketTickerStats?,
)

data class MarketWatchState(
    val rows: List<MarketWatchRow> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * Проекция общего [MarketOverviewStore] на форму Market Watch-панели:
 * загрузка, прелоад при старте и цикл обновления живут в сторе,
 * здесь — только маппинг в [MarketWatchState].
 */
class MarketWatchStore(store: MarketOverviewStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val state: StateFlow<MarketWatchState> = store.state
        .map { overview ->
            MarketWatchState(
                rows = overview.rows.map { row ->
                    MarketWatchRow(
                        exchangeId = row.exchangeId,
                        exchangeName = row.exchangeName,
                        symbol = row.symbol,
                        stats = row.stats,
                    )
                },
                loading = overview.loading,
                error = overview.error,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, MarketWatchState())
}
