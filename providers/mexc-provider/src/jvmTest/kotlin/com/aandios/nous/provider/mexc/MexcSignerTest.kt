/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc

import kotlin.test.Test
import kotlin.test.assertEquals

class MexcSignerTest {

    @Test
    fun `hmac sha256 matches RFC 4231 test vector`() {
        // RFC 4231, Test Case 2: key = "Jefe" (ASCII), data = "what do ya want for nothing?"
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            hmacSha256Hex("Jefe", "what do ya want for nothing?"),
        )
    }

    @Test
    fun `param string sorts keys and url-encodes values`() {
        val params = mapOf(
            "symbol" to "BTC_USDT",
            "page_size" to "100",
            "alpha" to "x y",
        )
        assertEquals("alpha=x%20y&page_size=100&symbol=BTC_USDT", MexcSigner.getParamString(params))
    }

    @Test
    fun `param string skips empty values`() {
        assertEquals("a=1", MexcSigner.getParamString(mapOf("a" to "1", "b" to "", "c" to "")))
        assertEquals("", MexcSigner.getParamString(emptyMap()))
    }

    @Test
    fun `formatDecimal expands scientific notation`() {
        assertEquals("0.00001", formatDecimal(1e-5))
        assertEquals("0.1", formatDecimal(0.1))
        assertEquals("123.456", formatDecimal(123.456))
        assertEquals("0", formatDecimal(0.0))
        assertEquals("100000", formatDecimal(1e5))
        assertEquals("1.5", formatDecimal(1.5))
    }

    @Test
    fun `roundToTick rounds to tick step`() {
        assertEquals(85578.6, roundToTick(85578.64, 0.1))
        assertEquals(85578.7, roundToTick(85578.66, 0.1))
        assertEquals(100.0, roundToTick(100.0001, 0.5))
        assertEquals(123.45, roundToTick(123.45, 0.0)) // без тика — как есть
    }

    @Test
    fun `symbol conversion`() {
        assertEquals("BTC_USDT", toMexcSymbol("BTCUSDT"))
        assertEquals("BTCUSDT", fromMexcSymbol("BTC_USDT"))
        assertEquals("1000PEPE_USDT", toMexcSymbol("1000PEPEUSDT"))
    }
}
