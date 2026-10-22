/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.research.indicator.IndicatorDefinitions
import com.aandios.nous.research.indicator.IndicatorPaneLayout
import com.aandios.nous.research.indicator.IndicatorPaneState
import com.aandios.nous.research.indicator.IndicatorState
import com.aandios.nous.research.indicator.IndicatorType
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class IndicatorPresetRepositoryTest {

    private class FakeStateStore : StateStore {
        val data = mutableMapOf<String, String>()

        override suspend fun getString(key: String): String? = data[key]

        override suspend fun putString(key: String, value: String) {
            data[key] = value
        }
    }

    private fun indicators() = IndicatorState(
        overlays = listOf(
            IndicatorDefinitions.create(IndicatorType.EMA, "ema-1").copy(
                lineColors = mapOf("ema" to 0xFF123456L),
            ),
        ),
        panes = listOf(
            IndicatorPaneState(
                id = "pane-rsi",
                indicators = listOf(IndicatorDefinitions.create(IndicatorType.RSI, "rsi-1")),
                heightDp = 140f,
            ),
            IndicatorPaneState(
                id = "pane-macd",
                indicators = listOf(IndicatorDefinitions.create(IndicatorType.MACD, "macd-1")),
            ),
        ),
        activePaneId = "pane-macd",
        paneLayout = IndicatorPaneLayout.STACKED,
    )

    @Test
    fun favouritesAndPresetsSurviveReload() = runTest {
        val store = FakeStateStore()
        val repository = IndicatorPresetRepository(store)
        repository.load()

        repository.toggleFavourite(IndicatorType.RSI)
        val saved = repository.savePreset("Scalp", indicators())
        assertEquals("Scalp", saved?.name)
        assertEquals(1, saved?.activePaneIndex)

        val restoredRepository = IndicatorPresetRepository(store)
        restoredRepository.load()

        assertTrue(IndicatorType.RSI in restoredRepository.state.value.favouriteTypes)
        assertEquals(1, restoredRepository.state.value.presets.size)
        val restored = restoredRepository.state.value.presets.single()
        assertEquals(2, restored.panes.size)
        assertEquals(1, restored.overlays.size)
        assertEquals(mapOf("ema" to 0xFF123456L), restored.overlays.single().lineColors)
        assertEquals("macd-1", restored.panes[1].indicators.single().id)
    }

    @Test
    fun presetDeleteAndRenamePersist() = runTest {
        val store = FakeStateStore()
        val repository = IndicatorPresetRepository(store)
        val first = repository.savePreset("First", indicators())!!
        val second = repository.savePreset("Second", indicators())!!

        repository.renamePreset(first.id, "Renamed")
        repository.deletePreset(second.id)

        val restoredRepository = IndicatorPresetRepository(store)
        restoredRepository.load()

        val presets = restoredRepository.state.value.presets
        assertEquals(1, presets.size)
        assertEquals("Renamed", presets.single().name)
    }

    @Test
    fun blankPresetNameIsRejected() = runTest {
        val repository = IndicatorPresetRepository(FakeStateStore())

        assertEquals(null, repository.savePreset("   ", indicators()))
        assertTrue(repository.state.value.presets.isEmpty())
    }
}
