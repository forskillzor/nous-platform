/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayoutHistoryTest {

    private fun leaf(id: String) = LayoutNode.Leaf(id)

    private fun snapshot(): LayoutSnapshot = LayoutSnapshot(
        layout = LayoutNode.Split(LayoutNode.Direction.HORIZONTAL, 0.5f, listOf(leaf("a"), leaf("b"))),
        panels = emptyMap()
    )

    @Test
    fun `push then undo returns previous state and redo restores`() {
        val history = LayoutHistory()
        val s0 = snapshot()
        val s1 = snapshot().copy(layout = leaf("only"))

        history.push(s0)
        val undone = history.undo(s1)
        assertNotNull(undone)
        assertEquals(2, LayoutEngine.collectPanelIds(undone.layout).size)
        assertTrue(history.canRedo)

        val redone = history.redo(undone)
        assertNotNull(redone)
        assertEquals("only", (redone.layout as LayoutNode.Leaf).panelId)
    }

    @Test
    fun `redo cleared on new push`() {
        val history = LayoutHistory()
        val s0 = snapshot()
        history.push(s0)
        history.undo(snapshot().copy(layout = leaf("x")))
        assertTrue(history.canRedo)
        history.push(s0)
        assertFalse(history.canRedo)
    }

    @Test
    fun `empty history has no undo or redo`() {
        val history = LayoutHistory()
        assertFalse(history.canUndo)
        assertFalse(history.canRedo)
        assertNull(history.undo(snapshot()))
        assertNull(history.redo(snapshot()))
    }

    @Test
    fun `deepCopy detaches split ratio from original`() {
        val split = LayoutNode.Split(LayoutNode.Direction.HORIZONTAL, 0.7f, listOf(leaf("a"), leaf("b")))
        val copy = split.deepCopy() as LayoutNode.Split
        split.ratio = 0.2f
        assertEquals(0.7f, copy.ratio)
        assertFalse(split.children === copy.children)
    }
}
