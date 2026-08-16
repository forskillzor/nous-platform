/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.domain

import com.aandios.nous.api.market.model.orderbook.OrderBookLevel
import com.aandios.nous.feature.dom.domain.model.AggregationLevel

/**
 * Унифицированное представление стакана котировок, где каждый уровень содержит
 * как bid, так и ask объемы для данной цены.
 *
 * Эта модель предназначена для передачи в UI-слой, чтобы избежать преобразований
 * на стороне UI и централизовать бизнес-логику в репозитории.
 */
data class OrderBook(
    val symbol: String,
    val levels: List<OrderBookLevel>,
    val timestamp: Long,
    val bestBid: Double?,
    val bestAsk: Double?,
    val spread: Double?,
    val spreadPercent: Double?
) {

    /**
     * Возвращает максимальный объем среди всех bid и ask для масштабирования визуализации.
     */
    fun maxVolume(): Double {
        return levels.maxOfOrNull { level ->
            maxOf(
                level.bidQty.toDoubleOrNull() ?: 0.0,
                level.askQty.toDoubleOrNull() ?: 0.0
            )
        } ?: 1.0
    }
    
    /**
     * Создает новый UnifiedOrderBook с агрегированными уровнями по заданному уровню агрегации.
     * @param aggregationLevel уровень агрегации (множитель тика)
     * @param baseTickSize базовый тик инструмента (из API биржи)
     * @return новый UnifiedOrderBook с агрегированными уровнями
     */
    fun aggregate(aggregationLevel: AggregationLevel, baseTickSize: Double): OrderBook {
        val aggregatedLevels = DomAggregator
            .aggregateUnifiedLevels(levels, aggregationLevel, baseTickSize)
        
        // Агрегируем лучшие цены с учетом уровня агрегации
        val aggregatedBestBid = bestBid?.let { aggregationLevel.roundDown(it, baseTickSize) }
        val aggregatedBestAsk = bestAsk?.let { aggregationLevel.roundDown(it, baseTickSize) }
        val aggregatedSpread = if (aggregatedBestBid != null && aggregatedBestAsk != null) 
            aggregatedBestAsk - aggregatedBestBid else null
        val aggregatedSpreadPercent = if (aggregatedBestBid != null && aggregatedSpread != null)
            (aggregatedSpread / aggregatedBestBid) * 100 else null
        
        return copy(
            levels = aggregatedLevels,
            bestBid = aggregatedBestBid,
            bestAsk = aggregatedBestAsk,
            spread = aggregatedSpread,
            spreadPercent = aggregatedSpreadPercent
        )
    }

}