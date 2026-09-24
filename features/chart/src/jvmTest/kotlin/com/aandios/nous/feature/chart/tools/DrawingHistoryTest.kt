/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.tools

import androidx.compose.ui.graphics.Color
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun `undo restores previous snapshot and enables redo`() {
        val history = DrawingHistory()
        history.add(trendLine("a"))
        history.add(trendLine("b"))

        history.undo()

        assertEquals(listOf("a"), history.drawings.map { it.id })
        assertTrue(history.canUndo)
        assertTrue(history.canRedo)

        history.undo()
        assertEquals(0, history.size)
        assertFalse(history.canUndo)
    }

    @Test
    fun `redo restores undone snapshot`() {
        val history = DrawingHistory()
        history.add(trendLine("a"))
        history.add(trendLine("b"))
        history.undo()

        history.redo()

        assertEquals(listOf("a", "b"), history.drawings.map { it.id })
        assertTrue(history.canUndo)
        assertFalse(history.canRedo)
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
    fun `update replaces drawing without undo entry`() {
        val history = DrawingHistory()
        history.add(trendLine("a"))

        history.update("a", trendLine("a").copy(startPrice = 105f))

        assertEquals(105f, (history.drawings.first() as Drawing.TrendLine).startPrice)
        assertTrue(history.canUndo) // снимок только от add
    }

    @Test
    fun `update then commit records single undo entry for whole drag`() {
        val history = DrawingHistory()
        history.add(trendLine("a"))
        history.add(trendLine("b"))

        history.update("b", trendLine("b").copy(startPrice = 1f))
        history.update("b", trendLine("b").copy(startPrice = 2f))
        history.update("b", trendLine("b").copy(startPrice = 3f))
        history.commit()

        history.undo()
        assertEquals(listOf("a", "b"), history.drawings.map { it.id })
        assertEquals(100f, (history.drawings.last() as Drawing.TrendLine).startPrice)
    }

    @Test
    fun `undo of moved drawing restores previous position`() {
        val history = DrawingHistory()
        history.add(trendLine("a"))

        history.update("a", trendLine("a").copy(startPrice = 120f))
        history.commit()

        history.undo()
        assertEquals(100f, (history.drawings.first() as Drawing.TrendLine).startPrice)
    }

    @Test
    fun `remove deletes drawing and records undo`() {
        val history = DrawingHistory()
        val drawing = trendLine("a")
        history.add(drawing)

        history.remove(drawing)

        assertEquals(0, history.size)
        history.undo()
        assertEquals(1, history.size)
    }

    @Test
    fun `maxHistory limits undo stack`() {
        val history = DrawingHistory(maxHistory = 2)
        history.add(trendLine("a"))
        history.add(trendLine("b"))
        history.add(trendLine("c"))

        history.undo() // -> a,b
        history.undo() // -> a
        assertFalse(history.canUndo)
        assertEquals(1, history.size)
    }

    @Test
    fun `replaceAll replaces content and clears history`() {
        val history = DrawingHistory()
        history.add(trendLine("old"))

        history.replaceAll(listOf(trendLine("new1"), trendLine("new2")))

        assertEquals(listOf("new1", "new2"), history.drawings.map { it.id })
        assertFalse(history.canUndo)
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

        assertEquals(1, initialSnapshot.size)
        assertEquals(Color(0xFFFFEB00), history.drawings.first().color)
    }
}
