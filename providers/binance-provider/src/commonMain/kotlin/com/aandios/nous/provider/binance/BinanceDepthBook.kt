/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance

import com.aandios.nous.api.market.model.orderbook.BookWindowLevels
import com.aandios.nous.api.market.model.orderbook.PriceUpdate

/**
 * Локальная книга Binance USDT-M для diff-стрима `@depth@100ms`.
 *
 * Синхронизация по документации Binance:
 *  * REST-снапшот даёт [lastUpdateId]; события с `u < lastUpdateId` — стейл;
 *  * первое обработанное событие должно накрывать `lastUpdateId + 1`
 *    (`U <= lastUpdateId + 1 <= u`), иначе был разрыв;
 *  * далее `pu` каждого события обязан равняться `u` предыдущего,
 *    иначе — GAP и нужен новый снапшот.
 *
 * Количества абсолютные, `0` снимает уровень. Bids/asks хранятся раздельно.
 */
class BinanceDepthBook {

    enum class ApplyResult { APPLIED, IGNORED, GAP }

    private val bidLevels = HashMap<Double, Double>()
    private val askLevels = HashMap<Double, Double>()

    /** Последний применённый update id; -1 — снапшот ещё не применён. */
    var lastUpdateId: Long = -1L
        private set

    /** После снапшота ждём первое событие, накрывающее lastUpdateId + 1. */
    private var awaitingFirstEvent = false

    fun reset(
        bids: List<PriceUpdate>,
        asks: List<PriceUpdate>,
        lastUpdateId: Long,
    ) {
        bidLevels.clear()
        askLevels.clear()
        applyRows(bidLevels, bids)
        applyRows(askLevels, asks)
        this.lastUpdateId = lastUpdateId
        awaitingFirstEvent = true
    }

    fun apply(
        bids: List<PriceUpdate>,
        asks: List<PriceUpdate>,
        firstUpdateId: Long,
        finalUpdateId: Long,
        previousFinalUpdateId: Long,
    ): ApplyResult {
        if (lastUpdateId < 0L) return ApplyResult.GAP
        if (finalUpdateId <= lastUpdateId) return ApplyResult.IGNORED

        if (awaitingFirstEvent) {
            // Первое событие должно содержать lastUpdateId + 1 в своём диапазоне.
            if (firstUpdateId > lastUpdateId + 1L) return ApplyResult.GAP
            applyRows(bidLevels, bids)
            applyRows(askLevels, asks)
            lastUpdateId = finalUpdateId
            awaitingFirstEvent = false
            return ApplyResult.APPLIED
        }

        if (previousFinalUpdateId != lastUpdateId) return ApplyResult.GAP
        applyRows(bidLevels, bids)
        applyRows(askLevels, asks)
        lastUpdateId = finalUpdateId
        return ApplyResult.APPLIED
    }

    /** Топ-[depth] уровней: bids по убыванию цены, asks по возрастанию. */
    fun window(depth: Int): BookWindowLevels {
        val n = depth.coerceAtLeast(1)
        val bids = bidLevels.entries
            .filter { it.value > 0.0 }
            .sortedByDescending { it.key }
            .take(n)
            .map { PriceUpdate(it.key, it.value) }
        val asks = askLevels.entries
            .filter { it.value > 0.0 }
            .sortedBy { it.key }
            .take(n)
            .map { PriceUpdate(it.key, it.value) }
        return BookWindowLevels(bids = bids, asks = asks)
    }

    private fun applyRows(target: HashMap<Double, Double>, rows: List<PriceUpdate>) {
        rows.forEach { row ->
            if (row.quantity == 0.0) target.remove(row.price) else target[row.price] = row.quantity
        }
    }
}
