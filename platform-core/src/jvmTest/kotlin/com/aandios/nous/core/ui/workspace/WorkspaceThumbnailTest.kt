/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.workspace

import com.aandios.nous.core.workspace.LayoutNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorkspaceThumbnailTest {

    @Test
    fun `second child takes remaining share of the split`() {
        val layout = LayoutNode.Split(
            direction = LayoutNode.Direction.HORIZONTAL,
            ratio = 0.7f,
            children = listOf(LayoutNode.Leaf("chart"), LayoutNode.Leaf("dom")),
        )
        val rects = collectRects(layout, 0f, 0f, w = 100f, h = 50f, gap = 0f)
        assertEquals(2, rects.size)
        assertEquals(70f, rects[0].first.width, 0.01f)
        assertEquals(30f, rects[1].first.width, 0.01f)
        assertEquals(70f, rects[1].first.left, 0.01f)
    }

    @Test
    fun `nested split rects stay inside the thumbnail bounds`() {
        val layout = LayoutNode.Split(
            direction = LayoutNode.Direction.HORIZONTAL,
            ratio = 0.72f,
            children = listOf(
                LayoutNode.Leaf("chart"),
                LayoutNode.Split(
                    direction = LayoutNode.Direction.VERTICAL,
                    ratio = 0.75f,
                    children = listOf(LayoutNode.Leaf("dom"), LayoutNode.Leaf("trades")),
                ),
            ),
        )
        val w = 160f
        val h = 90f
        val gap = 3f
        val rects = collectRects(layout, 0f, 0f, w, h, gap)
        assertEquals(3, rects.size)
        rects.forEach { (rect, _) ->
            assertTrue(rect.left >= -0.01f)
            assertTrue(rect.top >= -0.01f)
            assertTrue(rect.right <= w + 0.01f, "rect ${rect.right} exceeds width $w")
            assertTrue(rect.bottom <= h + 0.01f, "rect ${rect.bottom} exceeds height $h")
        }
        assertEquals((w - gap) * 0.72f, rects[0].first.width, 0.01f)
        assertEquals(rects[1].first.width, rects[2].first.width, 0.01f)
        assertEquals(h - gap, rects[1].first.height + rects[2].first.height, 0.01f)
    }

    @Test
    fun `three children split remaining space evenly`() {
        val layout = LayoutNode.Split(
            direction = LayoutNode.Direction.VERTICAL,
            ratio = 0.5f,
            children = listOf(
                LayoutNode.Leaf("a"),
                LayoutNode.Leaf("b"),
                LayoutNode.Leaf("c"),
            ),
        )
        val rects = collectRects(layout, 0f, 0f, w = 80f, h = 100f, gap = 2f)
        assertEquals(48f, rects[0].first.height, 0.01f)
        assertEquals(24f, rects[1].first.height, 0.01f)
        assertEquals(24f, rects[2].first.height, 0.01f)
        assertEquals(100f, rects[2].first.bottom, 0.01f)
    }
}
