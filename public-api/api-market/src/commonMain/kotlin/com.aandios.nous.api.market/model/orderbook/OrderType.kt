/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.api.market.model.orderbook

enum class OrderType {
    LIMIT,
    MARKET,
    /** Post-Only maker (снимается, если сразу матчится). */
    POST_ONLY,
    /** Immediate-or-Cancel. */
    IOC,
    /** Fill-or-Kill. */
    FOK,
}
