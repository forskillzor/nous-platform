/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.runtime.staticCompositionLocalOf
import com.aandios.nous.core.storage.StateStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

val LocalSymbolWatchlistRepository =
    staticCompositionLocalOf<SymbolWatchlistRepository?> { null }

@Serializable
data class SymbolWatchlist(
    val id: String,
    val name: String,
    val symbols: List<String> = emptyList(),
)

class SymbolWatchlistRepository(private val stateStore: StateStore) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }
    private val mutableLists = MutableStateFlow<List<SymbolWatchlist>>(emptyList())
    val lists: StateFlow<List<SymbolWatchlist>> = mutableLists.asStateFlow()
    private var loaded = false

    suspend fun load() = mutex.withLock {
        if (loaded) return@withLock
        val saved = stateStore.getString(STORE_KEY)
        mutableLists.value = saved?.let { json.decodeFromString<List<SymbolWatchlist>>(it) }.orEmpty()
        loaded = true
    }

    suspend fun create(name: String): SymbolWatchlist {
        var created: SymbolWatchlist? = null
        updateLists { lists ->
            val normalizedName = name.trim()
            require(normalizedName.isNotEmpty()) { "Watchlist name cannot be empty" }
            require(lists.none { it.name.equals(normalizedName, ignoreCase = true) }) {
                "A watchlist named '$normalizedName' already exists"
            }
            SymbolWatchlist(id = normalizedName.toWatchlistId(lists), name = normalizedName)
                .also { created = it }
                .let { lists + it }
        }
        return checkNotNull(created)
    }

    suspend fun rename(id: String, name: String) = updateLists { lists ->
        val normalizedName = name.trim()
        require(normalizedName.isNotEmpty()) { "Watchlist name cannot be empty" }
        require(lists.none { it.id != id && it.name.equals(normalizedName, ignoreCase = true) }) {
            "A watchlist named '$normalizedName' already exists"
        }
        require(lists.any { it.id == id }) { "Watchlist not found: $id" }
        lists.map { if (it.id == id) it.copy(name = normalizedName) else it }
    }

    suspend fun delete(id: String) = updateLists { lists ->
        require(lists.any { it.id == id }) { "Watchlist not found: $id" }
        lists.filterNot { it.id == id }
    }

    suspend fun toggleSymbol(id: String, symbol: String) = updateLists { lists ->
        val normalized = symbol.trim().uppercase()
        require(normalized.isNotEmpty()) { "Symbol cannot be empty" }
        require(lists.any { it.id == id }) { "Watchlist not found: $id" }
        lists.map { list ->
            if (list.id != id) list
            else list.copy(
                symbols = if (normalized in list.symbols) list.symbols - normalized
                else list.symbols + normalized,
            )
        }
    }

    private suspend fun updateLists(transform: (List<SymbolWatchlist>) -> List<SymbolWatchlist>) =
        mutex.withLock {
            check(loaded) { "Watchlists have not been loaded" }
            val updated = transform(mutableLists.value)
            stateStore.putString(STORE_KEY, json.encodeToString(updated))
            mutableLists.value = updated
        }

    private fun String.toWatchlistId(existing: List<SymbolWatchlist>): String {
        val base = lowercase().map { if (it.isLetterOrDigit()) it else '-' }
            .joinToString("").trim('-').ifEmpty { "list" }
        if (existing.none { it.id == base }) return base
        var suffix = 2
        while (existing.any { it.id == "$base-$suffix" }) suffix++
        return "$base-$suffix"
    }

    private companion object {
        const val STORE_KEY = "market_watchlists"
    }
}
