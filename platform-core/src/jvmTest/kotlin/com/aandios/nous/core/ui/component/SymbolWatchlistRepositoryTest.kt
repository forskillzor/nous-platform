/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import com.aandios.nous.core.storage.StateStore
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SymbolWatchlistRepositoryTest {
    @Test
    fun `persists created lists and toggled symbols`() = runBlocking {
        val store = MemoryStateStore()
        val repository = SymbolWatchlistRepository(store)
        repository.load()

        val list = repository.create("Scalp")
        repository.toggleSymbol(list.id, "btcusdt")
        repository.toggleSymbol(list.id, "ETHUSDT")

        val restored = SymbolWatchlistRepository(store)
        restored.load()
        assertEquals(
            listOf(SymbolWatchlist(list.id, "Scalp", listOf("BTCUSDT", "ETHUSDT"))),
            restored.lists.value,
        )

        restored.toggleSymbol(list.id, "BTCUSDT")
        assertEquals(listOf("ETHUSDT"), restored.lists.value.single().symbols)
    }

    @Test
    fun `rejects empty and duplicate watchlist names`() {
        runBlocking {
            val repository = SymbolWatchlistRepository(MemoryStateStore())
            repository.load()
            repository.create("Main")

            assertFailsWith<IllegalArgumentException> { repository.create("  ") }
            assertFailsWith<IllegalArgumentException> { repository.create("main") }
        }
    }

    @Test
    fun `reorder applies and persists the requested order`(): Unit = runBlocking {
        val store = MemoryStateStore()
        val repository = SymbolWatchlistRepository(store)
        repository.load()
        val first = repository.create("First")
        val second = repository.create("Second")
        val third = repository.create("Third")

        repository.reorder(listOf(third.id, first.id, second.id))
        assertEquals(listOf("Third", "First", "Second"), repository.lists.value.map { it.name })

        val restored = SymbolWatchlistRepository(store)
        restored.load()
        assertEquals(listOf(third.id, first.id, second.id), restored.lists.value.map { it.id })

        assertFailsWith<IllegalArgumentException> { repository.reorder(listOf(third.id)) }
        assertFailsWith<IllegalArgumentException> { repository.reorder(listOf(third.id, first.id, "unknown")) }
    }

    @Test
    fun `renames and deletes a list and its membership`() {
        runBlocking {
            val store = MemoryStateStore()
            val repository = SymbolWatchlistRepository(store)
            repository.load()
            val list = repository.create("Main")
            repository.toggleSymbol(list.id, "BTCUSDT")
            repository.rename(list.id, "Scalp")
            assertEquals("Scalp", repository.lists.value.single().name)

            repository.delete(list.id)

            val restored = SymbolWatchlistRepository(store)
            restored.load()
            assertEquals(emptyList(), restored.lists.value)
        }
    }

    private class MemoryStateStore : StateStore {
        private val values = mutableMapOf<String, String>()

        override suspend fun getString(key: String): String? = values[key]

        override suspend fun putString(key: String, value: String) {
            values[key] = value
        }
    }
}
