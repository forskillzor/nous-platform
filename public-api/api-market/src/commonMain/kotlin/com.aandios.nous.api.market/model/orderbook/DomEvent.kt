/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model.orderbook

import com.aandios.nous.api.market.model.BookTicker

/**
 * События стакана котировок.
 *
 * Источник — partial-стрим Binance (`depth<levels>@100ms`): каждое окно
 * полностью заменяет книгу, инкрементальной синхронизации нет.
 */
sealed class DomEvent {
    /**
     * Новое окно книги (топ-N уровней с абсолютными объёмами).
     * UI должен заменить текущие уровни содержимым окна.
     */
    data class BookWindow(
        val bids: List<PriceUpdate>,
        val asks: List<PriceUpdate>,
    ) : DomEvent()

    /**
     * Обновление лучших цен (best bid / best ask) и последней сделки.
     * Используется для якоря лесенки и подсветки строки последней цены.
     */
    data class BestPrices(
        val bestBid: Double,
        val bestBidQuantity: Double,
        val bestAsk: Double,
        val bestAskQuantity: Double,
        val lastPrice: Double,
        val symbol: String
    ) : DomEvent()

    companion object {
        fun fromWindow(window: BookWindowLevels): DomEvent {
            return BookWindow(window.bids, window.asks)
        }

        fun fromBookTicker(bookTicker: BookTicker, symbol: String): DomEvent {
            return BestPrices(
                bestBid = bookTicker.bestBid,
                bestBidQuantity = bookTicker.bestBidQty,
                bestAsk = bookTicker.bestAsk,
                bestAskQuantity = bookTicker.bestAskQty,
                lastPrice = bookTicker.lastPrice,
                symbol = symbol
            )
        }
    }
}
