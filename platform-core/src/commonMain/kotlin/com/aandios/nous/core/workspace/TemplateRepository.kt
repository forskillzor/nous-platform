/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import com.aandios.nous.core.storage.StateStore
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Хранилище пользовательских шаблонов workspace'ов.
 * Тот же паттерн, что у [WorkspaceRepository]: JSON в StateStore + индекс ключей.
 */
class TemplateRepository(
    private val store: StateStore,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    companion object {
        private const val PREFIX = "template_"
        private const val INDEX_KEY = "template_index"
    }

    suspend fun create(config: WorkspaceConfig) {
        store.putString("$PREFIX${config.id}", json.encodeToString(config))
        addToIndex(config.id)
    }

    suspend fun get(id: String): WorkspaceConfig? {
        val raw = store.getString("$PREFIX$id") ?: return null
        if (raw.isEmpty()) return null
        return try { json.decodeFromString<WorkspaceConfig>(raw) } catch (_: Exception) { null }
    }

    suspend fun getAll(): List<WorkspaceConfig> {
        val ids = getIndex()
        return ids.mapNotNull { get(it) }
    }

    suspend fun delete(id: String) {
        store.putString("$PREFIX$id", "")
        removeFromIndex(id)
    }

    private suspend fun addToIndex(id: String) {
        val ids = getIndex().toMutableList()
        if (id !in ids) ids.add(id)
        store.putString(INDEX_KEY, ids.joinToString(","))
    }

    private suspend fun removeFromIndex(id: String) {
        val ids = getIndex().toMutableList()
        ids.remove(id)
        store.putString(INDEX_KEY, ids.joinToString(","))
    }

    private suspend fun getIndex(): List<String> {
        val raw = store.getString(INDEX_KEY) ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return raw.split(",").filter { it.isNotBlank() }
    }
}
