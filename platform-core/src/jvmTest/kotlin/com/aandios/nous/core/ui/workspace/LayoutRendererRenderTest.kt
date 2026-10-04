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
import androidx.compose.ui.unit.Dp
import com.aandios.nous.core.workspace.LayoutEngine
import com.aandios.nous.core.workspace.LayoutNode
import com.aandios.nous.core.workspace.PanelConfig
import com.aandios.nous.core.workspace.PanelState
import com.aandios.nous.core.workspace.PanelType
import kotlin.random.Random
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
            2, hHandles,
            "ручки только между FLEX-парами: chart|chart и chart|chart; " +
                "trades-пара без chart — fixed, ручки нет"
        )
        val w1 = sizes.getValue("trades1").width
        val w2 = sizes.getValue("trades2").width
        assertTrue(w1 in 235..245, "trades1 должен остаться 240, а не $w1")
        assertTrue(w2 in 235..245, "trades2 должен остаться 240, а не $w2")
    }

    @Test
    fun chartAbsorbsFreeWidthOfFixedChain() = runDesktopComposeUiTest(width = 1600, height = 900) {
        // Сплит у ПРЕДПОСЛЕДНЕГО trades: [chart, [dom1, [[dom2, trades2], dom3]]].
        // Цепочка без chart — fixed 4×240=960, chart забирает весь остаток:
        // без пустоты, без ручек между фикс-панелями.
        val sizes = mutableMapOf<String, IntSize>()
        val tree = LayoutNode.Split(
            LayoutNode.Direction.HORIZONTAL, 0.5f,
            listOf(
                LayoutNode.Leaf("chart"),
                LayoutNode.Split(
                    LayoutNode.Direction.HORIZONTAL, 0.5f,
                    listOf(
                        LayoutNode.Leaf("dom1"),
                        LayoutNode.Split(
                            LayoutNode.Direction.HORIZONTAL, 0.5f,
                            listOf(
                                LayoutNode.Split(
                                    LayoutNode.Direction.HORIZONTAL, 0.5f,
                                    listOf(LayoutNode.Leaf("dom2"), LayoutNode.Leaf("trades2"))
                                ),
                                LayoutNode.Leaf("dom3")
                            )
                        )
                    )
                )
            )
        )
        val panels = mapOf(
            "chart" to panel("chart"),
            "dom1" to panel("dom1").copy(type = PanelType.DOM),
            "dom2" to panel("dom2").copy(type = PanelType.DOM),
            "dom3" to panel("dom3").copy(type = PanelType.DOM),
            "trades2" to panel("trades2").copy(type = PanelType.TRADES),
        )

        setContent {
            LayoutRenderer(
                node = tree,
                panels = panels,
                modifier = Modifier.fillMaxSize(),
                fixedPanelWidths = mapOf(
                    "dom1" to 240.dp,
                    "dom2" to 240.dp,
                    "dom3" to 240.dp,
                    "trades2" to 240.dp,
                ),
            ) { panelId ->
                Box(Modifier.fillMaxSize().onSizeChanged { sizes[panelId] = it })
            }
        }
        waitForIdle()

        val wChart = sizes.getValue("chart").width
        val wDom1 = sizes.getValue("dom1").width
        val wDom2 = sizes.getValue("dom2").width
        val wTrades2 = sizes.getValue("trades2").width
        val wDom3 = sizes.getValue("dom3").width
        assertTrue(wDom1 in 235..245, "dom1 должен быть 240, а не $wDom1")
        assertTrue(wDom2 in 235..245, "dom2 должен быть 240, а не $wDom2")
        assertTrue(wTrades2 in 235..245, "trades2 должен быть 240, а не $wTrades2")
        assertTrue(wDom3 in 235..245, "dom3 должен быть 240, а не $wDom3")
        assertTrue(wChart > 600, "chart должен забрать остаток, а не $wChart")
        assertTrue(
            wChart + wDom1 + wDom2 + wTrades2 + wDom3 in 1592..1600,
            "пустоты быть не должно: сумма=${wChart + wDom1 + wDom2 + wTrades2 + wDom3}"
        )
        val hHandles = onAllNodesWithTag("split-handle-h", useUnmergedTree = true)
            .fetchSemanticsNodes().size
        assertEquals(0, hHandles, "в фикс-цепочке ручек быть не должно")
    }

    @Test
    fun splitInMiddleOfFixedChainKeepsPanelsTight() = runDesktopComposeUiTest(width = 1600, height = 900) {
        // Сплит у ПЕРВОГО dom: [chart, [[dom1, trades1], [dom2, dom3]]].
        // Обе группы без chart — fixed: цепочка впритык, chart поглощает остаток.
        val sizes = mutableMapOf<String, IntSize>()
        val tree = LayoutNode.Split(
            LayoutNode.Direction.HORIZONTAL, 0.5f,
            listOf(
                LayoutNode.Leaf("chart"),
                LayoutNode.Split(
                    LayoutNode.Direction.HORIZONTAL, 0.5f,
                    listOf(
                        LayoutNode.Split(
                            LayoutNode.Direction.HORIZONTAL, 0.5f,
                            listOf(LayoutNode.Leaf("dom1"), LayoutNode.Leaf("trades1"))
                        ),
                        LayoutNode.Split(
                            LayoutNode.Direction.HORIZONTAL, 0.5f,
                            listOf(LayoutNode.Leaf("dom2"), LayoutNode.Leaf("dom3"))
                        )
                    )
                )
            )
        )
        val panels = mapOf(
            "chart" to panel("chart"),
            "dom1" to panel("dom1").copy(type = PanelType.DOM),
            "dom2" to panel("dom2").copy(type = PanelType.DOM),
            "dom3" to panel("dom3").copy(type = PanelType.DOM),
            "trades1" to panel("trades1").copy(type = PanelType.TRADES),
        )

        setContent {
            LayoutRenderer(
                node = tree,
                panels = panels,
                modifier = Modifier.fillMaxSize(),
                fixedPanelWidths = mapOf(
                    "dom1" to 240.dp,
                    "dom2" to 240.dp,
                    "dom3" to 240.dp,
                    "trades1" to 240.dp,
                ),
            ) { panelId ->
                Box(Modifier.fillMaxSize().onSizeChanged { sizes[panelId] = it })
            }
        }
        waitForIdle()

        val wChart = sizes.getValue("chart").width
        val widths = listOf("dom1", "trades1", "dom2", "dom3").map { sizes.getValue(it).width }
        widths.forEach { w ->
            assertTrue(w in 235..245, "панели цепочки должны быть по 240: $widths")
        }
        assertTrue(wChart > 600, "chart должен забрать остаток, а не $wChart")
        assertTrue(
            wChart + widths.sum() in 1592..1600,
            "пустоты быть не должно: сумма=${wChart + widths.sum()}"
        )
        val hHandles = onAllNodesWithTag("split-handle-h", useUnmergedTree = true)
            .fetchSemanticsNodes().size
        assertEquals(0, hHandles, "в фикс-цепочке ручек быть не должно")
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

    @Test
    fun sequentialSplitsNeverLeaveGapsAndKeepFixedWidths() = runDesktopComposeUiTest(width = 2200, height = 900) {
        // Сценарий пользователя: экран с chart, затем сплиты dom/trades в разном
        // порядке — от последнего элемента, от предпоследнего, от первого dom,
        // плюс случайные. Инварианты после каждого шага:
        //  * chart забирает ВЕСЬ свободный остаток (дыр нет);
        //  * фикс-панели одинаковой ширины и по 240, пока сумма помещается;
        //  * ручек между фикс-панелями нет.
        val sizes = mutableMapOf<String, IntSize>()
        var tree by mutableStateOf<LayoutNode>(LayoutNode.Leaf("chart0"))
        var panels by mutableStateOf(mapOf("chart0" to panel("chart0")))
        var fixedWidths by mutableStateOf(mapOf<String, Dp>())

        setContent {
            LayoutRenderer(
                node = tree,
                panels = panels,
                modifier = Modifier.fillMaxSize(),
                fixedPanelWidths = fixedWidths,
            ) { panelId ->
                Box(Modifier.fillMaxSize().onSizeChanged { sizes[panelId] = it })
            }
        }

        val rnd = Random(42)
        var seq = 0
        val created = mutableListOf<String>()

        fun addSplit(targetId: String, type: PanelType) {
            val newId = "p${seq++}_${type.name.lowercase()}"
            tree = LayoutEngine.split(tree, targetId, LayoutNode.Direction.HORIZONTAL, newId)
            val pc = when (type) {
                PanelType.DOM -> panel(newId).copy(type = PanelType.DOM)
                PanelType.TRADES -> panel(newId).copy(type = PanelType.TRADES)
                else -> panel(newId)
            }
            panels = panels + (newId to pc)
            if (type != PanelType.CHART) fixedWidths = fixedWidths + (newId to 240.dp)
            created += newId
        }

        fun fixedIds() = panels.values.filter { it.type != PanelType.CHART }.map { it.id }

        fun assertInvariants(label: String, expectedHandles: Int) {
            waitForIdle()
            val allWs = panels.keys.map { id -> id to (sizes.getValue(id).width) }
            val fixedWs = fixedIds().map { sizes.getValue(it).width }
            val handles = onAllNodesWithTag("split-handle-h", useUnmergedTree = true)
                .fetchSemanticsNodes().size

            fixedWs.forEach { w ->
                assertTrue(w > 150, "$label: фикс-панель схлопнулась до $w")
            }
            val chartCount = panels.values.count { it.type == PanelType.CHART }
            if (fixedWs.size * 240 + chartCount * 200 <= 2200) {
                fixedWs.forEach { w ->
                    assertTrue(w in 235..245, "$label: ожидали 240, получили $w (все: $allWs)")
                }
            }
            // Ручки — только между двумя flex-поддеревьями
            assertEquals(expectedHandles, handles, "$label: ручки: $handles")
            // Дыр нет: сумма панелей + ручки (4px) == ширине окна
            val total = allWs.sumOf { it.second } + handles * 4
            assertTrue(total in 2190..2200, "$label: дыра/переполнение: total=$total, панели=$allWs")
        }

        // 1) [chart0, dom]
        addSplit("chart0", PanelType.DOM)
        assertInvariants("after dom", expectedHandles = 0)
        // 2) от dom → trades: [chart0, [dom, trades]]
        addSplit(created[0], PanelType.TRADES)
        assertInvariants("after dom+trades", expectedHandles = 0)
        // 3) сплит у ПОСЛЕДНЕГО (trades) → dom
        addSplit(created[1], PanelType.DOM)
        assertInvariants("after last split", expectedHandles = 0)
        // 4) сплит у последнего → trades
        addSplit(created[2], PanelType.TRADES)
        assertInvariants("after last split 2", expectedHandles = 0)
        // 5) сплит у ПРЕДПОСЛЕДНЕГО (dom) → trades
        addSplit(created[1], PanelType.TRADES)
        assertInvariants("after second-to-last split", expectedHandles = 0)
        // 6) сплит у ПЕРВОГО dom → trades
        addSplit(created[0], PanelType.DOM)
        assertInvariants("after first-dom split", expectedHandles = 0)
        // 7-8) случайные сплиты по существующим фикс-панелям
        repeat(2) { i ->
            val target = fixedIds().random(rnd)
            val type = if (rnd.nextBoolean()) PanelType.DOM else PanelType.TRADES
            addSplit(target, type)
            assertInvariants("after random split $i ($target -> $type)", expectedHandles = 0)
        }
        // 9) сплит фикс-панели с CHART: цепочка становится flex,
        //    появляется ручка chart0|цепочка, chart внутри поглощает остаток
        addSplit(created[1], PanelType.CHART)
        assertInvariants("after chart in chain", expectedHandles = 1)
        val chart0w = sizes.getValue("chart0").width
        val innerChartW = sizes.getValue(created.last()).width
        assertTrue(chart0w > 150, "chart0 не должен схлопнуться, а не $chart0w")
        assertTrue(innerChartW > 150, "chart в цепочке не должен схлопнуться, а не $innerChartW")
        assertTrue(
            kotlin.math.abs(chart0w - innerChartW) < 6,
            "charts должны делить пропорционально: chart0=$chart0w, inner=$innerChartW"
        )
    }
}
