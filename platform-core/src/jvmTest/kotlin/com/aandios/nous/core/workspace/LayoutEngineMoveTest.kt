/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LayoutEngineMoveTest {

    private fun leaf(id: String) = LayoutNode.Leaf(id)

    @Test
    fun `move to left creates horizontal split with moved first`() {
        val root = LayoutNode.Split(
            LayoutNode.Direction.HORIZONTAL, 0.6f,
            listOf(leaf("a"), leaf("b"))
        )
        val result = LayoutEngine.movePanel(root, "a", "b", LayoutEngine.DropZone.LEFT)
        // a и b были единственными детьми → после извлечения "a" дерево схлопнулось
        // в Leaf("b"), куда и вставился сплит [a, b]
        val split = result as LayoutNode.Split
        assertEquals(LayoutNode.Direction.HORIZONTAL, split.direction)
        assertEquals(listOf("a", "b"), LayoutEngine.collectPanelIds(split))
        assertEquals("a", (split.children[0] as LayoutNode.Leaf).panelId)
        assertEquals("b", (split.children[1] as LayoutNode.Leaf).panelId)
    }

    @Test
    fun `move to right places moved second`() {
        val root = LayoutNode.Split(
            LayoutNode.Direction.HORIZONTAL, 0.6f,
            listOf(leaf("a"), leaf("b"))
        )
        val result = LayoutEngine.movePanel(root, "a", "b", LayoutEngine.DropZone.RIGHT)
        val split = result as LayoutNode.Split
        assertEquals("b", (split.children[0] as LayoutNode.Leaf).panelId)
        assertEquals("a", (split.children[1] as LayoutNode.Leaf).panelId)
    }

    @Test
    fun `center swaps panels in place`() {
        val root = LayoutNode.Split(
            LayoutNode.Direction.HORIZONTAL, 0.5f,
            listOf(leaf("a"), leaf("b"))
        )
        val result = LayoutEngine.movePanel(root, "a", "b", LayoutEngine.DropZone.CENTER)
        val split = result as LayoutNode.Split
        assertEquals("b", (split.children[0] as LayoutNode.Leaf).panelId)
        assertEquals("a", (split.children[1] as LayoutNode.Leaf).panelId)
        assertEquals(listOf("b", "a"), LayoutEngine.collectPanelIds(split))
    }

    @Test
    fun `move between nested splits keeps structure`() {
        val root = LayoutNode.Split(
            LayoutNode.Direction.VERTICAL, 0.5f,
            listOf(
                leaf("a"),
                LayoutNode.Split(LayoutNode.Direction.HORIZONTAL, 0.5f, listOf(leaf("b"), leaf("c")))
            )
        )
        val result = LayoutEngine.movePanel(root, "a", "c", LayoutEngine.DropZone.TOP)
        val ids = LayoutEngine.collectPanelIds(result)
        assertEquals(listOf("b", "a", "c"), ids)
    }

    @Test
    fun `no-op for same panel or missing panel`() {
        val root = LayoutNode.Split(
            LayoutNode.Direction.HORIZONTAL, 0.5f,
            listOf(leaf("a"), leaf("b"))
        )
        assertEquals(
            LayoutEngine.collectPanelIds(root),
            LayoutEngine.collectPanelIds(LayoutEngine.movePanel(root, "a", "a", LayoutEngine.DropZone.RIGHT))
        )
        assertEquals(
            LayoutEngine.collectPanelIds(root),
            LayoutEngine.collectPanelIds(LayoutEngine.movePanel(root, "missing", "b", LayoutEngine.DropZone.LEFT))
        )
    }

    @Test
    fun `removePanel returns null when removing last panel`() {
        val single = leaf("only")
        assertNull(LayoutEngine.removePanel(single, "only"))
    }

    @Test
    fun `move to root top puts panel above all at half`() {
        val root = LayoutNode.Split(
            LayoutNode.Direction.HORIZONTAL, 0.5f,
            listOf(leaf("a"), LayoutNode.Split(LayoutNode.Direction.VERTICAL, 0.5f, listOf(leaf("b"), leaf("c"))))
        )
        val result = LayoutEngine.movePanelToRoot(root, "c", LayoutEngine.DropZone.TOP)
        val split = result as LayoutNode.Split
        assertEquals(LayoutNode.Direction.VERTICAL, split.direction)
        assertEquals(0.5f, split.ratio)
        assertEquals("c", (split.children[0] as LayoutNode.Leaf).panelId)
        assertEquals(listOf("c", "a", "b"), LayoutEngine.collectPanelIds(split))
    }

    @Test
    fun `move to root bottom keeps moved last`() {
        val root = LayoutNode.Split(
            LayoutNode.Direction.HORIZONTAL, 0.5f,
            listOf(leaf("a"), leaf("b"))
        )
        val result = LayoutEngine.movePanelToRoot(root, "a", LayoutEngine.DropZone.BOTTOM)
        val split = result as LayoutNode.Split
        assertEquals(LayoutNode.Direction.VERTICAL, split.direction)
        assertEquals(listOf("b", "a"), LayoutEngine.collectPanelIds(split))
        assertEquals("a", (split.children[1] as LayoutNode.Leaf).panelId)
    }

    @Test
    fun `move to root left and right`() {
        val root = LayoutNode.Split(
            LayoutNode.Direction.VERTICAL, 0.5f,
            listOf(leaf("a"), leaf("b"))
        )
        val left = LayoutEngine.movePanelToRoot(root, "b", LayoutEngine.DropZone.LEFT) as LayoutNode.Split
        assertEquals(LayoutNode.Direction.HORIZONTAL, left.direction)
        assertEquals(listOf("b", "a"), LayoutEngine.collectPanelIds(left))

        val right = LayoutEngine.movePanelToRoot(root, "a", LayoutEngine.DropZone.RIGHT) as LayoutNode.Split
        assertEquals(LayoutNode.Direction.HORIZONTAL, right.direction)
        assertEquals(listOf("b", "a"), LayoutEngine.collectPanelIds(right))
    }

    @Test
    fun `move to root is no-op for single panel or center`() {
        val single = leaf("only")
        assertEquals(single, LayoutEngine.movePanelToRoot(single, "only", LayoutEngine.DropZone.TOP))

        val root = LayoutNode.Split(
            LayoutNode.Direction.HORIZONTAL, 0.5f,
            listOf(leaf("a"), leaf("b"))
        )
        assertEquals(
            LayoutEngine.collectPanelIds(root),
            LayoutEngine.collectPanelIds(LayoutEngine.movePanelToRoot(root, "a", LayoutEngine.DropZone.CENTER))
        )
    }
}
