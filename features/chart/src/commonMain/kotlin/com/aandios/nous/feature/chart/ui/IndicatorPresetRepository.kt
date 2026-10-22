/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.core.currentTimeMillis
import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.research.indicator.IndicatorInstance
import com.aandios.nous.research.indicator.IndicatorState
import com.aandios.nous.research.indicator.IndicatorType
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class IndicatorPresetPane(
    val indicators: List<IndicatorInstance> = emptyList(),
)

@Serializable
data class IndicatorPreset(
    val id: String,
    val name: String,
    val overlays: List<IndicatorInstance> = emptyList(),
    val panes: List<IndicatorPresetPane> = emptyList(),
    val activePaneIndex: Int = 0,
)

@Serializable
data class IndicatorLibrary(
    val favouriteTypes: List<IndicatorType> = emptyList(),
    val presets: List<IndicatorPreset> = emptyList(),
)

/**
 * Global (not per chart) favourites and named indicator presets. Presets store
 * overlays, pane grouping and parameters, but never pane heights/layout.
 */
class IndicatorPresetRepository(
    private val store: StateStore,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val mutex = Mutex()
    private var loaded = false
    private var sequence = 0L

    private val _state = MutableStateFlow(IndicatorLibrary())
    val state: StateFlow<IndicatorLibrary> = _state.asStateFlow()

    suspend fun load() {
        mutex.withLock { loadLocked() }
    }

    private suspend fun loadLocked() {
        if (loaded) return
        val restored = runCatching {
            store.getString(KEY_LIBRARY)?.let { json.decodeFromString<IndicatorLibrary>(it) }
        }.getOrNull()
        _state.value = restored ?: IndicatorLibrary()
        loaded = true
    }

    suspend fun toggleFavourite(type: IndicatorType) {
        mutate { library ->
            val favourites = library.favouriteTypes.toMutableList()
            if (!favourites.remove(type)) favourites.add(type)
            library.copy(favouriteTypes = favourites)
        }
    }

    suspend fun savePreset(name: String, indicators: IndicatorState): IndicatorPreset? {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return null
        var saved: IndicatorPreset? = null
        mutate { library ->
            val preset = indicators.toPreset(
                id = "preset-${currentTimeMillis().toString(36)}-${sequence++}",
                name = trimmed,
            )
            saved = preset
            library.copy(presets = library.presets + preset)
        }
        return saved
    }

    suspend fun renamePreset(id: String, name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        mutate { library ->
            library.copy(
                presets = library.presets.map { preset ->
                    if (preset.id == id) preset.copy(name = trimmed) else preset
                },
            )
        }
    }

    suspend fun deletePreset(id: String) {
        mutate { library ->
            library.copy(presets = library.presets.filterNot { it.id == id })
        }
    }

    private suspend fun mutate(transform: (IndicatorLibrary) -> IndicatorLibrary) {
        mutex.withLock {
            loadLocked()
            val next = transform(_state.value)
            _state.value = next
            withContext(NonCancellable) {
                runCatching { store.putString(KEY_LIBRARY, json.encodeToString(next)) }
            }
        }
    }

    private fun IndicatorState.toPreset(id: String, name: String): IndicatorPreset {
        val activeIndex = panes.indexOfFirst { it.id == activePaneId }.coerceAtLeast(0)
        return IndicatorPreset(
            id = id,
            name = name,
            overlays = overlays,
            panes = panes.map { IndicatorPresetPane(indicators = it.indicators) },
            activePaneIndex = activeIndex,
        )
    }

    private companion object {
        const val KEY_LIBRARY = "indicator_library"
    }
}
