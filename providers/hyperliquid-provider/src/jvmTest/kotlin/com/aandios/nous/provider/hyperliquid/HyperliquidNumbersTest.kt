/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import kotlin.test.Test
import kotlin.test.assertEquals

class HyperliquidNumbersTest {
    @Test
    fun `formats plain decimal without scientific notation`() {
        assertEquals("0.0001", plainHyperliquidDecimal(1.0E-4))
        assertEquals("0.000025", plainHyperliquidDecimal(2.5E-5))
        assertEquals("100000", plainHyperliquidDecimal(1.0E5))
    }

    @Test
    fun `normalizes prices to five significant figures and perp decimals`() {
        assertEquals("100", normalizeHyperliquidPrice(100.0, 5))
        assertEquals("1234.6", normalizeHyperliquidPrice(1234.567, 2))
        assertEquals("0.001234", normalizeHyperliquidPrice(0.0012344, 0))
        assertEquals("97001", normalizeHyperliquidPrice(97000.5, 5))
        assertEquals("0.5", normalizeHyperliquidPrice(0.5, 0))
        assertEquals("7619.7", normalizeHyperliquidPrice(7619.7, 3))
    }

    @Test
    fun `normalizes sizes to lot step`() {
        assertEquals("0.3", normalizeHyperliquidSize(0.3, 1))
        assertEquals("1.235", normalizeHyperliquidSize(1.2345, 3))
    }

    @Test
    fun `derives tick and minimum quantity from price and size decimals`() {
        assertEquals(1.0, hyperliquidTickSize(78065.0, 5), 1e-12)
        assertEquals(0.01, hyperliquidTickSize(200.0, 2), 1e-12)
        assertEquals(1.0E-5, hyperliquidTickSize(0.5, 0), 1e-12)
        assertEquals(0.00013, hyperliquidMinQty(78065.0, 5), 1e-12)
        assertEquals(0.002, hyperliquidMinQty(7619.7, 3), 1e-12)
    }

    @Test
    fun `computes aggressive market prices`() {
        assertEquals(105.0, hyperliquidAggressivePrice(100.0, isBuy = true, slippage = 0.05), 1e-12)
        assertEquals(95.0, hyperliquidAggressivePrice(100.0, isBuy = false, slippage = 0.05), 1e-12)
    }
}
