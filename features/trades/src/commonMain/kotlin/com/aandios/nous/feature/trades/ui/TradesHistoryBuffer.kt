/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.ui

import com.aandios.nous.api.market.model.trades.Trade

/**
 * Три уровня retention для Trades-панели:
 *
 *  * [maxRaw] — сырая история (в UI не отдаётся). Источник для reseed при
 *    смене фильтра: редкие крупные сделки не теряются вместе с коротким
 *    отображаемым фидом.
 *  * [maxFeed] — отображаемый фид для режима ALL.
 *  * [maxFiltered] — отображаемый список сделок, прошедших фильтр.
 *    Retention применяется ПОСЛЕ фильтрации.
 *
 * Все списки newest-first. Вставка/вытеснение — O(1) через [ArrayDeque].
 */
internal class TradesHistoryBuffer(
    private val maxRaw: Int = 20_000,
    private val maxFeed: Int = 500,
    private val maxFiltered: Int = 5_000,
) {
    private val raw = ArrayDeque<Trade>()
    private val feed = ArrayDeque<Trade>()
    private val filtered = ArrayDeque<Trade>()

    /**
     * Добавляет сделку во все уровни.
     * @return true, если изменился filtered-список (нужно опубликовать в UI).
     */
    fun add(trade: Trade, filterActive: Boolean, matches: (Trade) -> Boolean): Boolean {
        push(raw, maxRaw, trade)
        push(feed, maxFeed, trade)
        if (!filterActive || !matches(trade)) return false
        push(filtered, maxFiltered, trade)
        return true
    }

    /** Пересобирает filtered из raw (смена фильтра / уточнение SymbolInfo). */
    fun reseed(filterActive: Boolean, matches: (Trade) -> Boolean) {
        filtered.clear()
        if (!filterActive) return
        raw.asSequence()
            .filter(matches)
            .take(maxFiltered)
            .forEach { filtered.addLast(it) }
    }

    fun clear() {
        raw.clear()
        feed.clear()
        filtered.clear()
    }

    fun feedSnapshot(): List<Trade> = feed.toList()

    fun filteredSnapshot(): List<Trade> = filtered.toList()

    internal val rawSize: Int get() = raw.size

    internal fun rawSnapshot(): List<Trade> = raw.toList()

    private fun push(target: ArrayDeque<Trade>, limit: Int, trade: Trade) {
        target.addFirst(trade)
        while (target.size > limit) target.removeLast()
    }
}
