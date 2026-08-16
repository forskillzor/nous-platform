/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model.orderbook

import com.aandios.nous.api.market.model.BookTicker

/**
 * События для инкрементального обновления DOM (стакана котировок).
 * Вместо публикации полного снапшота при каждом изменении,
 * репозиторий эмитит эти события, которые UI может применять
 * к локальным изменяемым коллекциям.
 */
sealed class DomEvent {
    /**
     * Начальный снапшот стакана. Содержит все текущие уровни.
     * При получении этого события UI должен очистить текущие коллекции
     * и загрузить новые данные из снапшота.
     */
    data class Snapshot(
        val snapshot: DepthSnapshot,
        val symbol: String
    ) : DomEvent()

    /**
     * Обновление уровня bid (покупка).
     * @param price цена уровня
     * @param quantity новый объём (0.0 означает удаление уровня)
     */
    data class UpdateBid(
        val price: Double,
        val quantity: Double
    ) : DomEvent()

    /**
     * Обновление уровня ask (продажа).
     * @param price цена уровня
     * @param quantity новый объём (0.0 означает удаление уровня)
     */
    data class UpdateAsk(
        val price: Double,
        val quantity: Double
    ) : DomEvent()

    /**
     * Обновление лучших цен (best bid / best ask).
     * Используется для обрезки отображаемых уровней.
     */
    data class BestPrices(
        val bestBid: Double,
        val bestBidQuantity: Double,
        val bestAsk: Double,
        val bestAskQuantity: Double,
        val symbol: String
    ) : DomEvent()

    /**
     * Сброс состояния. Используется при переподписке или ошибке.
     */
    object Reset : DomEvent()

    /**
     * Преобразует DepthUpdate в список DomEvent.
     * Каждое изменение цены в DepthUpdate преобразуется в отдельное событие.
     * Некорректные данные логируются и пропускаются.
     */
    companion object {
        /**
         * Преобразует DepthUpdate в список DomEvent.
         * Каждое изменение цены в DepthUpdate преобразуется в отдельное событие.
         * Некорректные данные логируются и пропускаются.
         */
        fun fromDepthUpdate(update: DepthUpdate, symbol: String): List<DomEvent> {
            val events = mutableListOf<DomEvent>()

            // Обрабатываем bids
            update.bids.forEach { (priceStr, qtyStr) ->
                val price = priceStr.toDoubleOrNull()
                val quantity = qtyStr.toDoubleOrNull()

                if (price == null || quantity == null) {
                    println("⚠️ DomEvent: Failed to parse bid data: price='$priceStr', quantity='$qtyStr' for symbol $symbol")
                    return@forEach
                }

                events.add(UpdateBid(price, quantity))
            }

            // Обрабатываем asks
            update.asks.forEach { (priceStr, qtyStr) ->
                val price = priceStr.toDoubleOrNull()
                val quantity = qtyStr.toDoubleOrNull()

                if (price == null || quantity == null) {
                    println("⚠️ DomEvent: Failed to parse ask data: price='$priceStr', quantity='$qtyStr' for symbol $symbol")
                    return@forEach
                }

                events.add(UpdateAsk(price, quantity))
            }

            return events
        }

        /**
         * Эмитит события depth update через callback без создания промежуточного списка.
         * Альтернатива [fromDepthUpdate] для случаев, где важна производительность.
         */
        inline fun emitDepthUpdates(
            update: DepthUpdate,
            symbol: String,
            emit: (DomEvent) -> Unit
        ) {
            update.bids.forEach { (priceStr, qtyStr) ->
                val price = priceStr.toDoubleOrNull()
                val quantity = qtyStr.toDoubleOrNull()

                if (price == null || quantity == null) {
                    println("⚠️ DomEvent: Failed to parse bid data: price='$priceStr', quantity='$qtyStr' for symbol $symbol")
                    return@forEach
                }

                emit(UpdateBid(price, quantity))
            }

            update.asks.forEach { (priceStr, qtyStr) ->
                val price = priceStr.toDoubleOrNull()
                val quantity = qtyStr.toDoubleOrNull()

                if (price == null || quantity == null) {
                    println("⚠️ DomEvent: Failed to parse ask data: price='$priceStr', quantity='$qtyStr' for symbol $symbol")
                    return@forEach
                }

                emit(UpdateAsk(price, quantity))
            }
        }

        fun fromBookTicker(bookTicker: BookTicker, symbol: String): DomEvent {
            return BestPrices(
                bestBid = bookTicker.bestBid,
                bestBidQuantity = bookTicker.bestBidQty,
                bestAsk = bookTicker.bestAsk,
                bestAskQuantity = bookTicker.bestAskQty,
                symbol = symbol
            )
        }

        fun fromSnapshot(snapshot: DepthSnapshot, symbol: String): DomEvent {
            return Snapshot(snapshot, symbol)
        }
    }
}