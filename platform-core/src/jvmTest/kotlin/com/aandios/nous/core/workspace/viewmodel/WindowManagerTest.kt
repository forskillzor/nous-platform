/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace.viewmodel

import com.aandios.nous.core.storage.StateStore
import com.aandios.nous.core.workspace.AppConfig
import com.aandios.nous.core.workspace.AppStateRepository
import com.aandios.nous.core.workspace.WindowSessionState
import com.aandios.nous.core.workspace.WorkspaceConfig
import com.aandios.nous.core.workspace.WorkspaceRepository
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class WindowManagerTest {

    private class MemoryStateStore : StateStore {
        private val values = mutableMapOf<String, String>()

        override suspend fun getString(key: String): String? = values[key]

        override suspend fun putString(key: String, value: String) {
            values[key] = value
        }
    }

    private class Harness(
        val tabManager: TabManager,
        val workspaceRepo: WorkspaceRepository,
        val appStateRepo: AppStateRepository,
        val wm: WindowManager,
    )

    private fun TestScope.harness(): Harness {
        val store = MemoryStateStore()
        val workspaceRepo = WorkspaceRepository(store)
        val appStateRepo = AppStateRepository(store)
        val tabManager = TabManager()
        val wm = WindowManager(tabManager, workspaceRepo, appStateRepo, StandardTestDispatcher(testScheduler))
        return Harness(tabManager, workspaceRepo, appStateRepo, wm)
    }

    private fun workspace(id: String) = WorkspaceConfig(id = id, name = id)

    @Test
    fun `preview click reuses the preview slot and disposes the replaced workspace`() = runTest {
        val h = harness()
        h.wm.restoreSessions()
        val sessionId = h.wm.sessions.value.single().id

        h.wm.openWorkspacePreviewIn(sessionId, workspace("A"))
        advanceUntilIdle()
        assertNotNull(h.tabManager.workspaceById("A"))
        var st = h.wm.sessionById(sessionId)!!
        assertEquals(listOf("A"), st.workspaceIds)
        assertEquals("A", st.activeWorkspaceId)
        assertEquals("A", st.previewWorkspaceId)

        h.wm.openWorkspacePreviewIn(sessionId, workspace("B"))
        advanceUntilIdle()
        st = h.wm.sessionById(sessionId)!!
        assertEquals(listOf("B"), st.workspaceIds)
        assertEquals("B", st.previewWorkspaceId)
        assertNull(h.tabManager.workspaceById("A"))
        assertNotNull(h.tabManager.workspaceById("B"))
    }

    @Test
    fun `pinned open never duplicates and keeps an unrelated preview`() = runTest {
        val h = harness()
        h.wm.restoreSessions()
        val sessionId = h.wm.sessions.value.single().id

        h.wm.openWorkspacePreviewIn(sessionId, workspace("A"))
        h.wm.openWorkspaceIn(sessionId, workspace("B"))
        h.wm.openWorkspaceIn(sessionId, workspace("B"))
        advanceUntilIdle()

        var st = h.wm.sessionById(sessionId)!!
        assertEquals(listOf("A", "B"), st.workspaceIds)
        assertEquals("B", st.activeWorkspaceId)
        assertEquals("A", st.previewWorkspaceId)

        h.wm.promotePreview(sessionId, "A")
        st = h.wm.sessionById(sessionId)!!
        assertNull(st.previewWorkspaceId)
        assertEquals("A", st.activeWorkspaceId)

        h.wm.openWorkspaceIn(sessionId, workspace("A"))
        st = h.wm.sessionById(sessionId)!!
        assertEquals(listOf("A", "B"), st.workspaceIds)
    }

    @Test
    fun `closing the preview tab clears the flag and disposes the workspace`() = runTest {
        val h = harness()
        h.wm.restoreSessions()
        val sessionId = h.wm.sessions.value.single().id

        h.wm.openWorkspaceIn(sessionId, workspace("A"))
        h.wm.openWorkspacePreviewIn(sessionId, workspace("B"))
        h.wm.closeWorkspaceIn(sessionId, "B")
        advanceUntilIdle()

        val st = h.wm.sessionById(sessionId)!!
        assertEquals(listOf("A"), st.workspaceIds)
        assertNull(st.previewWorkspaceId)
        assertEquals("A", st.activeWorkspaceId)
        assertNull(h.tabManager.workspaceById("B"))
        assertNotNull(h.tabManager.workspaceById("A"))
    }

    @Test
    fun `detach promotes preview into a new window`() = runTest {
        val h = harness()
        h.wm.restoreSessions()
        val sessionId = h.wm.sessions.value.single().id

        h.wm.openWorkspacePreviewIn(sessionId, workspace("A"))
        h.wm.openWorkspaceIn(sessionId, workspace("B"))
        advanceUntilIdle()

        h.wm.detachWorkspace("A", sessionId)
        val source = h.wm.sessionById(sessionId)!!
        assertEquals(listOf("B"), source.workspaceIds)
        assertNull(source.previewWorkspaceId)
        val detached = h.wm.sessions.value.single { it.id != sessionId }
        assertEquals(listOf("A"), detached.workspaceIds)
        assertEquals("A", detached.activeWorkspaceId)
        assertNull(detached.previewWorkspaceId)
        assertNotNull(h.tabManager.workspaceById("A"))
    }

    @Test
    fun `move between windows clears the source preview flag`() = runTest {
        val h = harness()
        h.wm.restoreSessions()
        val first = h.wm.sessions.value.single().id
        h.wm.newEmptySession()
        val second = h.wm.sessions.value.single { it.id != first }.id

        h.wm.openWorkspacePreviewIn(first, workspace("A"))
        h.wm.openWorkspaceIn(first, workspace("B"))
        advanceUntilIdle()

        h.wm.moveWorkspace("A", first, second, 0)
        assertEquals(listOf("B"), h.wm.sessionById(first)!!.workspaceIds)
        assertNull(h.wm.sessionById(first)!!.previewWorkspaceId)
        val target = h.wm.sessionById(second)!!
        assertEquals(listOf("A"), target.workspaceIds)
        assertEquals("A", target.activeWorkspaceId)
        assertNull(target.previewWorkspaceId)
    }

    @Test
    fun `restore deduplicates tabs and drops stale preview`() = runTest {
        val store = MemoryStateStore()
        val workspaceRepo = WorkspaceRepository(store)
        val appStateRepo = AppStateRepository(store)
        workspaceRepo.create(workspace("A"))
        appStateRepo.save(
            AppConfig(
                windows = listOf(
                    WindowSessionState(
                        id = "w1",
                        workspaceIds = listOf("A", "A", "MISSING"),
                        activeWorkspaceId = "MISSING",
                        previewWorkspaceId = "MISSING",
                    ),
                ),
            ),
        )

        val wm = WindowManager(TabManager(), workspaceRepo, appStateRepo, StandardTestDispatcher(testScheduler))
        wm.restoreSessions()
        advanceUntilIdle()

        val st = wm.sessionById("w1")!!
        assertEquals(listOf("A"), st.workspaceIds)
        assertEquals("A", st.activeWorkspaceId)
        assertNull(st.previewWorkspaceId)
    }
}
