/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.model

import com.aandios.nous.api.market.model.orderbook.BookWindowLevels
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Окно стакана partial-стрима Binance Futures (`<symbol>@depth<levels>@100ms`).
 *
 * Каждое сообщение — самодостаточный срез топ-N уровней с абсолютными
 * объёмами. Служебные поля U/u/pu игнорируются — синхронизация не нужна.
 */
@Serializable
data class BBookWindow(
    @SerialName("b") val bids: List<List<String>>,
    @SerialName("a") val asks: List<List<String>>
) {
    fun toBookWindowLevels() = BookWindowLevels(
        bids = BookWindowLevels.parseRows(bids),
        asks = BookWindowLevels.parseRows(asks)
    )
}
