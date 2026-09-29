/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.tools

import androidx.compose.ui.graphics.Color
import com.aandios.nous.core.storage.StateStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DrawingRepositoryTest {

    private class FakeStateStore : StateStore {
        val data = mutableMapOf<String, String>()
        override suspend fun getString(key: String): String? = data[key]
        override suspend fun putString(key: String, value: String) {
            data[key] = value
        }
    }

    private val drawings = listOf(
        Drawing.TrendLine(
            id = "tl_1",
            startPrice = 100.5f,
            startTimeMs = 1_000L,
            endPrice = 110.25f,
            endTimeMs = 61_000L,
            color = Color(0xFFFFEB00),
            createdAt = 42L,
            label = "trend",
        ),
        Drawing.HorizontalLevel(
            id = "h_1",
            price = 105f,
            color = Color(0xFF2196F3),
            createdAt = 43L,
            label = "105.00",
            isDashed = true,
        ),
        Drawing.Rectangle(
            id = "r_1",
            topPrice = 110f,
            bottomPrice = 100f,
            startTimeMs = 0L,
            endTimeMs = 120_000L,
            color = Color(0x442196F3),
            createdAt = 44L,
        ),
        Drawing.VerticalLine(
            id = "v_1",
            timeMs = 60_000L,
            color = Color(0xFFFF5722),
            createdAt = 45L,
        ),
    )

    @Test
    fun `save then load roundtrip keeps all drawings`() = runTest {
        val store = FakeStateStore()
        val repository = DrawingRepository(store)

        repository.save("ws-1", "panel-1", drawings)
        val loaded = repository.load("ws-1", "panel-1")

        assertEquals(drawings, loaded)
    }

    @Test
    fun `drawings are isolated per workspace and panel`() = runTest {
        val store = FakeStateStore()
        val repository = DrawingRepository(store)

        repository.save("ws-1", "panel-1", drawings.take(1))
        repository.save("ws-1", "panel-2", drawings.take(2))
        repository.save("ws-2", "panel-1", emptyList())

        assertEquals(1, repository.load("ws-1", "panel-1").size)
        assertEquals(2, repository.load("ws-1", "panel-2").size)
        assertTrue(repository.load("ws-2", "panel-1").isEmpty())
    }

    @Test
    fun `load from empty store returns empty list`() = runTest {
        val repository = DrawingRepository(FakeStateStore())

        assertTrue(repository.load("ws", "panel").isEmpty())
    }

    @Test
    fun `load with corrupted json returns empty list`() = runTest {
        val store = FakeStateStore()
        store.data[DrawingRepository.key("ws", "panel")] = "{not json"
        val repository = DrawingRepository(store)

        assertTrue(repository.load("ws", "panel").isEmpty())
    }

    @Test
    fun `storage key is workspace and panel scoped`() {
        assertEquals("drawings_ws-1_panel-1", DrawingRepository.key("ws-1", "panel-1"))
    }
}
