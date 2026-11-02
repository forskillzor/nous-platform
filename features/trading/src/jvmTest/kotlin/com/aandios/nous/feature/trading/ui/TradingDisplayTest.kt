/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trading.ui

import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.trading.Position
import kotlin.test.Test
import kotlin.test.assertEquals

class TradingDisplayTest {
    @Test
    fun `position pnl prefers unrealized over realized`() {
        val position = Position(symbol = "BTC", pnl = 0.0, unrealizedPnl = -0.084)
        assertEquals(-0.084, positionDisplayPnl(position))
    }

    @Test
    fun `position pnl falls back to realized when unrealized is unavailable`() {
        val position = Position(symbol = "BTC", pnl = 1.5, unrealizedPnl = 0.0)
        assertEquals(1.5, positionDisplayPnl(position))
    }

    @Test
    fun `header balance uses equity instead of available margin`() {
        val balance = Balance(
            currency = "USDC",
            amount = "0",
            margin = "1.8262",
            equity = "1.7792",
        )
        assertEquals("1.7792", balanceDisplayValue(balance))
    }

    @Test
    fun `header balance falls back to amount when equity is zero`() {
        val balance = Balance(currency = "USDC", amount = "5", equity = "0")
        assertEquals("5", balanceDisplayValue(balance))
    }

    @Test
    fun `daily pnl currency follows the primary balance`() {
        assertEquals("USDC", dailyPnlCurrency(Balance(currency = "USDC", amount = "1")))
        assertEquals("USDT", dailyPnlCurrency(null))
    }
}
