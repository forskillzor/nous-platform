/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.model

/**
 * Лучшие цены и объёмы — одно состояние вместо шести отдельных StateFlow:
 * на каждый тик одна запись состояния вместо шести инвалидаций.
 */
data class BestPricesState(
    val bestBid: Double? = null,
    val bestAsk: Double? = null,
    val bestBidQuantity: Double? = null,
    val bestAskQuantity: Double? = null,
    val bestBidDisplayTicks: Long? = null,
    val bestAskDisplayTicks: Long? = null,
    /** Последняя цена сделки в корзине агрегации — подсветка строки лесенки. */
    val lastPriceDisplayTicks: Long? = null,
)
