/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import com.aandios.nous.core.storage.StateStore
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString

class WorkspaceRepository(
    private val store: StateStore,
    private val json: Json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
) {
    companion object {
        private const val PREFIX = "workspace_"
        private const val INDEX_KEY = "workspace_index"
    }

    suspend fun create(config: WorkspaceConfig) {
        val updated = config.copy(updatedAt = currentTime())
        store.putString("$PREFIX${config.id}", json.encodeToString(updated))
        addToIndex(config.id)
    }

    suspend fun get(id: String): WorkspaceConfig? {
        val raw = store.getString("$PREFIX$id") ?: return null
        if (raw.isEmpty()) return null
        return try { json.decodeFromString<WorkspaceConfig>(raw) } catch (_: Exception) { null }
    }

    suspend fun update(config: WorkspaceConfig) {
        val updated = config.copy(updatedAt = currentTime())
        store.putString("$PREFIX${config.id}", json.encodeToString(updated))
    }

    suspend fun delete(id: String) {
        store.putString("$PREFIX$id", "")
        removeFromIndex(id)
    }

    suspend fun getAll(): List<WorkspaceConfig> {
        val ids = getIndex()
        return ids.mapNotNull { get(it) }
    }

    suspend fun listByGroup(group: String): List<WorkspaceConfig> = getAll().filter { it.group == group }
    suspend fun getGroups(): List<String> = getAll().map { it.group }.filter { it.isNotEmpty() }.distinct()

    fun exportJson(config: WorkspaceConfig): String {
        return json.encodeToString(config)
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

class AppStateRepository(
    private val store: StateStore,
    private val json: Json = Json { ignoreUnknownKeys = true }
) {
    companion object { private const val KEY = "app_config" }

    suspend fun save(config: AppConfig) = store.putString(KEY, json.encodeToString(config))
    suspend fun restore(): AppConfig? {
        val raw = store.getString(KEY) ?: return null
        if (raw.isEmpty()) return null
        return try { json.decodeFromString<AppConfig>(raw) } catch (_: Exception) { null }
    }
}
