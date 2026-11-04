/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.main

import kotlin.test.Test
import kotlin.test.assertEquals

class WindowBoundsRegistryTest {

    @Test
    fun `drop hit test only accepts tab bar and insertion follows tab centers`() {
        val windowId = "tab-drop-test"
        WindowBoundsRegistry.remove(windowId)
        try {
            WindowBoundsRegistry.update(windowId, WindowBoundsRegistry.Bounds(1000, 1000, 400, 300))
            WindowBoundsRegistry.updateTabWorkspaceIds(windowId, listOf("A", "B"))
            WindowBoundsRegistry.updateTabBarBounds(windowId, WindowBoundsRegistry.Bounds(10, 10, 250, 36))
            WindowBoundsRegistry.updateTabCenter(windowId, "A", 80)
            WindowBoundsRegistry.updateTabCenter(windowId, "B", 160)

            assertEquals(windowId, WindowBoundsRegistry.tabBarAt(20, 20))
            assertEquals(null, WindowBoundsRegistry.tabBarAt(20, 100))
            assertEquals(0, WindowBoundsRegistry.tabInsertionIndex(windowId, 70))
            assertEquals(1, WindowBoundsRegistry.tabInsertionIndex(windowId, 120))
            assertEquals(2, WindowBoundsRegistry.tabInsertionIndex(windowId, 190))
        } finally {
            WindowBoundsRegistry.remove(windowId)
        }
    }

    @Test
    fun `removing a tab workspace prunes its measured center`() {
        val windowId = "tab-center-cleanup-test"
        WindowBoundsRegistry.remove(windowId)
        try {
            WindowBoundsRegistry.updateTabWorkspaceIds(windowId, listOf("A", "B"))
            WindowBoundsRegistry.updateTabBarBounds(windowId, WindowBoundsRegistry.Bounds(0, 0, 200, 36))
            WindowBoundsRegistry.updateTabCenter(windowId, "A", 40)
            WindowBoundsRegistry.updateTabCenter(windowId, "B", 100)

            WindowBoundsRegistry.updateTabWorkspaceIds(windowId, listOf("B"))

            assertEquals(1, WindowBoundsRegistry.tabInsertionIndex(windowId, 120))
        } finally {
            WindowBoundsRegistry.remove(windowId)
        }
    }
}
