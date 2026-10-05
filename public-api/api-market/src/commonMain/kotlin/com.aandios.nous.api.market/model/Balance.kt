/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model

/**
 * Баланс актива (фьючерсный счёт).
 */
data class Balance(
    val currency: String,
    /** Доступный баланс. */
    val amount: String,
    /** Заморожено (в ордерах). */
    val frozen: String = "0",
    /** Маржа под позициями. */
    val margin: String = "0",
    /** Equity счёта. */
    val equity: String = "0",
    /** Нереализованный PnL. */
    val unrealizedPnl: String = "0",
)
