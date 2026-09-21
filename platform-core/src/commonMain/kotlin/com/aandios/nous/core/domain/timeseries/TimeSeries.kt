/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.domain.timeseries

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Источник данных временного ряда: история, realtime и правила слияния.
 * Реализуется адаптерами провайдеров (свечи, ликвидации, footprint, индикаторы),
 * что позволяет использовать один контроллер пагинации/подписки для всех.
 */
interface TimeSeriesSource<T> {

    /** Начальная порция данных (обычно последние N элементов, по возрастанию времени). */
    suspend fun loadInitial(): List<T>

    /** Порция данных старее [beforeTimestamp] (по возрастанию времени). */
    suspend fun loadBefore(beforeTimestamp: Long, limit: Int): List<T>

    /** Realtime-обновления. Может быть пустым потоком. */
    fun liveUpdates(): Flow<T>

    /** Слияние realtime-элемента с текущим списком. */
    fun mergeItem(items: List<T>, update: T): List<T>

    /** Временная метка элемента — используется для пагинации и сортировки. */
    fun timestampOf(item: T): Long
}

data class TimeSeriesState<T>(
    val items: List<T> = emptyList(),
    val loading: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = true,
    val loadCount: Int = 0,
    val error: String? = null,
)

/**
 * Обобщённый контроллер загрузки временного ряда: initial load, live-подписка,
 * пагинация назад (loadMore) и состояние для UI.
 */
class TimeSeriesController<T>(
    private val source: TimeSeriesSource<T>,
    private val scope: CoroutineScope,
    private val pageSize: Int = 200,
) {
    private val _state = MutableStateFlow(TimeSeriesState<T>())
    val state: StateFlow<TimeSeriesState<T>> = _state.asStateFlow()

    private var liveJob: Job? = null

    /** Загружает начальные данные и подписывается на realtime. */
    fun start() {
        liveJob?.cancel()
        _state.value = TimeSeriesState(loading = true)
        liveJob = scope.launch {
            try {
                val initial = source.loadInitial()
                _state.update {
                    it.copy(
                        items = initial,
                        loading = false,
                        hasMore = initial.isNotEmpty(),
                    )
                }
                source.liveUpdates().collect { update ->
                    _state.update { it.copy(items = source.mergeItem(it.items, update)) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message ?: "Unknown error") }
            }
        }
    }

    /** Подгружает более старые данные (скролл влево). */
    fun loadMore() {
        val current = _state.value
        if (current.loadingMore || !current.hasMore) return
        val oldest = current.items.firstOrNull()?.let { source.timestampOf(it) } ?: return

        _state.update { it.copy(loadingMore = true) }
        scope.launch {
            try {
                val older = source.loadBefore(oldest - 1, pageSize)
                if (older.isEmpty()) {
                    _state.update { it.copy(loadingMore = false, hasMore = false) }
                } else {
                    _state.update { s ->
                        s.copy(
                            items = (older + s.items)
                                .distinctBy { source.timestampOf(it) }
                                .sortedBy { source.timestampOf(it) },
                            loadingMore = false,
                            loadCount = older.size,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(loadingMore = false, error = e.message) }
            }
        }
    }

    /** Останавливает realtime-подписку, оставляя загруженные данные. */
    fun stop() {
        liveJob?.cancel()
        liveJob = null
    }

    fun dispose() {
        stop()
    }
}
