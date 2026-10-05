/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model.trading

import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType

/**
 * Заявка на размещение ордера.
 * Единый формат для всех провайдеров; провайдер маппит в свою схему.
 */
data class OrderRequest(
    val symbol: String,
    val side: OrderSide,
    val orderType: OrderType,
    val quantity: Double,
    val price: Double = 0.0,
    /** Только уменьшение позиции (reduce-only). */
    val reduceOnly: Boolean = false,
    /** id позиции, которую нужно закрыть (для close-ордеров). */
    val positionId: Long? = null,
    /** Пользовательский id ордера (externalOid). */
    val clientOrderId: String? = null,
    /** Плечо (для изолированной маржи). */
    val leverage: Int? = null,
    /** Режим маржи: 1 — изолированная, 2 — кросс. */
    val marginMode: Int? = null,
    /** Режим позиций: 1 — hedge, 2 — one-way. */
    val positionMode: Int? = null,
    val stopLossPrice: Double? = null,
    val takeProfitPrice: Double? = null,
)
