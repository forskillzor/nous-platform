/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.domain

import com.aandios.nous.api.market.model.orderbook.OrderBookLevel
import com.aandios.nous.feature.dom.domain.model.AggregationLevel

/**
 * Сервис агрегации уровней стакана заявок (DOM) по заданному тику.
 * Объединяет заявки, попадающие в один агрегированный уровень, суммируя их объёмы.
 *
 * Оптимизация: single-pass аккумуляция в LinkedHashMap вместо groupBy + map.
 * Устраняет промежуточные аллокации List для каждой группы.
 */
object DomAggregator {

    /**
     * Агрегирует список уровней стакана по заданному уровню агрегации.
     *
     * @param levels исходный список уровней (может быть как bid, так и ask)
     * @param aggregationLevel уровень агрегации (множитель тика)
     * @param baseTickSize базовый тик инструмента (из API биржи)
     * @return новый список агрегированных уровней, отсортированный в том же порядке, что и исходный
     *         (по убыванию цены для bids, по возрастанию для asks)
     */
    fun aggregateLevels(
        levels: List<OrderBookLevel>,
        aggregationLevel: AggregationLevel,
        baseTickSize: Double
    ): List<OrderBookLevel> {
        if (levels.isEmpty() || baseTickSize <= 0.0) {
            return levels
        }

        // Single-pass аккумуляция: LinkedHashMap сохраняет порядок вставки
        // Вместо groupBy { .. } + map { .. } — один проход, без промежуточных списков
        val aggregated = linkedMapOf<String, AggregatedBucket>()
        for (level in levels) {
            val key = aggregationLevel.aggregationKey(level.price, baseTickSize)
            val bucket = aggregated.getOrPut(key) { AggregatedBucket() }
            bucket.totalQty += level.quantity.toDoubleOrNull() ?: 0.0
            bucket.totalBidQty += level.bidQty.takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 0.0
            bucket.totalAskQty += level.askQty.takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 0.0
        }

        return aggregated.map { (aggregatedPrice, bucket) ->
            OrderBookLevel(
                price = aggregatedPrice,
                quantity = bucket.totalQty.toString(),
                total = "",
                bidQty = if (bucket.totalBidQty > 0.0) bucket.totalBidQty.toString() else "",
                askQty = if (bucket.totalAskQty > 0.0) bucket.totalAskQty.toString() else ""
            )
        }.sortedBy { it.price.toDoubleOrNull() ?: 0.0 }
    }

    /**
     * Агрегирует полный стакан (bids и asks) по заданному уровню агрегации.
     *
     * @param bids список уровней покупок (должны быть отсортированы по убыванию цены)
     * @param asks список уровней продаж (должны быть отсортированы по возрастанию цены)
     * @param aggregationLevel уровень агрегации (множитель тика)
     * @param baseTickSize базовый тик инструмента (из API биржи)
     * @return пара (агрегированные bids, агрегированные asks)
     */
    fun aggregateOrderBook(
        bids: List<OrderBookLevel>,
        asks: List<OrderBookLevel>,
        aggregationLevel: AggregationLevel,
        baseTickSize: Double
    ): Pair<List<OrderBookLevel>, List<OrderBookLevel>> {
        val aggregatedBids = aggregateLevels(bids, aggregationLevel, baseTickSize)
            .sortedByDescending { it.price.toDoubleOrNull() ?: 0.0 }
        val aggregatedAsks = aggregateLevels(asks, aggregationLevel, baseTickSize)
            .sortedBy { it.price.toDoubleOrNull() ?: 0.0 }
        return aggregatedBids to aggregatedAsks
    }

    /**
     * Агрегирует унифицированный стакан (UnifiedOrderBook) по заданному уровню агрегации.
     * Объединяет уровни, которые имеют как bidQty, так и askQty.
     *
     * @param unifiedLevels список унифицированных уровней
     * @param aggregationLevel уровень агрегации (множитель тика)
     * @param baseTickSize базовый тик инструмента (из API биржи)
     * @return агрегированный список унифицированных уровней, отсортированный по убыванию цены
     */
    fun aggregateUnifiedLevels(
        unifiedLevels: List<OrderBookLevel>,
        aggregationLevel: AggregationLevel,
        baseTickSize: Double
    ): List<OrderBookLevel> {
        if (unifiedLevels.isEmpty() || baseTickSize <= 0.0) return emptyList()

        // Single-pass аккумуляция вместо groupBy
        val aggregated = linkedMapOf<String, AggregatedUnifiedBucket>()
        for (level in unifiedLevels) {
            val key = aggregationLevel.aggregationKey(level.price, baseTickSize)
            val bucket = aggregated.getOrPut(key) { AggregatedUnifiedBucket() }
            bucket.totalBidQty += level.bidQty.takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 0.0
            bucket.totalAskQty += level.askQty.takeIf { it.isNotBlank() }?.toDoubleOrNull() ?: 0.0
        }

        return aggregated.map { (aggregatedPrice, bucket) ->
            OrderBookLevel(
                price = aggregatedPrice,
                quantity = "",
                total = "",
                bidQty = if (bucket.totalBidQty > 0.0) bucket.totalBidQty.toString() else "",
                askQty = if (bucket.totalAskQty > 0.0) bucket.totalAskQty.toString() else ""
            )
        }.sortedByDescending { it.price.toDoubleOrNull() ?: 0.0 }
    }

    /** Внутренний класс для накопления сумм в один проход */
    private class AggregatedBucket {
        var totalQty: Double = 0.0
        var totalBidQty: Double = 0.0
        var totalAskQty: Double = 0.0
    }

    /** Внутренний класс для накопления сумм unified levels в один проход */
    private class AggregatedUnifiedBucket {
        var totalBidQty: Double = 0.0
        var totalAskQty: Double = 0.0
    }
}
