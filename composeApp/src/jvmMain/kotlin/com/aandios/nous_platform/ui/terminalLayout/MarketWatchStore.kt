/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.api.market.adapters.MarketOverviewAdapter
import com.aandios.nous.api.market.model.MarketTickerStats
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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

class MarketWatchStore(
    private val registry: ProviderRegistry,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(MarketWatchState())
    val state: StateFlow<MarketWatchState> = _state.asStateFlow()

    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (isActive) {
                refresh()
                delay(REFRESH_INTERVAL_MS)
            }
        }
    }

    private suspend fun refresh() {
        _state.value = _state.value.copy(loading = true)
        val failures = mutableListOf<String>()
        val refreshed = buildList {
            registry.providers.forEach { provider ->
                try {
                    addAll(provider.toMarketWatchRows())
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures += "${provider.config.displayName}: ${e.message ?: "market data unavailable"}"
                }
            }
        }
        _state.value = MarketWatchState(
            rows = refreshed,
            loading = false,
            error = failures.takeIf { it.isNotEmpty() }?.joinToString("\n"),
        )
    }

    private suspend fun Provider.toMarketWatchRows(): List<MarketWatchRow> {
        val symbols = symbolInfo?.getAllSymbolsInfo()
            ?.filter { it.status == "TRADING" }
            ?.map { it.symbol }
            .orEmpty()
        val stats = (bookTicker as? MarketOverviewAdapter)?.getMarketOverview().orEmpty()
            .associateBy { it.symbol }
        return (symbols + stats.keys).distinct().map { symbol ->
            MarketWatchRow(
                exchangeId = providerId,
                exchangeName = config.displayName,
                symbol = symbol,
                stats = stats[symbol],
            )
        }
    }

    private companion object {
        const val REFRESH_INTERVAL_MS = 30_000L
    }
}
