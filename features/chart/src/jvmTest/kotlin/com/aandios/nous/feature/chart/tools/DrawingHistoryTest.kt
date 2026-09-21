/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.tools

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DrawingHistoryTest {

    private fun trendLine(id: String) = Drawing.TrendLine(
        id = id,
        startPrice = 100f,
        startTimeMs = 0L,
        endPrice = 110f,
        endTimeMs = 60_000L,
    )

    @Test
    fun `add appends drawing and enables undo`() {
        val history = DrawingHistory()

        history.add(trendLine("a"))

        assertEquals(1, history.size)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun `undo removes drawing and enables redo`() {
        val history = DrawingHistory()
        history.add(trendLine("a"))

        val undone = history.undo()

        assertEquals("a", undone?.id)
        assertEquals(0, history.size)
        assertFalse(history.canUndo)
        assertTrue(history.canRedo)
    }

    @Test
    fun `redo restores undone drawing`() {
        val history = DrawingHistory()
        history.add(trendLine("a"))
        history.undo()

        val redone = history.redo()

        assertEquals("a", redone?.id)
        assertEquals(1, history.size)
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun `undo on empty history returns null`() {
        assertNull(DrawingHistory().undo())
    }

    @Test
    fun `add clears redo stack`() {
        val history = DrawingHistory()
        history.add(trendLine("a"))
        history.undo()
        history.add(trendLine("b"))

        assertFalse(history.canRedo)
        assertEquals(1, history.size)
    }

    @Test
    fun `remove deletes drawing from list and stacks`() {
        val history = DrawingHistory()
        val drawing = trendLine("a")
        history.add(drawing)

        history.remove(drawing)

        assertEquals(0, history.size)
        assertFalse(history.canUndo)
    }

    @Test
    fun `maxHistory limits undo stack`() {
        val history = DrawingHistory(maxHistory = 2)
        history.add(trendLine("a"))
        history.add(trendLine("b"))
        history.add(trendLine("c"))

        assertEquals(3, history.size)
        history.undo()
        history.undo()
        assertFalse(history.canUndo)
    }

    @Test
    fun `replaceAll replaces content and fills undo stack`() {
        val history = DrawingHistory()
        history.add(trendLine("old"))

        history.replaceAll(listOf(trendLine("new1"), trendLine("new2")))

        assertEquals(listOf("new1", "new2"), history.drawings.map { it.id })
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun `clear empties everything`() {
        val history = DrawingHistory()
        history.add(trendLine("a"))
        history.undo()
        history.add(trendLine("b"))

        history.clear()

        assertEquals(0, history.size)
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
    }

    @Test
    fun `drawings list is observable compose state`() {
        val history = DrawingHistory()
        val initialSnapshot = history.drawings

        history.add(trendLine("a"))

        // Snapshot list reflects the addition (Compose state list)
        assertEquals(1, initialSnapshot.size)
        assertEquals(Color(0xFFFFEB00), history.drawings.first().color)
    }
}
