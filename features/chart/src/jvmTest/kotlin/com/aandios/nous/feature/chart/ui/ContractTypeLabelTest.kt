/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.api.market.model.SymbolInfo
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ContractTypeLabelTest {

    private fun info(
        quoteAsset: String = "USDT",
        contractType: String? = "PERPETUAL",
        marginAsset: String? = "USDT",
    ) = SymbolInfo(
        symbol = "BTCUSDT",
        tickSize = 0.01,
        stepSize = 0.001,
        minQty = 0.001,
        minNotional = 10.0,
        status = "TRADING",
        baseAsset = "BTC",
        quoteAsset = quoteAsset,
        contractType = contractType,
        marginAsset = marginAsset,
    )

    @Test
    fun `usd-m perpetual uses margin asset`() {
        assertEquals("USDT-M Perp", contractTypeLabel(info()))
    }

    @Test
    fun `coin-m perpetual uses quote asset when margin missing`() {
        assertEquals("USD-M Perp", contractTypeLabel(info(quoteAsset = "USD", marginAsset = null)))
    }

    @Test
    fun `spot has no contract label`() {
        assertNull(contractTypeLabel(info(contractType = null, marginAsset = null)))
    }

    @Test
    fun `null info returns null`() {
        assertNull(contractTypeLabel(null))
    }
}
