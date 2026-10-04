/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.workspace

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.IntSize
import com.aandios.nous.core.workspace.LayoutEngine
import com.aandios.nous.core.workspace.LayoutNode
import com.aandios.nous.core.workspace.PanelConfig
import com.aandios.nous.core.workspace.PanelState
import com.aandios.nous.core.workspace.PanelType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class LayoutRendererRenderTest {

    private fun panel(id: String) = PanelConfig(
        id = id,
        type = PanelType.CHART,
        providerRef = "main",
        symbol = "BTCUSDT",
        state = PanelState.Chart(),
    )

    @Test
    fun rendersAllPanelsOfNestedTree() = runDesktopComposeUiTest {
        val rendered = mutableSetOf<String>()
        val sizes = mutableMapOf<String, IntSize>()
        val tree = LayoutNode.Split(
            LayoutNode.Direction.VERTICAL, 0.5f,
            listOf(
                LayoutNode.Leaf("a"),
                LayoutNode.Split(
                    LayoutNode.Direction.HORIZONTAL, 0.5f,
                    listOf(LayoutNode.Leaf("b"), LayoutNode.Leaf("c"))
                ),
                LayoutNode.Leaf("d"),
            )
        )
        val panels = listOf("a", "b", "c", "d").associate { it to panel(it) }

        setContent {
            LayoutRenderer(
                node = tree,
                panels = panels,
                modifier = Modifier.fillMaxSize(),
            ) { panelId ->
                rendered += panelId
                Box(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { sizes[panelId] = it }
                )
            }
        }
        waitForIdle()

        assertEquals(setOf("a", "b", "c", "d"), rendered)
        assertEquals(4, sizes.size, "панели должны получить реальные размеры: $sizes")
        sizes.forEach { (id, size) ->
            assertTrue(size.width > 0 && size.height > 0, "панель $id получила нулевой размер: $size")
        }
    }

    @Test
    fun rendersAfterSplitEditAndTransition() = runDesktopComposeUiTest {
        val rendered = mutableSetOf<String>()
        var tree by mutableStateOf<LayoutNode>(
            LayoutNode.Split(
                LayoutNode.Direction.HORIZONTAL, 0.5f,
                listOf(LayoutNode.Leaf("a"), LayoutNode.Leaf("b"))
            )
        )
        var panels by mutableStateOf(mapOf("a" to panel("a"), "b" to panel("b")))

        setContent {
            LayoutRenderer(
                node = tree,
                panels = panels,
                modifier = Modifier.fillMaxSize(),
            ) { panelId ->
                rendered += panelId
                Box(Modifier.fillMaxSize())
            }
        }
        waitForIdle()
        rendered.clear()

        tree = LayoutEngine.split(tree, "a", LayoutNode.Direction.VERTICAL, "x")
        panels = panels + ("x" to panel("x"))

        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        assertEquals(setOf("a", "b", "x"), rendered)
    }

    @Test
    fun rendersAfterTreeReplacementLikeTabSwitch() = runDesktopComposeUiTest {
        val rendered = mutableSetOf<String>()
        var tree by mutableStateOf<LayoutNode>(
            LayoutNode.Split(
                LayoutNode.Direction.HORIZONTAL, 0.6f,
                listOf(
                    LayoutNode.Leaf("chart"),
                    LayoutNode.Split(LayoutNode.Direction.HORIZONTAL, 0.55f, listOf(LayoutNode.Leaf("dom"), LayoutNode.Leaf("trades")))
                )
            )
        )
        var panels by mutableStateOf(
            listOf("chart", "dom", "trades").associate { it to panel(it) }
        )

        setContent {
            LayoutRenderer(
                node = tree,
                panels = panels,
                modifier = Modifier.fillMaxSize(),
            ) { panelId ->
                rendered += panelId
                Box(Modifier.fillMaxSize())
            }
        }
        waitForIdle()
        rendered.clear()

        // Имитация переключения вкладки: совсем другое дерево
        tree = LayoutNode.Split(
            LayoutNode.Direction.VERTICAL, 0.5f,
            listOf(
                LayoutNode.Leaf("p1"),
                LayoutNode.Leaf("p2"),
                LayoutNode.Leaf("p3"),
            )
        )
        panels = listOf("p1", "p2", "p3").associate { it to panel(it) }

        waitForIdle()
        mainClock.advanceTimeBy(1_000)
        waitForIdle()

        assertEquals(setOf("p1", "p2", "p3"), rendered)
    }
}
