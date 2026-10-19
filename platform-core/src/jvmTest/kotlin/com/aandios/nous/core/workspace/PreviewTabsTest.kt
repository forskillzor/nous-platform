/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull

class PreviewTabsTest {

    private fun session(
        ids: List<String>,
        active: String? = ids.lastOrNull(),
        preview: String? = null,
    ) = WindowSessionState(
        id = "s",
        workspaceIds = ids,
        activeWorkspaceId = active,
        previewWorkspaceId = preview,
    )

    @Test
    fun `preview opens as new tab and marks preview`() {
        val st = session(listOf("A"), active = "A").openPreview("B")
        assertEquals(listOf("A", "B"), st.workspaceIds)
        assertEquals("B", st.activeWorkspaceId)
        assertEquals("B", st.previewWorkspaceId)
    }

    @Test
    fun `preview of already open workspace only activates`() {
        val st = session(listOf("A", "B"), active = "A", preview = "B").openPreview("A")
        assertEquals(listOf("A", "B"), st.workspaceIds)
        assertEquals("A", st.activeWorkspaceId)
        assertEquals("B", st.previewWorkspaceId)
    }

    @Test
    fun `new preview replaces the slot in place`() {
        val st = session(listOf("P1", "P2", "A"), active = "A", preview = "A").openPreview("B")
        assertEquals(listOf("P1", "P2", "B"), st.workspaceIds)
        assertEquals("B", st.activeWorkspaceId)
        assertEquals("B", st.previewWorkspaceId)
    }

    @Test
    fun `stale preview flag is replaced by the new preview`() {
        val st = session(listOf("A", "B"), active = "A", preview = "GONE").openPreview("C")
        assertEquals(listOf("A", "B", "C"), st.workspaceIds)
        assertEquals("C", st.previewWorkspaceId)
    }

    @Test
    fun `pinned open promotes the workspace when it is the preview`() {
        val st = session(listOf("A", "B"), active = "B", preview = "B").openPinned("B")
        assertEquals(listOf("A", "B"), st.workspaceIds)
        assertEquals("B", st.activeWorkspaceId)
        assertNull(st.previewWorkspaceId)
    }

    @Test
    fun `pinned open keeps an unrelated preview`() {
        val st = session(listOf("A", "B"), active = "A", preview = "A").openPinned("B")
        assertEquals(listOf("A", "B"), st.workspaceIds)
        assertEquals("B", st.activeWorkspaceId)
        assertEquals("A", st.previewWorkspaceId)
    }

    @Test
    fun `pinned open never duplicates a tab`() {
        val st = session(listOf("A", "B"), active = "A").openPinned("B").openPinned("B")
        assertEquals(listOf("A", "B"), st.workspaceIds)
        assertEquals("B", st.activeWorkspaceId)
    }

    @Test
    fun `promote clears only own preview flag`() {
        val st = session(listOf("A", "B"), active = "A", preview = "A").promotePreview("A")
        assertNull(st.previewWorkspaceId)
        assertEquals("A", st.activeWorkspaceId)

        val other = session(listOf("A", "B"), active = "A", preview = "B").promotePreview("A")
        assertEquals("B", other.previewWorkspaceId)
        assertEquals("A", other.activeWorkspaceId)
    }

    @Test
    fun `without workspace falls back to last tab and drops its preview flag`() {
        val st = session(listOf("A", "B", "C"), active = "C", preview = "C").withoutWorkspace("C")
        assertEquals(listOf("A", "B"), st.workspaceIds)
        assertEquals("B", st.activeWorkspaceId)
        assertNull(st.previewWorkspaceId)
    }

    @Test
    fun `without workspace keeps active and preview of others`() {
        val st = session(listOf("A", "B"), active = "A", preview = "B").withoutWorkspace("GONE")
        assertEquals(listOf("A", "B"), st.workspaceIds)
        assertEquals("A", st.activeWorkspaceId)
        assertEquals("B", st.previewWorkspaceId)
    }

    @Test
    fun `duplicate gets new id, copy name and same folder`() {
        val original = WorkspaceConfig(id = "orig", name = "Scalping", group = "Futures")
        val copy = duplicateWorkspaceConfig(original)
        assertNotEquals(original.id, copy.id)
        assertEquals("Scalping copy", copy.name)
        assertEquals("Futures", copy.group)
        assertEquals(original.layout, copy.layout)
        assertEquals(original.panels, copy.panels)
    }
}
