/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.market

import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.api.market.adapters.MarketOverviewAdapter
import com.aandios.nous.api.market.model.MarketTickerStats
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.core.data.repository.SymbolInfoRepositoryImpl
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Строка общего каталога рынка: спецификация инструмента (SymbolInfo) и
 * статистика 24ч, слитые по символу. [exchangeName] — «сырое» displayName
 * биржи, регистр/суффиксы — дело потребителя.
 */
data class MarketOverviewRow(
    val symbol: String,
    val exchangeId: String,
    val exchangeName: String,
    val isPerpetual: Boolean = true,
    val quoteAsset: String? = null,
    val tickSize: Double? = null,
    val stats: MarketTickerStats? = null,
)

data class MarketOverviewState(
    val rows: List<MarketOverviewRow> = emptyList(),
    val loading: Boolean = true,
    val error: String? = null,
)

/**
 * Единый источник данных о рынке по всем биржам реестра: спеки символов
 * (SymbolInfoAdapter) плюс статистика 24ч (MarketOverviewAdapter через
 * bookTicker). Прелоадится при старте приложения и обновляется циклом
 * [refreshIntervalMs]; сбой одной биржи или источника не скрывает
 * остальные строки.
 *
 * Потребители (explorer-дропдаун chart, Market Watch) видят только
 * [state] и не знают, как и откуда берутся данные — REST-цикл спрятан
 * здесь; сюда же позже добавятся WS-стримы без правок потребителей.
 */
class MarketOverviewStore(
    private val registry: ProviderRegistry,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val refreshIntervalMs: Long = DEFAULT_REFRESH_INTERVAL_MS,
) {
    companion object {
        const val DEFAULT_REFRESH_INTERVAL_MS = 30_000L
    }

    private val _state = MutableStateFlow(MarketOverviewState())
    val state: StateFlow<MarketOverviewState> = _state.asStateFlow()

    private var started = false

    /** Прелоад при старте приложения + фоновый цикл обновления. Идемпотентен. */
    fun start() {
        if (started) return
        started = true
        scope.launch {
            while (isActive) {
                refresh()
                delay(refreshIntervalMs)
            }
        }
    }

    /**
     * Один цикл обновления. Немедленная перезагрузка не ждёт интервала.
     * Ошибки источников собираются в [MarketOverviewState.error].
     */
    suspend fun refresh() {
        _state.value = _state.value.copy(loading = true)
        val failures = mutableListOf<String>()
        val rows = buildList {
            registry.providers.forEach { provider ->
                try {
                    addAll(provider.toOverviewRows(failures))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failures += "${provider.config.displayName}: ${e.message ?: "market data unavailable"}"
                }
            }
        }
        _state.value = MarketOverviewState(
            rows = rows,
            loading = false,
            error = failures.takeIf { it.isNotEmpty() }?.joinToString("\n"),
        )
    }

    private suspend fun Provider.toOverviewRows(failures: MutableList<String>): List<MarketOverviewRow> {
        // Оба источника опциональны: сбой любого не скрывает строки другого
        val infos: Map<String, SymbolInfo> = try {
            symbolInfo?.let { SymbolInfoRepositoryImpl(it).getAllSymbolsInfo() }
                ?.filter { it.status == "TRADING" }
                ?.associateBy { it.symbol }
                .orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failures += "${config.displayName}: symbols unavailable (${e.message ?: "error"})"
            emptyMap()
        }
        val stats = try {
            (bookTicker as? MarketOverviewAdapter)?.getMarketOverview().orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failures += "${config.displayName}: 24h stats unavailable (${e.message ?: "error"})"
            emptyList()
        }.associateBy { it.symbol }

        val symbols = (infos.keys + stats.keys).sorted()
        return symbols.map { symbol ->
            val info = infos[symbol]
            MarketOverviewRow(
                symbol = symbol,
                exchangeId = providerId,
                exchangeName = config.displayName,
                isPerpetual = info?.contractType == "PERPETUAL",
                quoteAsset = info?.quoteAsset,
                tickSize = info?.tickSize,
                stats = stats[symbol],
            )
        }
    }
}
