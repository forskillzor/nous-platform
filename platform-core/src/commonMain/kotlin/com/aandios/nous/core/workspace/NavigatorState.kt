/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import com.aandios.nous.core.storage.StateStore
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Состояние навигатора workspace'ов: порядок папок (включая пустые, созданные
 * пользователем) и свёрнутые папки. Хранится одним документом в StateStore.
 */
@Serializable
data class NavigatorState(
    val folders: List<String> = emptyList(),
    val collapsed: List<String> = emptyList(),
)

class NavigatorStateRepository(
    private val store: StateStore,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    companion object {
        const val KEY = "navigator_state"
    }

    suspend fun load(): NavigatorState {
        val raw = store.getString(KEY) ?: return NavigatorState()
        if (raw.isEmpty()) return NavigatorState()
        return try {
            json.decodeFromString<NavigatorState>(raw)
        } catch (_: Exception) {
            NavigatorState()
        }
    }

    suspend fun save(state: NavigatorState) {
        store.putString(KEY, json.encodeToString(state))
    }
}
