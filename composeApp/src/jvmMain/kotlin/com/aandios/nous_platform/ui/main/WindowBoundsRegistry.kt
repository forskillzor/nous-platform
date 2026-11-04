/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.main

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * Реестр границ окон и панелей вкладок в экранных координатах (px).
 */
object WindowBoundsRegistry {
    data class Bounds(val x: Int, val y: Int, val width: Int, val height: Int) {
        fun contains(px: Int, py: Int): Boolean =
            px in x..(x + width) && py in y..(y + height)
    }

    data class TabBarBounds(
        val bounds: Bounds,
        val workspaceIds: List<String> = emptyList(),
        val tabCenters: Map<String, Int> = emptyMap(),
    )

    private val _bounds = MutableStateFlow<Map<String, Bounds>>(emptyMap())
    val bounds: StateFlow<Map<String, Bounds>> = _bounds

    private val _tabBars = MutableStateFlow<Map<String, TabBarBounds>>(emptyMap())

    fun update(windowId: String, b: Bounds) {
        _bounds.update { it + (windowId to b) }
    }

    fun updateTabBarBounds(windowId: String, bounds: Bounds) {
        _tabBars.update { current ->
            val previous = current[windowId] ?: TabBarBounds(bounds)
            current + (windowId to previous.copy(bounds = bounds))
        }
    }

    fun updateTabWorkspaceIds(windowId: String, workspaceIds: List<String>) {
        _tabBars.update { current ->
            val previous = current[windowId] ?: TabBarBounds(Bounds(0, 0, 0, 0))
            current + (
                windowId to previous.copy(
                    workspaceIds = workspaceIds,
                    tabCenters = previous.tabCenters.filterKeys { it in workspaceIds },
                )
            )
        }
    }

    fun updateTabCenter(windowId: String, workspaceId: String, centerX: Int) {
        _tabBars.update { current ->
            val previous = current[windowId] ?: return@update current
            if (workspaceId !in previous.workspaceIds) return@update current
            current + (windowId to previous.copy(tabCenters = previous.tabCenters + (workspaceId to centerX)))
        }
    }

    fun remove(windowId: String) {
        _bounds.update { it - windowId }
        _tabBars.update { it - windowId }
    }

    fun boundsAt(px: Int, py: Int): String? =
        _bounds.value.entries.firstOrNull { it.value.contains(px, py) }?.key

    fun tabBarAt(px: Int, py: Int): String? =
        _tabBars.value.entries.firstOrNull { it.value.bounds.contains(px, py) }?.key

    fun tabInsertionIndex(windowId: String, cursorX: Int): Int {
        val tabBar = _tabBars.value[windowId] ?: return 0
        return tabBar.workspaceIds.indexOfFirst { workspaceId ->
            tabBar.tabCenters[workspaceId]?.let { cursorX < it } == true
        }.let { index -> if (index < 0) tabBar.workspaceIds.size else index }
    }

    fun cursorLocation(): Pair<Int, Int>? =
        try {
            java.awt.MouseInfo.getPointerInfo()?.location?.let { it.x to it.y }
        } catch (_: Throwable) {
            null
        }
}
