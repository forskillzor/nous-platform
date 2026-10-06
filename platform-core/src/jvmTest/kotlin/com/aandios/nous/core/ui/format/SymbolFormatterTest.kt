/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.format

import kotlin.test.Test
import kotlin.test.assertEquals

class SymbolFormatterTest {

    @Test
    fun `tiny values print without exponent`() {
        // Контрактные рынки MEXC: шаг BTC = 0.0001 (toString даёт 1.0E-4)
        assertEquals("0.0001", plainDecimalString(0.0001))
        assertEquals("0.0001", plainDecimalString(1.0E-4))
        assertEquals("0.000025", plainDecimalString(2.5E-5))
        assertEquals("0.001", plainDecimalString(0.001))
        assertEquals("10", plainDecimalString(10.0))
        assertEquals("0", plainDecimalString(0.0))
    }

    @Test
    fun `formatter volume for contract min sizes`() {
        val btc = SymbolFormatter(tickSize = 0.1, minQty = 0.0001)
        assertEquals("0.0001", btc.formatVolume(0.0001))
        assertEquals("0.0001", btc.formatVolumeFull(0.0001))

        val sol = SymbolFormatter(tickSize = 0.01, minQty = 0.1)
        assertEquals("1", sol.formatVolume(1.0))
        assertEquals("0.3", sol.formatVolume(0.3))
    }

    @Test
    fun `formatter prices unchanged`() {
        val btc = SymbolFormatter(tickSize = 0.1, minQty = 0.0001)
        assertEquals("85578.7", btc.formatPrice(85578.66))
        assertEquals(85578.7, btc.roundPrice(85578.66))
    }
}
