/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import kotlin.math.roundToInt

private val ExplorerListsMenuWidth = 220.dp
private val ExplorerListsTriggerMaxWidth = 160.dp

/** Итоговый индекс списка после перестановки в дропдауне. */
internal fun explorerListReorderTarget(fromIndex: Int, boundary: Int, count: Int): Int {
    if (count <= 0) return 0
    val insertBefore = boundary.coerceIn(0, count)
    val target = if (insertBefore > fromIndex) insertBefore - 1 else insertBefore
    return target.coerceIn(0, count - 1)
}

/**
 * Полный порядок списков после перестановки в дропдауне: позиция
 * Favourites не меняется, остальные списки идут в [dropdownOrder].
 */
internal fun explorerMergedWatchlistOrder(
    watchlists: List<SymbolWatchlist>,
    dropdownOrder: List<String>,
): List<String> {
    val pending = ArrayDeque(dropdownOrder)
    return watchlists.map { list ->
        if (list.isFavourites()) list.id else pending.removeFirst()
    }
}

/**
 * Строка табов списков: ALL и Favourites закреплены, остальные списки живут
 * в дропдауне — там же переставляются драгом.
 */
@Composable
internal fun ExplorerTabsRow(
    watchlists: List<SymbolWatchlist>,
    activeListId: String?,
    onActiveListIdChange: (String?) -> Unit,
    onReorderWatchlists: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    showFootprintTab: Boolean = false,
    footprintActive: Boolean = false,
    onFootprintClick: () -> Unit = {},
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val tokens = LocalTerminalDesignTokens.current
    val favourites = remember(watchlists) { watchlists.favouritesWatchlist() }
    val dropdownLists = remember(watchlists, favourites) {
        watchlists.filterNot { it.id == favourites?.id }
    }
    val activeDropdownList = dropdownLists.firstOrNull { it.id == activeListId }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(tokens.spacing.sm),
    ) {
        ExplorerTab("ALL", active = !footprintActive && activeListId == null) {
            onActiveListIdChange(null)
        }
        if (favourites != null) {
            ExplorerTab(
                label = favourites.name.uppercase(),
                active = !footprintActive && activeListId == favourites.id,
            ) { onActiveListIdChange(favourites.id) }
        }
        if (dropdownLists.isNotEmpty()) {
            TerminalBoxDropdown(
                text = activeDropdownList?.name?.uppercase() ?: "LISTS",
                modifier = Modifier.widthIn(max = ExplorerListsTriggerMaxWidth),
                style = if (!footprintActive && activeDropdownList != null) {
                    TerminalBoxStyle.ACCENT
                } else {
                    TerminalBoxStyle.NORMAL
                },
                menuWidth = ExplorerListsMenuWidth,
            ) { dismiss ->
                ExplorerListsMenuContent(
                    dropdownLists = dropdownLists,
                    watchlists = watchlists,
                    activeListId = activeListId,
                    onSelect = { id ->
                        onActiveListIdChange(id)
                        dismiss()
                    },
                    onReorderWatchlists = onReorderWatchlists,
                )
            }
        }
        if (showFootprintTab) {
            ExplorerTab("FOOTPRINT", active = footprintActive) { onFootprintClick() }
        }
        Spacer(Modifier.weight(1f))
        trailing?.invoke(this)
    }
}

/** Содержимое дропдауна: выбор списка и перестановка драгом внутри меню. */
@Composable
private fun ExplorerListsMenuContent(
    dropdownLists: List<SymbolWatchlist>,
    watchlists: List<SymbolWatchlist>,
    activeListId: String?,
    onSelect: (String) -> Unit,
    onReorderWatchlists: (List<String>) -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    val density = LocalDensity.current
    val itemHeightPx = with(density) { TerminalMenuItemHeight.toPx() }
    val ids = remember(dropdownLists) { dropdownLists.map { it.id } }
    var drag by remember { mutableStateOf<ExplorerListDrag?>(null) }

    dropdownLists.forEachIndexed { index, list ->
        val current = drag?.takeIf { it.listId == list.id }
        Box(
            modifier = Modifier
                .zIndex(if (current != null) 1f else 0f)
                .offset { IntOffset(0, current?.offsetY?.roundToInt() ?: 0) }
                .graphicsLayer { alpha = if (current != null) 0.9f else 1f }
                .pointerInput(list.id, index, dropdownLists.size) {
                    var pressY = 0f
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            pressY = offset.y
                            drag = ExplorerListDrag(list.id, index)
                        },
                        onDragCancel = { drag = null },
                        onVerticalDrag = { _, dragAmount ->
                            val active = drag ?: return@detectVerticalDragGestures
                            if (active.listId != list.id) return@detectVerticalDragGestures
                            active.offsetY += dragAmount
                            active.boundary =
                                (index + (pressY + active.offsetY) / itemHeightPx)
                                    .roundToInt()
                                    .coerceIn(0, dropdownLists.size)
                        },
                        onDragEnd = {
                            val active = drag
                            drag = null
                            if (active == null) return@detectVerticalDragGestures
                            val target = explorerListReorderTarget(
                                fromIndex = active.fromIndex,
                                boundary = active.boundary,
                                count = dropdownLists.size,
                            )
                            if (target != active.fromIndex) {
                                val reordered = ids.toMutableList()
                                reordered.add(target, reordered.removeAt(active.fromIndex))
                                onReorderWatchlists(
                                    explorerMergedWatchlistOrder(watchlists, reordered),
                                )
                            }
                        },
                    )
                },
        ) {
            TerminalMenuItem(
                text = list.name.uppercase(),
                selected = activeListId == list.id,
                onClick = { if (current == null) onSelect(list.id) },
            )
            if (current != null && current.boundary == index && current.fromIndex != index) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .fillMaxWidth()
                        .padding(horizontal = tokens.spacing.xs)
                        .height(2.dp)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
    if (drag?.boundary == dropdownLists.size) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.xs)
                .height(2.dp)
                .background(MaterialTheme.colorScheme.primary),
        )
    }
}

/** Активный драг списка внутри дропдауна. */
private class ExplorerListDrag(
    val listId: String,
    val fromIndex: Int,
) {
    var offsetY by mutableFloatStateOf(0f)
    var boundary by mutableIntStateOf(fromIndex)
}

@Composable
private fun ExplorerTab(label: String, active: Boolean, onClick: () -> Unit) {
    val tokens = LocalTerminalDesignTokens.current
    Text(
        text = label,
        color = if (active) MaterialTheme.colorScheme.surface else tokens.colors.label,
        fontSize = 10.sp,
        lineHeight = tokens.typography.controlLineHeight,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
        fontFamily = TerminalFontFamily,
        letterSpacing = 0.5.sp,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
            .background(if (active) tokens.colors.accent else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
    )
}
