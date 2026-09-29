/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.tools

import com.aandios.nous.core.storage.StateStore
import kotlinx.serialization.json.Json

/**
 * Персистент рисунков на диск: ключ drawings_<workspaceId>_<panelId>.
 * Хранится в StateStore (SQLite settings) в виде JSON-массива.
 */
class DrawingRepository(
    private val store: StateStore,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true },
) {

    suspend fun load(workspaceId: String, panelId: String): List<Drawing> {
        val raw = store.getString(key(workspaceId, panelId)) ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return try {
            json.decodeFromString<List<Drawing>>(raw)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun save(workspaceId: String, panelId: String, drawings: List<Drawing>) {
        store.putString(key(workspaceId, panelId), json.encodeToString(drawings))
    }

    companion object {
        fun key(workspaceId: String, panelId: String): String = "drawings_${workspaceId}_$panelId"
    }
}
