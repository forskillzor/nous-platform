/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

import com.aandios.nous.api.market.model.orderbook.BookWindowLevels
import com.aandios.nous.api.market.model.orderbook.PriceUpdate

/**
 * Локальная книга MEXC для инкрементального канала `push.depth`.
 *
 * Диффы несут АБСОЛЮТНЫЕ количества по цене (0 = уровень снят), а [version]
 * строго инкрементальная: следующее событие должно быть ровно version+1,
 * иначе книга требует ресинка из REST-снапшота (см. MexcDomAdapter).
 *
 * Bids и asks хранятся раздельно — сторона уровня определяется каналом,
 * поэтому окно не может «перемешать» стороны вокруг спреда.
 */
class MexcDepthBook {

    enum class ApplyResult { APPLIED, IGNORED, GAP }

    private val bidLevels = HashMap<Double, Double>()
    private val askLevels = HashMap<Double, Double>()

    /** Версия книги; -1 — снапшот ещё не применён. */
    var version: Long = -1L
        private set

    /** Полный снапшот (REST `contract/depth` или первый полный срез). */
    fun reset(asks: List<List<Double>>, bids: List<List<Double>>, version: Long) {
        askLevels.clear()
        bidLevels.clear()
        applyRows(askLevels, asks)
        applyRows(bidLevels, bids)
        this.version = version
    }

    /**
     * Применить инкремент: IGNORED — версия не новее текущей,
     * GAP — разрыв последовательности (нужен ресинк), APPLIED — ок.
     */
    fun apply(asks: List<List<Double>>, bids: List<List<Double>>, version: Long): ApplyResult {
        if (this.version >= 0L && version <= this.version) return ApplyResult.IGNORED
        if (this.version >= 0L && version != this.version + 1L) return ApplyResult.GAP
        applyRows(askLevels, asks)
        applyRows(bidLevels, bids)
        this.version = version
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

    private fun applyRows(target: HashMap<Double, Double>, rows: List<List<Double>>) {
        rows.forEach { row ->
            val price = row.getOrNull(0) ?: return@forEach
            val quantity = row.getOrNull(1) ?: return@forEach
            if (quantity == 0.0) target.remove(price) else target[price] = quantity
        }
    }
}
