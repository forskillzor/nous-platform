/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.panel

import com.aandios.nous.core.storage.StateStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Настройки панели Market Watch: фильтры explorer-списка (таб watchlist,
 * perpetuals, quote-актив, биржа), сортировка и позиция скролла (индекс
 * первого видимого элемента + смещение). Хранится одним документом в
 * StateStore; [exchangeId] == null — все биржи.
 */
@Serializable
data class MarketWatchPrefs(
    val exchangeId: String? = null,
    val listId: String? = null,
    val sort: String = "VOLUME",
    val descending: Boolean = true,
    val perpetualsOnly: Boolean = true,
    val quoteAsset: String? = "USDT",
    val scrollIndex: Int = 0,
    val scrollOffset: Int = 0,
)

/**
 * Персистенс панелей боковой колонки: ширина тул-панели (Market Watch /
 * навигатор) и настройки Market Watch.
 */
class ToolPanelPrefsRepository(
    private val store: StateStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    companion object {
        const val WIDTH_KEY = "tool_panel_width"
        const val MARKET_WATCH_KEY = "market_watch_prefs"
    }

    suspend fun loadWidth(defaultWidth: Float): Float =
        store.getString(WIDTH_KEY)?.toFloatOrNull() ?: defaultWidth

    suspend fun saveWidth(width: Float) {
        store.putString(WIDTH_KEY, width.toString())
    }

    suspend fun loadMarketWatch(): MarketWatchPrefs {
        val raw = store.getString(MARKET_WATCH_KEY) ?: return MarketWatchPrefs()
        if (raw.isEmpty()) return MarketWatchPrefs()
        return try {
            json.decodeFromString<MarketWatchPrefs>(raw)
        } catch (_: Exception) {
            MarketWatchPrefs()
        }
    }

    suspend fun saveMarketWatch(prefs: MarketWatchPrefs) {
        store.putString(MARKET_WATCH_KEY, json.encodeToString(prefs))
    }
}
