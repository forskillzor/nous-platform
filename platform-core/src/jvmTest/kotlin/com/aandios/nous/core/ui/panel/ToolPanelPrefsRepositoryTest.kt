/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.panel

import com.aandios.nous.core.storage.StateStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class ToolPanelPrefsRepositoryTest {

    @Test
    fun `width falls back to default when nothing is stored`() = runBlocking {
        val repository = ToolPanelPrefsRepository(MemoryStateStore())
        assertEquals(300f, repository.loadWidth(defaultWidth = 300f))
    }

    @Test
    fun `width round trips`() = runBlocking {
        val store = MemoryStateStore()
        ToolPanelPrefsRepository(store).saveWidth(412.5f)
        assertEquals(412.5f, ToolPanelPrefsRepository(store).loadWidth(defaultWidth = 300f))
    }

    @Test
    fun `width ignores corrupted value`() = runBlocking {
        val store = MemoryStateStore()
        store.putString(ToolPanelPrefsRepository.WIDTH_KEY, "not-a-number")
        assertEquals(300f, ToolPanelPrefsRepository(store).loadWidth(defaultWidth = 300f))
    }

    @Test
    fun `market watch loads defaults when nothing is stored`() = runBlocking {
        val repository = ToolPanelPrefsRepository(MemoryStateStore())
        assertEquals(MarketWatchPrefs(), repository.loadMarketWatch())
    }

    @Test
    fun `market watch round trips settings and scroll`() = runBlocking {
        val store = MemoryStateStore()
        val prefs = MarketWatchPrefs(
            exchangeId = "bybit",
            listId = "favourites",
            sort = "VOLUME",
            descending = true,
            perpetualsOnly = false,
            quoteAsset = "USDC",
            scrollIndex = 42,
            scrollOffset = 120,
        )
        ToolPanelPrefsRepository(store).saveMarketWatch(prefs)
        assertEquals(prefs, ToolPanelPrefsRepository(store).loadMarketWatch())
    }

    @Test
    fun `market watch legacy payload keeps defaults for new fields`() = runBlocking {
        val store = MemoryStateStore()
        store.putString(
            ToolPanelPrefsRepository.MARKET_WATCH_KEY,
            """{"exchangeId":"ALL","listId":"favourites","sort":"SYMBOL","descending":false,"scrollIndex":7,"scrollOffset":3}""",
        )
        val prefs = ToolPanelPrefsRepository(store).loadMarketWatch()
        assertEquals("ALL", prefs.exchangeId)
        assertEquals("favourites", prefs.listId)
        assertEquals("SYMBOL", prefs.sort)
        assertEquals(false, prefs.descending)
        assertEquals(7, prefs.scrollIndex)
        assertEquals(3, prefs.scrollOffset)
        assertEquals(true, prefs.perpetualsOnly)
        assertEquals("USDT", prefs.quoteAsset)
    }

    @Test
    fun `market watch falls back to defaults on corrupted payload`() = runBlocking {
        val store = MemoryStateStore()
        store.putString(ToolPanelPrefsRepository.MARKET_WATCH_KEY, "{ broken")
        assertEquals(MarketWatchPrefs(), ToolPanelPrefsRepository(store).loadMarketWatch())
    }

    private class MemoryStateStore : StateStore {
        private val values = mutableMapOf<String, String>()

        override suspend fun getString(key: String): String? = values[key]

        override suspend fun putString(key: String, value: String) {
            values[key] = value
        }
    }
}
