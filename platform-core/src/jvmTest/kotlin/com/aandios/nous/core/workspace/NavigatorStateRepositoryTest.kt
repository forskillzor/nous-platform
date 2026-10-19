/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import com.aandios.nous.core.storage.StateStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class NavigatorStateRepositoryTest {

    @Test
    fun `loads defaults when nothing is stored`() = runBlocking {
        val repository = NavigatorStateRepository(MemoryStateStore())
        assertEquals(NavigatorState(), repository.load())
    }

    @Test
    fun `round trips folders and collapsed state`() = runBlocking {
        val store = MemoryStateStore()
        val repository = NavigatorStateRepository(store)
        val state = NavigatorState(folders = listOf("My spaces", "Scalping"), collapsed = listOf("Archived"))
        repository.save(state)

        val restored = NavigatorStateRepository(store)
        assertEquals(state, restored.load())
    }

    @Test
    fun `falls back to defaults on corrupted payload`() = runBlocking {
        val store = MemoryStateStore()
        store.putString(NavigatorStateRepository.KEY, "{ not json")
        assertEquals(NavigatorState(), NavigatorStateRepository(store).load())
    }

    private class MemoryStateStore : StateStore {
        private val values = mutableMapOf<String, String>()

        override suspend fun getString(key: String): String? = values[key]

        override suspend fun putString(key: String, value: String) {
            values[key] = value
        }
    }
}
