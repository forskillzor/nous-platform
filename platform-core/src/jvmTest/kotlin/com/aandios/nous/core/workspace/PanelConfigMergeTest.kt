/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PanelConfigMergeTest {

    private fun panel(
        symbol: String = "BTCUSDT",
        providerId: String? = null,
        providerName: String? = null,
        providerRef: String = "main",
        timeframe: String = "1m",
    ) = PanelConfig(
        id = "chart",
        type = PanelType.CHART,
        providerRef = providerRef,
        symbol = symbol,
        state = PanelState.Chart(timeframe = timeframe, chartMode = "FOOTPRINT"),
        providerId = providerId,
        providerName = providerName,
    )

    @Test
    fun `provider only change is detected and applied`() {
        val local = panel(providerId = "binance-nous-0.0.1", providerName = "Binance")
        val remote = panel(providerId = "bybit-nous-0.0.1", providerName = "Bybit")

        assertTrue(externalPanelChanged(local, remote))
        val merged = applyExternalPanelState(local, remote)
        assertEquals("bybit-nous-0.0.1", merged.providerId)
        assertEquals("Bybit", merged.providerName)
        assertEquals("BTCUSDT", merged.symbol)
        // Локальные настройки панели не трогаем
        assertEquals(PanelState.Chart(timeframe = "1m", chartMode = "FOOTPRINT"), merged.state)
        assertEquals("chart", merged.id)
        assertEquals(PanelType.CHART, merged.type)
    }

    @Test
    fun `symbol and provider ref changes are applied together`() {
        val local = panel(symbol = "BTCUSDT", providerRef = "main")
        val remote = panel(symbol = "SOLUSDT", providerRef = "alt")

        val merged = applyExternalPanelState(local, remote)
        assertEquals("SOLUSDT", merged.symbol)
        assertEquals("alt", merged.providerRef)
    }

    @Test
    fun `unchanged panel is returned as the same instance`() {
        val local = panel(providerId = "bybit-nous-0.0.1", providerName = "Bybit")
        val remote = panel(providerId = "bybit-nous-0.0.1", providerName = "Bybit")

        assertFalse(externalPanelChanged(local, remote))
        assertSame(local, applyExternalPanelState(local, remote))
    }

    @Test
    fun `remote null provider clears a local provider`() {
        val local = panel(providerId = "bybit-nous-0.0.1", providerName = "Bybit")
        val remote = panel(providerId = null, providerName = null)

        val merged = applyExternalPanelState(local, remote)
        assertNull(merged.providerId)
        assertNull(merged.providerName)
    }
}
