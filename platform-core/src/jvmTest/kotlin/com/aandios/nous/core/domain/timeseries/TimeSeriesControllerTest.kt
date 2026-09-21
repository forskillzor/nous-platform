/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.domain.timeseries

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class TimeSeriesControllerTest {

    private class FakeSource(
        private val initial: List<Int>,
        private val before: List<Int> = emptyList(),
        private val failInitial: Boolean = false,
        private val liveFlow: Flow<Int> = emptyFlow(),
    ) : TimeSeriesSource<Int> {

        override suspend fun loadInitial(): List<Int> {
            if (failInitial) throw IllegalStateException("boom")
            return initial
        }

        override suspend fun loadBefore(beforeTimestamp: Long, limit: Int): List<Int> = before

        override fun liveUpdates(): Flow<Int> = liveFlow

        override fun mergeItem(items: List<Int>, update: Int): List<Int> = items + update

        override fun timestampOf(item: Int): Long = item.toLong()
    }

    @Test
    fun `start loads initial items and clears loading`() = runTest {
        val source = FakeSource(initial = listOf(1, 2, 3))
        val controller = TimeSeriesController(source, this)

        controller.start()
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3), controller.state.value.items)
        assertFalse(controller.state.value.loading)
        assertTrue(controller.state.value.hasMore)
        assertNull(controller.state.value.error)
    }

    @Test
    fun `live updates are merged into items`() = runTest {
        val live = MutableSharedFlow<Int>(extraBufferCapacity = 16)
        val source = FakeSource(initial = listOf(1, 2), liveFlow = live)
        val controller = TimeSeriesController(source, this)

        controller.start()
        advanceUntilIdle()

        live.emit(3)
        live.emit(4)
        advanceUntilIdle()

        assertEquals(listOf(1, 2, 3, 4), controller.state.value.items)

        controller.stop()
    }

    @Test
    fun `loadMore prepends older items and sets loadCount`() = runTest {
        val source = FakeSource(initial = listOf(10, 20), before = listOf(5, 7))
        val controller = TimeSeriesController(source, this, pageSize = 2)

        controller.start()
        advanceUntilIdle()

        controller.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(5, 7, 10, 20), controller.state.value.items)
        assertEquals(2, controller.state.value.loadCount)
        assertTrue(controller.state.value.hasMore)
    }

    @Test
    fun `loadMore with empty result disables hasMore`() = runTest {
        val source = FakeSource(initial = listOf(10))
        val controller = TimeSeriesController(source, this)

        controller.start()
        advanceUntilIdle()

        controller.loadMore()
        advanceUntilIdle()

        assertEquals(listOf(10), controller.state.value.items)
        assertFalse(controller.state.value.hasMore)
        assertFalse(controller.state.value.loadingMore)
    }

    @Test
    fun `loadInitial failure is exposed as error`() = runTest {
        val source = FakeSource(initial = emptyList(), failInitial = true)
        val controller = TimeSeriesController(source, this)

        controller.start()
        advanceUntilIdle()

        assertEquals("boom", controller.state.value.error)
        assertFalse(controller.state.value.loading)
    }

    @Test
    fun `empty initial data disables hasMore`() = runTest {
        val source = FakeSource(initial = emptyList())
        val controller = TimeSeriesController(source, this)

        controller.start()
        advanceUntilIdle()

        assertTrue(controller.state.value.items.isEmpty())
        assertFalse(controller.state.value.hasMore)
    }
}
