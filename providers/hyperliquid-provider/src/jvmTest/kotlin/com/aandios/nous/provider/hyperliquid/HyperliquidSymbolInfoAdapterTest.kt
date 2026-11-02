/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import com.aandios.nous.provider.hyperliquid.adapter.HyperliquidSymbolInfoAdapter
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class HyperliquidSymbolInfoAdapterTest {
    @Test
    fun `resolves aliases and returns all tradeable symbols`() = runTest {
        val marketData = HyperliquidFixtures.marketData(HyperliquidFixtures.fakeInfo())
        val adapter = HyperliquidSymbolInfoAdapter(marketData)

        val sol = adapter.getSymbolInfo("SOLUSDT")
        assertNotNull(sol)
        assertEquals("SOL", sol.symbol)
        assertEquals("PERPETUAL", sol.contractType)

        val all = adapter.getAllSymbolsInfo()
        assertEquals(
            listOf("BTC", "SOL", "SPX", "flx:TSLA", "xyz:SP500", "xyz:XYZ100").sorted(),
            all.map { it.symbol }.sorted(),
        )
    }
}
