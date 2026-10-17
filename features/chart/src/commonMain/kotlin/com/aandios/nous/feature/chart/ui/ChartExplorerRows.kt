/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.api.market.Provider
import com.aandios.nous.api.market.ProviderRegistry
import com.aandios.nous.api.market.adapters.MarketOverviewAdapter
import com.aandios.nous.api.market.model.SymbolInfo
import com.aandios.nous.core.data.repository.SymbolInfoRepositoryImpl
import com.aandios.nous.core.ui.component.SymbolExplorerRow
import kotlin.coroutines.cancellation.CancellationException

/**
 * Строки расширенного списка символов (explorer dropdown в тайтле chart):
 * по всем провайдерам реестра — спеки символов (SymbolInfoAdapter) плюс
 * статистика 24ч (MarketOverviewAdapter через bookTicker).
 *
 * displaySymbol получает суффикс ".P" у perpetual-контрактов (стиль MW),
 * [SymbolExplorerRow.symbol] остаётся «сырым» (что кладём в chart).
 * Провайдер без данных не ломает список — просто пропускается.
 */
suspend fun buildExplorerRows(registry: ProviderRegistry): List<SymbolExplorerRow> =
    buildList {
        registry.providers.forEach { provider ->
            try {
                addAll(provider.toExplorerRows())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // биржа недоступна — остальные строки всё равно показываем
            }
        }
    }

private suspend fun Provider.toExplorerRows(): List<SymbolExplorerRow> {
    // Оба источника опциональны: сбой любого не скрывает строки другого
    val infos: Map<String, SymbolInfo> = try {
        symbolInfo?.let { SymbolInfoRepositoryImpl(it).getAllSymbolsInfo() }
            ?.filter { it.status == "TRADING" }
            ?.associateBy { it.symbol }
            .orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyMap()
    }
    val stats = try {
        (bookTicker as? MarketOverviewAdapter)?.getMarketOverview().orEmpty()
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        emptyList()
    }.associateBy { it.symbol }
    val symbols = (infos.keys + stats.keys).sorted()
    return symbols.map { symbol ->
        val info = infos[symbol]
        val stat = stats[symbol]
        val perpetual = info?.contractType == "PERPETUAL"
        SymbolExplorerRow(
            symbol = symbol,
            displaySymbol = symbol + if (perpetual) ".P" else "",
            exchangeId = providerId,
            exchangeLabel = config.displayName.uppercase(),
            isPerpetual = perpetual,
            quoteAsset = info?.quoteAsset,
            tickSize = info?.tickSize,
            lastPrice = stat?.lastPrice,
            changePercent24h = stat?.changePercent24h,
            quoteVolume24h = stat?.quoteVolume24h,
        )
    }
}
