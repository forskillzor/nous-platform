/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model.trading

/**
 * Результат размещения ордера.
 */
data class OrderResponse(
    val orderId: String,
    val price: Double = 0.0,
    val success: Boolean = true,
    /** Сообщение об ошибке/предупреждении (пусто при успехе). */
    val message: String? = null,
)
