/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.workspace

import com.aandios.nous.core.workspace.PanelConfig
import com.aandios.nous.core.workspace.PanelType
import com.aandios.nous.core.workspace.ProviderRef
import com.aandios.nous.core.workspace.WorkspaceConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkspaceNavigatorModelTest {

    private fun ws(
        id: String,
        name: String,
        group: String = "",
        order: Int = 0,
        createdAt: Long = 0L,
        provider: String? = null,
    ) = WorkspaceConfig(
        id = id,
        name = name,
        group = group,
        order = order,
        createdAt = createdAt,
        providers = provider?.let { listOf(ProviderRef(id = "main", name = it, symbols = listOf("BTCUSDT"))) }
            ?: emptyList(),
    )

    @Test
    fun `tree orders folders unsorted first then folderOrder then alphabetical`() {
        val tree = buildNavigatorTree(
            workspaces = listOf(
                ws("w1", "One", group = "My spaces"),
                ws("w2", "Two", group = "Scalping"),
                ws("w3", "Three"),
                ws("w4", "Four", group = "Alpha"),
            ),
            folderOrder = listOf("Scalping", "My spaces"),
            query = "",
        )
        assertEquals(
            listOf("", "Scalping", "My spaces", "Alpha"),
            tree.folders.map { it.rawGroup },
        )
        assertEquals(NAVIGATOR_UNSORTED_LABEL, tree.folders.first().name)
    }

    @Test
    fun `tree keeps empty folders from folderOrder and sorts workspaces by order then createdAt`() {
        val tree = buildNavigatorTree(
            workspaces = listOf(
                ws("w2", "Second", group = "A", order = 1),
                ws("w1", "First", group = "A", order = 0),
                ws("w3", "Third", group = "A", order = 1, createdAt = 5L),
            ),
            folderOrder = listOf("A", "Empty"),
            query = "",
        )
        assertEquals(listOf("A", "Empty"), tree.folders.map { it.rawGroup })
        assertEquals(listOf("w1", "w2", "w3"), tree.folders.first().workspaces.map { it.workspace.id })
        assertTrue(tree.folders[1].workspaces.isEmpty())
    }

    @Test
    fun `search highlights workspace name matches`() {
        val tree = buildNavigatorTree(
            workspaces = listOf(ws("w1", "SQL DayTrading", group = "My spaces"), ws("w2", "BTC Flow")),
            folderOrder = emptyList(),
            query = "sql",
        )
        val entry = tree.folders.single().workspaces.single()
        assertEquals("w1", entry.workspace.id)
        assertEquals(0..2, entry.highlight)
    }

    @Test
    fun `search by folder name keeps all children and highlights matching names`() {
        val tree = buildNavigatorTree(
            workspaces = listOf(
                ws("w1", "One", group = "Scalping"),
                ws("w2", "LTCUSDT Classic Scalping", group = "Scalping"),
            ),
            folderOrder = emptyList(),
            query = "scalping",
        )
        val folder = tree.folders.single()
        assertEquals("Scalping", folder.rawGroup)
        assertEquals(listOf("w1", "w2"), folder.workspaces.map { it.workspace.id })
        assertEquals(listOf(null, 16..23), folder.workspaces.map { it.highlight })
    }

    @Test
    fun `search matches exchange meta without highlight`() {
        val tree = buildNavigatorTree(
            workspaces = listOf(
                ws("w1", "One", provider = "Bybit"),
                ws("w2", "Two", provider = "Binance"),
            ),
            folderOrder = emptyList(),
            query = "binance",
        )
        val entry = tree.folders.single().workspaces.single()
        assertEquals("w2", entry.workspace.id)
        assertNull(entry.highlight)
    }

    @Test
    fun `invalid regex returns empty tree with error`() {
        val tree = buildNavigatorTree(
            workspaces = listOf(ws("w1", "One")),
            folderOrder = emptyList(),
            query = "[",
        )
        assertTrue(tree.folders.isEmpty())
        assertTrue(!tree.error.isNullOrBlank())
    }

    @Test
    fun `visible items hide collapsed children and expand all during search`() {
        val tree = buildNavigatorTree(
            workspaces = listOf(ws("w1", "One", group = "A"), ws("w2", "Two")),
            folderOrder = emptyList(),
            query = "",
        )
        val collapsed = visibleNavigatorItems(tree, collapsedFolders = setOf("A"), activeId = null)
        assertEquals(listOf("f:", "w:w2", "f:A"), collapsed.map { it.key })

        val expanded = visibleNavigatorItems(
            tree,
            collapsedFolders = setOf("A"),
            activeId = "w1",
            expandAll = true,
        )
        assertEquals(listOf("f:", "w:w2", "f:A", "w:w1"), expanded.map { it.key })
        val folderA = expanded.first { it.key == "f:A" } as NavigatorItem.FolderItem
        assertTrue(folderA.containsActive)
    }

    @Test
    fun `drop target resolves folders halves and clamps`() {
        val tree = buildNavigatorTree(
            workspaces = listOf(
                ws("a1", "A1", group = "A"),
                ws("a2", "A2", group = "A", order = 1),
                ws("b1", "B1", group = "B"),
            ),
            folderOrder = emptyList(),
            query = "",
        )
        val items = visibleNavigatorItems(tree, emptySet(), activeId = null)
        val spans = items.mapIndexed { index, item ->
            NavigatorItemSpan(item, index * 26f, index * 26f + 26f)
        }
        // f:A(0..26) a1(26..52) a2(52..78) f:B(78..104) b1(104..130)
        val dragged = items.first { it.key == "w:b1" }

        assertEquals(
            NavigatorDropTarget.IntoFolder("A"),
            resolveNavigatorDropTarget(spans, dragged, y = 10f),
        )
        assertEquals(
            NavigatorDropTarget.BeforeWorkspace("A", "a1"),
            resolveNavigatorDropTarget(spans, dragged, y = 35f),
        )
        assertEquals(
            NavigatorDropTarget.BeforeWorkspace("A", null),
            resolveNavigatorDropTarget(spans, dragged, y = 70f),
        )
        assertEquals(
            NavigatorDropTarget.BeforeWorkspace("B", null),
            resolveNavigatorDropTarget(spans, dragged, y = 200f),
        )
        assertEquals(
            NavigatorDropTarget.IntoFolder("A"),
            resolveNavigatorDropTarget(spans, dragged, y = -10f),
        )
    }

    @Test
    fun `folder drop target reorders between folder headers`() {
        val tree = buildNavigatorTree(
            workspaces = listOf(ws("a1", "A1", group = "A"), ws("b1", "B1", group = "B")),
            folderOrder = emptyList(),
            query = "",
        )
        val items = visibleNavigatorItems(tree, emptySet(), activeId = null)
        val spans = items.mapIndexed { index, item ->
            NavigatorItemSpan(item, index * 26f, index * 26f + 26f)
        }
        // f:A(0..26) a1(26..52) f:B(52..78) b1(78..104)
        val dragged = items.first { it.key == "f:A" }

        assertEquals(
            NavigatorDropTarget.FolderPosition("B"),
            resolveNavigatorDropTarget(spans, dragged, y = 60f),
        )
        assertEquals(
            NavigatorDropTarget.FolderPosition(null),
            resolveNavigatorDropTarget(spans, dragged, y = 90f),
        )
    }

    @Test
    fun `workspace drop moves across folders and renumbers order`() {
        val all = listOf(
            ws("a1", "A1", group = "A", order = 0),
            ws("a2", "A2", group = "A", order = 1),
            ws("b1", "B1", group = "B", order = 0),
            ws("b2", "B2", group = "B", order = 1),
        )
        val updated = applyWorkspaceDrop(all, draggedId = "b2", targetFolder = "A", beforeId = "a2")
        val byId = updated.associateBy { it.id }

        assertEquals("A", byId.getValue("b2").group)
        assertEquals(1, byId.getValue("b2").order)
        assertEquals(2, byId.getValue("a2").order)
        assertEquals(0, byId.getValue("a1").order)
        assertEquals("B", byId.getValue("b1").group)
        assertEquals(0, byId.getValue("b1").order)
    }

    @Test
    fun `workspace drop appends to folder end when beforeId is null`() {
        val all = listOf(
            ws("a1", "A1", group = "A"),
            ws("b1", "B1", group = "B", order = 0),
            ws("b2", "B2", group = "B", order = 1),
        )
        val updated = applyWorkspaceDrop(all, draggedId = "a1", targetFolder = "B", beforeId = null)
        val byId = updated.associateBy { it.id }

        assertEquals(2, byId.getValue("a1").order)
        assertEquals("B", byId.getValue("a1").group)
        assertEquals(0, byId.getValue("b1").order)
        assertEquals(1, byId.getValue("b2").order)
    }

    @Test
    fun `folder drop reorders and appends`() {
        assertEquals(
            listOf("C", "A", "B"),
            applyFolderDrop(listOf("A", "B", "C"), dragged = "C", beforeFolder = "A"),
        )
        assertEquals(
            listOf("B", "C", "A"),
            applyFolderDrop(listOf("A", "B", "C"), dragged = "A", beforeFolder = null),
        )
    }

    @Test
    fun `navigator symbol prefers chart panel`() {
        val config = ws("w1", "One", provider = "Binance").copy(
            panels = listOf(
                PanelConfig(id = "p1", type = PanelType.DOM, providerRef = "main", symbol = "ETHUSDT"),
                PanelConfig(id = "p2", type = PanelType.CHART, providerRef = "main", symbol = "SOLUSDT"),
            ),
        )
        assertEquals("SOLUSDT", navigatorPanelSymbol(config))
        assertEquals("Binance", navigatorExchangeLabel(config))
    }

    @Test
    fun `navigator exchange prefers the chart panel provider name`() {
        val config = ws("w1", "One", provider = "Binance").copy(
            panels = listOf(
                PanelConfig(
                    id = "p1",
                    type = PanelType.CHART,
                    providerRef = "main",
                    symbol = "SOLUSDT",
                    providerName = "Bybit",
                ),
            ),
        )
        assertEquals("Bybit", navigatorExchangeLabel(config))
    }

    @Test
    fun `navigator symbol and exchange are empty without data`() {
        assertEquals("", navigatorPanelSymbol(ws("w1", "One")))
        assertEquals("", navigatorExchangeLabel(ws("w1", "One")))
        assertEquals("Bybit", navigatorExchangeLabel(ws("w2", "Two", provider = "Bybit")))
    }
}
