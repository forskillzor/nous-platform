/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model.orderbook

/**
 * Один уровень стакана с абсолютным объёмом.
 */
data class PriceUpdate(
    val price: Double,
    val quantity: Double,
)

/**
 * Окно стакана из partial-стрима (Binance `depth<levels>@100ms`).
 *
 * Каждое сообщение — самодостаточный срез топ-N уровней с абсолютными
 * объёмами: никакой синхронизации с REST-снапшотом не требуется, окно
 * просто заменяет текущую книгу. Уровни, выпавшие из топ-N, в окне
 * отсутствуют.
 */
data class BookWindowLevels(
    val bids: List<PriceUpdate>,
    val asks: List<PriceUpdate>,
) {
    companion object {
        /** Парсит сырые строки вида `["price", "quantity"]`, битые — пропускает. */
        fun parseRows(rows: List<List<String>>): List<PriceUpdate> {
            val result = ArrayList<PriceUpdate>(rows.size)
            rows.forEach { row ->
                val price = row.getOrNull(0)?.toDoubleOrNull()
                val quantity = row.getOrNull(1)?.toDoubleOrNull()
                if (price != null && quantity != null) {
                    result.add(PriceUpdate(price, quantity))
                }
            }
            return result
        }
    }
}
