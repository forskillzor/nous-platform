/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.workspace

import com.aandios.nous.core.ui.component.SearchRegexResult
import com.aandios.nous.core.ui.component.compileSearchRegex
import com.aandios.nous.core.ui.component.matchSymbol
import com.aandios.nous.core.workspace.PanelType
import com.aandios.nous.core.workspace.WorkspaceConfig

/** Подпись псевдо-папки для workspace'ов без группы. */
const val NAVIGATOR_UNSORTED_LABEL = "Unsorted"

/** Workspace-строка дерева: конфиг + диапазон подсветки поиска в имени. */
data class NavigatorWorkspaceEntry(
    val workspace: WorkspaceConfig,
    val highlight: IntRange?,
)

/** Папка дерева навигатора: [rawGroup] — группа в WorkspaceConfig ("" = Unsorted). */
data class NavigatorFolderNode(
    val rawGroup: String,
    val name: String,
    val workspaces: List<NavigatorWorkspaceEntry>,
) {
    val isUnsorted: Boolean get() = rawGroup.isEmpty()
}

/** Дерево навигатора: папки в порядке отображения + ошибка regex поиска. */
data class NavigatorTree(
    val folders: List<NavigatorFolderNode>,
    val error: String? = null,
) {
    val isEmpty: Boolean get() = folders.isEmpty()
}

/** Базовый порядок workspace'ов внутри папки: ручной order, затем давность. */
val navigatorWorkspaceComparator: Comparator<WorkspaceConfig> =
    compareBy({ it.order }, { it.createdAt })

fun navigatorExchangeLabel(ws: WorkspaceConfig): String =
    ws.providers.firstOrNull()?.name.orEmpty()

fun navigatorPanelSymbol(ws: WorkspaceConfig): String =
    (ws.panels.firstOrNull { it.type == PanelType.CHART } ?: ws.panels.firstOrNull())?.symbol.orEmpty()

/** Нижняя строка workspace-строки навигатора: «Биржа · Символ» (пустые части отброшены). */
fun navigatorWorkspaceSubline(ws: WorkspaceConfig): String =
    listOf(navigatorExchangeLabel(ws), navigatorPanelSymbol(ws))
        .filter { it.isNotEmpty() }
        .joinToString(" · ")

/**
 * Строит дерево навигатора: папки (Unsorted первой, затем порядок [folderOrder],
 * затем новые папки по алфавиту), внутри — workspace'ы по order/createdAt.
 * При непустом [query] фильтрует единым матчером explorer/Market Watch: матч
 * имени папки показывает всех детей, матч имени workspace даёт диапазон
 * подсветки, матч мета (биржа/папка) — score 4. Ошибка regex возвращает
 * пустое дерево с текстом ошибки.
 */
fun buildNavigatorTree(
    workspaces: List<WorkspaceConfig>,
    folderOrder: List<String>,
    query: String,
): NavigatorTree {
    val byGroup = workspaces.groupBy { it.group }
    val orderedGroups = (folderOrder + byGroup.keys)
        .distinct()
        .sortedWith(
            compareBy(
                { if (it.isEmpty()) 0 else 1 },
                { folderOrder.indexOf(it).let { i -> if (i >= 0) i else Int.MAX_VALUE } },
                { it },
            ),
        )

    if (query.isBlank()) {
        return NavigatorTree(
            orderedGroups.map { group ->
                NavigatorFolderNode(
                    rawGroup = group,
                    name = if (group.isEmpty()) NAVIGATOR_UNSORTED_LABEL else group,
                    workspaces = (byGroup[group] ?: emptyList())
                        .sortedWith(navigatorWorkspaceComparator)
                        .map { NavigatorWorkspaceEntry(it, null) },
                )
            },
        )
    }

    val regex = when (val compiled = compileSearchRegex(query)) {
        is SearchRegexResult.Ok -> compiled.regex
        is SearchRegexResult.Invalid -> return NavigatorTree(emptyList(), compiled.message)
    }

    val folders = orderedGroups.mapNotNull { group ->
        val name = if (group.isEmpty()) NAVIGATOR_UNSORTED_LABEL else group
        val items = byGroup[group] ?: emptyList()
        val folderMatch = regex.containsMatchIn(name)
        val entries = if (folderMatch) {
            items.sortedWith(navigatorWorkspaceComparator).map { ws ->
                NavigatorWorkspaceEntry(ws, matchSymbol(ws.name, emptyList(), regex)?.matchRange)
            }
        } else {
            items
                .mapNotNull { ws ->
                    matchSymbol(
                        displaySymbol = ws.name,
                        metaFields = listOf(name, navigatorExchangeLabel(ws), navigatorPanelSymbol(ws)),
                        regex = regex,
                    )?.let { match -> NavigatorWorkspaceEntry(ws, match.matchRange) to match }
                }
                .sortedWith(compareBy({ it.second.score }, { it.second.position }, { it.second.matchLength }))
                .map { it.first }
        }
        if (entries.isEmpty() && !folderMatch) null else NavigatorFolderNode(group, name, entries)
    }
    return NavigatorTree(folders)
}

/** Видимая строка дерева (папка или workspace). */
sealed interface NavigatorItem {
    val key: String

    data class FolderItem(
        val folder: NavigatorFolderNode,
        val containsActive: Boolean,
    ) : NavigatorItem {
        override val key: String get() = "f:${folder.rawGroup}"
    }

    data class WorkspaceItem(
        val entry: NavigatorWorkspaceEntry,
        val folder: NavigatorFolderNode,
        val indexInFolder: Int,
    ) : NavigatorItem {
        override val key: String get() = "w:${entry.workspace.id}"
    }
}

/**
 * Плоский список видимых строк: заголовки папок + (если раскрыта) дети.
 * [expandAll] используется при активном поиске — результат всегда раскрыт.
 */
fun visibleNavigatorItems(
    tree: NavigatorTree,
    collapsedFolders: Set<String>,
    activeId: String?,
    expandAll: Boolean = false,
): List<NavigatorItem> = buildList {
    tree.folders.forEach { folder ->
        add(
            NavigatorItem.FolderItem(
                folder = folder,
                containsActive = folder.workspaces.any { it.workspace.id == activeId },
            ),
        )
        if (expandAll || folder.rawGroup !in collapsedFolders) {
            folder.workspaces.forEachIndexed { index, entry ->
                add(NavigatorItem.WorkspaceItem(entry, folder, index))
            }
        }
    }
}

/** Цель переноса при drag & drop. */
sealed interface NavigatorDropTarget {
    /** Курсор над заголовком папки — workspace уйдёт в конец папки. */
    data class IntoFolder(val folder: String) : NavigatorDropTarget

    /** Вставка workspace перед [beforeId] (null — в конец папки). */
    data class BeforeWorkspace(val folder: String, val beforeId: String?) : NavigatorDropTarget

    /** Новая позиция папки: перед [beforeFolder] (null — в конец списка). */
    data class FolderPosition(val beforeFolder: String?) : NavigatorDropTarget
}

/** Экранные границы видимой строки (для resolve цели переноса). */
data class NavigatorItemSpan(
    val item: NavigatorItem,
    val top: Float,
    val bottom: Float,
)

private fun List<NavigatorItemSpan>.sortedByTop(): List<NavigatorItemSpan> = sortedBy { it.top }

private fun NavigatorItemSpan.center(): Float = (top + bottom) / 2f

/**
 * Определяет цель переноса по вертикальной позиции курсора [y]:
 * папки принимают workspace'ы целиком, workspace'ы — вставку до/после себя
 * (верхняя/нижняя половина строки), папки переставляются между заголовками.
 */
fun resolveNavigatorDropTarget(
    spans: List<NavigatorItemSpan>,
    dragged: NavigatorItem,
    y: Float,
): NavigatorDropTarget? {
    if (spans.isEmpty()) return null
    val sorted = spans.sortedByTop()
    val hovered = sorted.firstOrNull { y >= it.top && y <= it.bottom }

    return when (dragged) {
        is NavigatorItem.WorkspaceItem -> when (val item = hovered?.item) {
            null -> {
                val first = sorted.first()
                val last = sorted.last()
                if (y < first.top) {
                    when (val target = first.item) {
                        is NavigatorItem.FolderItem -> NavigatorDropTarget.IntoFolder(target.folder.rawGroup)
                        is NavigatorItem.WorkspaceItem ->
                            NavigatorDropTarget.BeforeWorkspace(target.folder.rawGroup, target.entry.workspace.id)
                    }
                } else {
                    when (val target = last.item) {
                        is NavigatorItem.WorkspaceItem -> NavigatorDropTarget.BeforeWorkspace(target.folder.rawGroup, null)
                        is NavigatorItem.FolderItem -> NavigatorDropTarget.IntoFolder(target.folder.rawGroup)
                    }
                }
            }
            is NavigatorItem.FolderItem -> NavigatorDropTarget.IntoFolder(item.folder.rawGroup)
            is NavigatorItem.WorkspaceItem -> {
                if (y < hovered.center()) {
                    NavigatorDropTarget.BeforeWorkspace(item.folder.rawGroup, item.entry.workspace.id)
                } else {
                    val siblings = sorted.filter {
                        val it2 = it.item
                        it2 is NavigatorItem.WorkspaceItem && it2.folder.rawGroup == item.folder.rawGroup
                    }
                    val index = siblings.indexOfFirst {
                        (it.item as NavigatorItem.WorkspaceItem).entry.workspace.id == item.entry.workspace.id
                    }
                    val next = siblings.getOrNull(index + 1)?.item as? NavigatorItem.WorkspaceItem
                    NavigatorDropTarget.BeforeWorkspace(item.folder.rawGroup, next?.entry?.workspace?.id)
                }
            }
        }

        is NavigatorItem.FolderItem -> {
            val folderSpans = sorted.filter {
                val it2 = it.item
                it2 is NavigatorItem.FolderItem && !it2.folder.isUnsorted
            }
            if (folderSpans.isEmpty()) return null
            fun positionFor(boundary: NavigatorItemSpan, above: Boolean): NavigatorDropTarget {
                val folder = (boundary.item as NavigatorItem.FolderItem).folder.rawGroup
                if (above) return NavigatorDropTarget.FolderPosition(folder)
                val index = folderSpans.indexOf(boundary)
                val next = folderSpans.getOrNull(index + 1)?.item as? NavigatorItem.FolderItem
                return NavigatorDropTarget.FolderPosition(next?.folder?.rawGroup)
            }
            val hover = folderSpans.firstOrNull { y >= it.top && y <= it.bottom }
            if (hover != null) return positionFor(hover, y < hover.center())
            val nearest = folderSpans.minByOrNull { minOf(kotlin.math.abs(y - it.top), kotlin.math.abs(y - it.bottom)) } ?: return null
            return positionFor(nearest, y < nearest.center())
        }
    }
}

/**
 * Переносит workspace в папку [targetFolder] перед [beforeId] (null — в конец),
 * пересчитывает order затронутой папки. Возвращает обновлённый полный список;
 * изменённые элементы можно персистить сравнением с исходным.
 */
fun applyWorkspaceDrop(
    all: List<WorkspaceConfig>,
    draggedId: String,
    targetFolder: String,
    beforeId: String?,
): List<WorkspaceConfig> {
    val dragged = all.firstOrNull { it.id == draggedId } ?: return all
    val siblings = all
        .filter { it.group == targetFolder && it.id != draggedId }
        .sortedWith(navigatorWorkspaceComparator)
    val insertAt = beforeId
        ?.let { id -> siblings.indexOfFirst { it.id == id } }
        ?.takeIf { it >= 0 }
        ?: siblings.size
    val reordered = siblings.toMutableList().apply { add(insertAt, dragged) }

    val changed = reordered
        .mapIndexedNotNull { index, ws ->
            val copy = ws.copy(group = targetFolder, order = index)
            if (ws == copy) null else copy
        }
        .associateBy { it.id }
    return all.map { changed[it.id] ?: it }
}

/** Новый порядок папок (без Unsorted) при переносе [dragged] перед [beforeFolder]. */
fun applyFolderDrop(
    displayOrder: List<String>,
    dragged: String,
    beforeFolder: String?,
): List<String> {
    val without = displayOrder.filterNot { it == dragged }
    val insertAt = beforeFolder
        ?.let { boundary -> without.indexOfFirst { it == boundary } }
        ?.takeIf { it >= 0 }
        ?: without.size
    return without.toMutableList().apply { add(insertAt, dragged) }
}
