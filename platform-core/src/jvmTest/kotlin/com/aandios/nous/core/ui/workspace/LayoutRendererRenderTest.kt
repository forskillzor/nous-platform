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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performMouseInput
import androidx.compose.ui.test.runDesktopComposeUiTest
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
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
    fun fixedPanelsKeepWidthAndLeaveNoGaps() = runDesktopComposeUiTest {
        // [chart | dom(240) | trades(240)] — dom/trades фиксированные,
        // chart забирает остаток; дыр нет (сумма = ширине окна).
        val sizes = mutableMapOf<String, IntSize>()
        var tree by mutableStateOf<LayoutNode>(
            LayoutNode.Split(
                LayoutNode.Direction.HORIZONTAL, 0.5f,
                listOf(LayoutNode.Leaf("chart"), LayoutNode.Leaf("dom"), LayoutNode.Leaf("trades"))
            )
        )
        var panels by mutableStateOf(
            mapOf("chart" to panel("chart"), "dom" to panel("dom"), "trades" to panel("trades"))
        )

        setContent {
            LayoutRenderer(
                node = tree,
                panels = panels,
                modifier = Modifier.fillMaxSize(),
                fixedPanelWidths = mapOf("dom" to 240.dp, "trades" to 240.dp),
            ) { panelId ->
                Box(Modifier.fillMaxSize().onSizeChanged { sizes[panelId] = it })
            }
        }
        waitForIdle()

        val wDom = sizes.getValue("dom").width
        val wTrades = sizes.getValue("trades").width
        val wChart = sizes.getValue("chart").width
        assertTrue(wDom in 235..245, "dom должен быть ~240, а не $wDom; sizes=$sizes")
        assertTrue(wTrades in 235..245, "trades должен быть ~240, а не $wTrades")
        assertTrue(wChart > 400, "chart должен занять остаток, а не $wChart")
        assertTrue(
            wChart + wDom + wTrades in 1015..1024,
            "дыр быть не должно: сумма=${wChart + wDom + wTrades}"
        )
    }

    @Test
    fun allFixedSplitKeepsFixedWidthsWithoutHandles() = runDesktopComposeUiTest {
        // [domA(240) | domB(240)] — оба фиксированные: остаются по 240,
        // ручек между ними НЕТ (dom/trades не ресайзятся никогда).
        val sizes = mutableMapOf<String, IntSize>()
        var tree by mutableStateOf<LayoutNode>(
            LayoutNode.Split(
                LayoutNode.Direction.HORIZONTAL, 0.5f,
                listOf(LayoutNode.Leaf("a"), LayoutNode.Leaf("b"))
            )
        )
        var panels by mutableStateOf(
            mapOf(
                "a" to panel("a").copy(type = PanelType.DOM),
                "b" to panel("b").copy(type = PanelType.DOM),
            )
        )

        setContent {
            LayoutRenderer(
                node = tree,
                panels = panels,
                modifier = Modifier.fillMaxSize(),
                fixedPanelWidths = mapOf("a" to 240.dp, "b" to 240.dp),
            ) { panelId ->
                Box(Modifier.fillMaxSize().onSizeChanged { sizes[panelId] = it })
            }
        }
        waitForIdle()

        val wA = sizes.getValue("a").width
        val wB = sizes.getValue("b").width
        assertTrue(wA in 235..245, "domA должен остаться 240, а не $wA")
        assertTrue(wB in 235..245, "domB должен остаться 240, а не $wB")
        val handles = onAllNodesWithTag("split-handle-h", useUnmergedTree = true)
            .fetchSemanticsNodes().size
        assertEquals(0, handles, "между двумя фиксированными dom ручки быть не должно")
    }

    @Test
    fun handlesPresentInNestedTradesPair() = runDesktopComposeUiTest {
        // Точное дерево активного workspace пользователя (LTC Daytrading):
        // root H(0.797): [ V(0.591): [ H[chart, chart], H[chart, chart] ], H(0.5): [trades, trades] ]
        val sizes = mutableMapOf<String, IntSize>()
        val tree = LayoutNode.Split(
            LayoutNode.Direction.HORIZONTAL, 0.7972905f,
            listOf(
                LayoutNode.Split(
                    LayoutNode.Direction.VERTICAL, 0.5913869f,
                    listOf(
                        LayoutNode.Split(
                            LayoutNode.Direction.HORIZONTAL, 0.5f,
                            listOf(LayoutNode.Leaf("chart"), LayoutNode.Leaf("chart2"))
                        ),
                        LayoutNode.Split(
                            LayoutNode.Direction.HORIZONTAL, 0.44440296f,
                            listOf(LayoutNode.Leaf("chart3"), LayoutNode.Leaf("chart4"))
                        )
                    )
                ),
                LayoutNode.Split(
                    LayoutNode.Direction.HORIZONTAL, 0.5f,
                    listOf(LayoutNode.Leaf("trades1"), LayoutNode.Leaf("trades2"))
                )
            )
        )
        val panels = mapOf(
            "chart" to panel("chart"),
            "chart2" to panel("chart2"),
            "chart3" to panel("chart3"),
            "chart4" to panel("chart4"),
            "trades1" to panel("trades1").copy(type = PanelType.TRADES),
            "trades2" to panel("trades2").copy(type = PanelType.TRADES),
        )

        setContent {
            LayoutRenderer(
                node = tree,
                panels = panels,
                modifier = Modifier.fillMaxSize(),
                fixedPanelWidths = mapOf(
                    "trades1" to 240.dp,
                    "trades2" to 240.dp,
                ),
            ) { panelId ->
                Box(Modifier.fillMaxSize().onSizeChanged { sizes[panelId] = it })
            }
        }
        waitForIdle()

        val hHandles = onAllNodesWithTag("split-handle-h", useUnmergedTree = true)
            .fetchSemanticsNodes().size
        assertEquals(
            3, hHandles,
            "ручки только между FLEX-парами: root(V-group|trades-pair), chart|chart, chart|chart"
        )
        val w1 = sizes.getValue("trades1").width
        val w2 = sizes.getValue("trades2").width
        assertTrue(w1 in 235..245, "trades1 должен остаться 240, а не $w1")
        assertTrue(w2 in 235..245, "trades2 должен остаться 240, а не $w2")
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

    @Test
    fun splitHandleResizeWorksAfterTreeChange() = runDesktopComposeUiTest {
        // Регрессия: после move/split у другого сплита «умирал» resize —
        // pointerInput(Unit) держал протухшую лямбду onResize (старый ratio-state).
        val sizes = mutableMapOf<String, IntSize>()
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
                Box(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { sizes[panelId] = it }
                )
            }
        }
        waitForIdle()

        fun dragHandle(dx: Float) {
            val bounds = onNodeWithTag("split-handle-h", useUnmergedTree = true)
                .fetchSemanticsNode().boundsInRoot
            val centerX = (bounds.left + bounds.right) / 2f
            onRoot().performMouseInput {
                moveTo(Offset(centerX, 300f))
                press()
                moveBy(Offset(dx, 0f))
                release()
            }
            waitForIdle()
        }

        // 1) Ручка работает до изменения дерева
        dragHandle(80f)
        val widthA1 = sizes.getValue("a").width
        assertTrue(
            widthA1 > 550,
            "первый drag должен расширить 'a' (ожидали > 550, получили $widthA1, sizes=$sizes)"
        )

        // 2) Изменяем дерево (split панели 'a') — имитация move/split
        tree = LayoutEngine.split(tree, "a", LayoutNode.Direction.VERTICAL, "x")
        panels = panels + ("x" to panel("x"))
        waitForIdle()
        mainClock.advanceTimeBy(1_000) // дождаться fly-анимации
        waitForIdle()
        val widthA2 = sizes.getValue("a").width

        // 3) Ручка ДОЛЖНА продолжать работать после изменения дерева
        dragHandle(-80f)
        val widthA3 = sizes.getValue("a").width

        assertTrue(
            widthA3 < widthA2,
            "resize должен работать после изменения дерева: $widthA2 -> $widthA3"
        )
    }
}
