/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.workspace

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aandios.nous.core.ui.component.TerminalContextPopup
import com.aandios.nous.core.ui.component.TerminalDropdownMenu
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import com.aandios.nous.core.workspace.WorkspaceConfig
import com.aandios.nous.core.workspace.currentTime
import kotlin.math.roundToInt

/**
 * Navigator workspace'ов (левая tool-панель): заголовок с созданием папки,
 * поиск единым матчером explorer/Market Watch, дерево папок с workspace'ами
 * (drag & drop папок и workspace'ов, контекстные меню Rename/Duplicate/
 * Make Template/Delete) и Quick Access с шаблонами. Одиночный клик открывает
 * preview-вкладку, двойной — закреплённую. Папка — это [WorkspaceConfig.group],
 * порядок папок и свёрнутость персистит вызывающая сторона (NavigatorState).
 */
@Composable
fun WorkspaceNavigator(
    workspaces: List<WorkspaceConfig>,
    activeId: String?,
    folderOrder: List<String>,
    collapsedFolders: Set<String>,
    templates: List<Pair<String, WorkspaceConfig>>,
    onWorkspacePreview: (WorkspaceConfig) -> Unit,
    onWorkspaceOpen: (WorkspaceConfig) -> Unit,
    onNewWorkspace: () -> Unit,
    onCreateFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onRenameWorkspace: (WorkspaceConfig, String) -> Unit,
    onDeleteWorkspace: (WorkspaceConfig) -> Unit,
    onMoveWorkspace: (WorkspaceConfig, String, String?) -> Unit,
    onCollapsedFoldersChange: (Set<String>) -> Unit,
    onFolderOrderChange: (List<String>) -> Unit,
    onCreateFromTemplate: (String, WorkspaceConfig) -> Unit,
    modifier: Modifier = Modifier,
    onMakeTemplate: ((WorkspaceConfig) -> Unit)? = null,
    onDuplicateWorkspace: ((WorkspaceConfig) -> Unit)? = null,
) {
    val tokens = LocalTerminalDesignTokens.current
    val accent = MaterialTheme.colorScheme.primary

    var query by remember { mutableStateOf("") }
    var headerMenuExpanded by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf<String?>(null) }
    var templatesExpanded by remember { mutableStateOf(false) }

    val tree = remember(workspaces, folderOrder, query) {
        buildNavigatorTree(workspaces, folderOrder, query)
    }
    val items = remember(tree, collapsedFolders, activeId, query) {
        visibleNavigatorItems(tree, collapsedFolders, activeId, expandAll = query.isNotBlank())
    }

    var dragPayload by remember { mutableStateOf<NavigatorItem?>(null) }
    var dropTarget by remember { mutableStateOf<NavigatorDropTarget?>(null) }
    val rowBounds = remember { mutableStateMapOf<String, Rect>() }
    var listTopLeft by remember { mutableStateOf(Offset.Zero) }
    var pointerRel by remember { mutableStateOf(Offset.Zero) }

    fun beginDrag(item: NavigatorItem, localPosition: Offset) {
        val validKeys = items.map { it.key }.toSet()
        rowBounds.keys.toList().forEach { if (it !in validKeys) rowBounds.remove(it) }
        dragPayload = item
        dropTarget = null
        pointerRel = (rowBounds[item.key]?.topLeft ?: Offset.Zero) + localPosition - listTopLeft
    }

    fun updateDrag(item: NavigatorItem, localPosition: Offset) {
        val rect = rowBounds[item.key] ?: return
        val abs = rect.topLeft + localPosition
        pointerRel = abs - listTopLeft
        val target = resolveNavigatorDropTarget(
            spans = items.mapNotNull { candidate ->
                rowBounds[candidate.key]?.let { NavigatorItemSpan(candidate, it.top, it.bottom) }
            },
            dragged = item,
            y = abs.y,
        )
        if (target is NavigatorDropTarget.IntoFolder && target.folder in collapsedFolders) {
            onCollapsedFoldersChange(collapsedFolders - target.folder)
        }
        dropTarget = target
    }

    fun finishDrag() {
        val dragged = dragPayload
        val target = dropTarget
        dragPayload = null
        dropTarget = null
        if (dragged == null || target == null) return
        when (dragged) {
            is NavigatorItem.WorkspaceItem -> {
                val ws = dragged.entry.workspace
                when (target) {
                    is NavigatorDropTarget.IntoFolder -> onMoveWorkspace(ws, target.folder, null)
                    is NavigatorDropTarget.BeforeWorkspace -> onMoveWorkspace(ws, target.folder, target.beforeId)
                    is NavigatorDropTarget.FolderPosition -> Unit
                }
            }

            is NavigatorItem.FolderItem -> if (target is NavigatorDropTarget.FolderPosition) {
                val display = items
                    .filterIsInstance<NavigatorItem.FolderItem>()
                    .map { it.folder.rawGroup }
                    .filter { it.isNotEmpty() }
                onFolderOrderChange(applyFolderDrop(display, dragged.folder.rawGroup, target.beforeFolder))
            }
        }
    }

    fun cancelDrag() {
        dragPayload = null
        dropTarget = null
    }

    val lineAboveKey: String? = when (val target = dropTarget) {
        is NavigatorDropTarget.BeforeWorkspace -> target.beforeId?.let { "w:$it" }
        is NavigatorDropTarget.FolderPosition -> target.beforeFolder?.let { "f:$it" }
        else -> null
    }
    val endLineKey: String? = run {
        val target = dropTarget as? NavigatorDropTarget.FolderPosition ?: return@run null
        if (target.beforeFolder != null) return@run null
        val lastFolderIndex = items.indexOfLast { it is NavigatorItem.FolderItem && !it.folder.isUnsorted }
        if (lastFolderIndex < 0) return@run null
        val group = (items[lastFolderIndex] as NavigatorItem.FolderItem).folder.rawGroup
        var last = lastFolderIndex
        for (index in lastFolderIndex + 1 until items.size) {
            val candidate = items[index]
            if (candidate is NavigatorItem.WorkspaceItem && candidate.folder.rawGroup == group) last = index
        }
        items[last].key
    }
    val appendLineFolder: String? = (dropTarget as? NavigatorDropTarget.BeforeWorkspace)
        ?.takeIf { it.beforeId == null }
        ?.folder

    val draggable = query.isBlank()

    Column(modifier = modifier.fillMaxSize().background(tokens.colors.panelSurface)) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
                    .padding(horizontal = tokens.spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MosaicGlyph(color = accent, size = 13.dp)
                Spacer(Modifier.width(tokens.spacing.sm))
                Text(
                    text = "Workspaces",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = tokens.typography.controlSize,
                    lineHeight = tokens.typography.controlLineHeight,
                    fontWeight = FontWeight.Bold,
                    fontFamily = TerminalFontFamily,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                NavigatorActionButton(onClick = { newFolderName = "" }, description = "New folder") {
                    FolderPlusGlyph(color = accent, size = 14.dp)
                }
                Spacer(Modifier.width(tokens.spacing.xxs))
                NavigatorActionButton(onClick = { headerMenuExpanded = true }, description = "More actions") {
                    DotsGlyph(color = tokens.colors.label)
                }
            }
            NavigatorMenu(expanded = headerMenuExpanded, onDismiss = { headerMenuExpanded = false }) {
                NavigatorMenuItem("New Workspace") {
                    headerMenuExpanded = false
                    onNewWorkspace()
                }
                NavigatorMenuItem("New Folder") {
                    headerMenuExpanded = false
                    newFolderName = ""
                }
                NavigatorMenuItem("Expand All") {
                    headerMenuExpanded = false
                    onCollapsedFoldersChange(emptySet())
                }
                NavigatorMenuItem("Collapse All") {
                    headerMenuExpanded = false
                    onCollapsedFoldersChange(
                        items.filterIsInstance<NavigatorItem.FolderItem>().map { it.folder.rawGroup }.toSet(),
                    )
                }
            }
        }

        newFolderName?.let { name ->
            TerminalContextPopup(
                expanded = true,
                onDismiss = { newFolderName = null },
                title = "New folder",
                message = "Workspaces can be dragged into the folder afterwards.",
                value = name,
                onValueChange = { newFolderName = it },
                placeholder = "Folder name",
                confirmText = "Create",
                onConfirm = {
                    val trimmed = name.trim()
                    newFolderName = null
                    if (trimmed.isNotEmpty() && trimmed != NAVIGATOR_UNSORTED_LABEL) onCreateFolder(trimmed)
                },
            )
        }

        NavigatorSearchField(
            query = query,
            onQueryChange = { query = it },
            modifier = Modifier.padding(horizontal = tokens.spacing.md),
        )

        tree.error?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                fontSize = tokens.typography.smallSize,
                lineHeight = tokens.typography.compactLineHeight,
                fontFamily = TerminalFontFamily,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xxs),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "WORKSPACES",
                color = tokens.colors.label,
                fontSize = tokens.typography.smallSize,
                lineHeight = tokens.typography.compactLineHeight,
                letterSpacing = tokens.typography.compactLetterSpacing,
                fontWeight = FontWeight.Medium,
                fontFamily = TerminalFontFamily,
                modifier = Modifier.weight(1f),
            )
            NavigatorActionButton(onClick = onNewWorkspace, description = "New workspace") {
                PlusGlyph(color = tokens.colors.label, size = 12.dp)
            }
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (items.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize().padding(tokens.spacing.md),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = if (query.isNotBlank()) "Nothing found" else "No workspaces yet",
                        color = tokens.colors.label,
                        fontSize = tokens.typography.compactSize,
                        lineHeight = tokens.typography.controlLineHeight,
                        fontFamily = TerminalFontFamily,
                    )
                    if (query.isBlank()) {
                        Text(
                            text = "Create one from a template below",
                            color = tokens.colors.label.copy(alpha = 0.6f),
                            fontSize = tokens.typography.smallSize,
                            lineHeight = tokens.typography.compactLineHeight,
                            fontFamily = TerminalFontFamily,
                            modifier = Modifier.padding(top = tokens.spacing.xs),
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .onGloballyPositioned { listTopLeft = it.boundsInRoot().topLeft },
                ) {
                    items(items, key = { it.key }) { item ->
                        when (item) {
                            is NavigatorItem.FolderItem -> NavigatorFolderRow(
                                item = item,
                                collapsed = query.isBlank() && item.folder.rawGroup in collapsedFolders,
                                dropHighlight = dropTarget == NavigatorDropTarget.IntoFolder(item.folder.rawGroup),
                                showLineAbove = lineAboveKey == item.key,
                                showLineBelow = endLineKey == item.key,
                                isDragged = dragPayload?.key == item.key,
                                draggable = draggable,
                                onToggle = {
                                    if (item.folder.rawGroup in collapsedFolders) {
                                        onCollapsedFoldersChange(collapsedFolders - item.folder.rawGroup)
                                    } else {
                                        onCollapsedFoldersChange(collapsedFolders + item.folder.rawGroup)
                                    }
                                },
                                onRename = { newName -> onRenameFolder(item.folder.rawGroup, newName) },
                                onDelete = { onDeleteFolder(item.folder.rawGroup) },
                                onDragStart = { beginDrag(item, it) },
                                onDrag = { updateDrag(item, it) },
                                onDragEnd = { finishDrag() },
                                onDragCancel = { cancelDrag() },
                                modifier = Modifier.onGloballyPositioned { rowBounds[item.key] = it.boundsInRoot() },
                            )

                            is NavigatorItem.WorkspaceItem -> NavigatorWorkspaceRow(
                                item = item,
                                isActive = item.entry.workspace.id == activeId,
                                showLineAbove = lineAboveKey == item.key,
                                showLineBelow = endLineKey == item.key ||
                                    (appendLineFolder == item.folder.rawGroup &&
                                        item.indexInFolder == item.folder.workspaces.lastIndex),
                                isDragged = dragPayload?.key == item.key,
                                draggable = draggable,
                                onClick = { onWorkspacePreview(item.entry.workspace) },
                                onDoubleClick = { onWorkspaceOpen(item.entry.workspace) },
                                onDuplicate = onDuplicateWorkspace?.let { fn ->
                                    { fn(item.entry.workspace) }
                                },
                                onRename = { newName -> onRenameWorkspace(item.entry.workspace, newName) },
                                onDelete = { onDeleteWorkspace(item.entry.workspace) },
                                onMakeTemplate = onMakeTemplate?.let { fn ->
                                    { fn(item.entry.workspace) }
                                },
                                onDragStart = { beginDrag(item, it) },
                                onDrag = { updateDrag(item, it) },
                                onDragEnd = { finishDrag() },
                                onDragCancel = { cancelDrag() },
                                modifier = Modifier.onGloballyPositioned { rowBounds[item.key] = it.boundsInRoot() },
                            )
                        }
                    }
                }
            }

            dragPayload?.let { payload ->
                val label = when (payload) {
                    is NavigatorItem.FolderItem -> payload.folder.name
                    is NavigatorItem.WorkspaceItem -> payload.entry.workspace.name
                }
                Row(
                    modifier = Modifier
                        .offset { IntOffset(pointerRel.x.roundToInt() + 12, (pointerRel.y - 11.dp.toPx()).roundToInt()) }
                        .zIndex(3f)
                        .widthIn(max = 200.dp)
                        .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
                        .background(tokens.colors.selection)
                        .border(
                            tokens.dimensions.borderWidth,
                            accent,
                            RoundedCornerShape(tokens.cornerRadii.smallCard),
                        )
                        .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    when (payload) {
                        is NavigatorItem.FolderItem -> FolderGlyph(color = accent, size = 11.dp)
                        is NavigatorItem.WorkspaceItem -> MosaicGlyph(color = accent, size = 11.dp)
                    }
                    Spacer(Modifier.width(tokens.spacing.sm))
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = tokens.typography.compactSize,
                        lineHeight = tokens.typography.controlLineHeight,
                        fontFamily = TerminalFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        HorizontalDivider(
            color = tokens.colors.panelBorder,
            thickness = tokens.dimensions.borderWidth,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { templatesExpanded = !templatesExpanded }
                .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "QUICK ACCESS",
                color = tokens.colors.label,
                fontSize = tokens.typography.smallSize,
                lineHeight = tokens.typography.compactLineHeight,
                letterSpacing = tokens.typography.compactLetterSpacing,
                fontWeight = FontWeight.Medium,
                fontFamily = TerminalFontFamily,
                modifier = Modifier.weight(1f),
            )
            ChevronGlyph(collapsed = !templatesExpanded, color = tokens.colors.label)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = tokens.spacing.md)
                .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
                .clickable { templatesExpanded = !templatesExpanded }
                .padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LayersGlyph(color = accent, size = 13.dp)
            Spacer(Modifier.width(tokens.spacing.sm))
            Text(
                text = "Workspace templates",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = tokens.typography.compactSize,
                lineHeight = tokens.typography.controlLineHeight,
                fontFamily = TerminalFontFamily,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = templates.size.toString(),
                color = tokens.colors.label,
                fontSize = tokens.typography.smallSize,
                lineHeight = tokens.typography.compactLineHeight,
                fontFamily = TerminalFontFamily,
            )
        }

        if (templatesExpanded) {
            if (templates.isEmpty()) {
                Text(
                    text = "No templates",
                    color = tokens.colors.label.copy(alpha = 0.6f),
                    fontSize = tokens.typography.smallSize,
                    lineHeight = tokens.typography.compactLineHeight,
                    fontFamily = TerminalFontFamily,
                    modifier = Modifier.padding(start = 26.dp, end = tokens.spacing.md, bottom = tokens.spacing.xs),
                )
            } else {
                templates.forEach { (label, config) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = tokens.spacing.md)
                            .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
                            .clickable { onCreateFromTemplate(label, config) }
                            .padding(start = tokens.spacing.lg, end = tokens.spacing.sm, top = tokens.spacing.xxs, bottom = tokens.spacing.xxs),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MosaicGlyph(color = tokens.colors.label, size = 11.dp)
                        Spacer(Modifier.width(tokens.spacing.sm))
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = tokens.typography.compactSize,
                            lineHeight = tokens.typography.controlLineHeight,
                            fontFamily = TerminalFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(tokens.spacing.xs))
    }
}

@Composable
private fun NavigatorSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTerminalDesignTokens.current
    val focusRequester = remember { FocusRequester() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                RoundedCornerShape(tokens.cornerRadii.smallCard),
            )
            .border(
                tokens.dimensions.borderWidth,
                tokens.colors.panelBorder,
                RoundedCornerShape(tokens.cornerRadii.smallCard),
            )
            .padding(horizontal = tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchGlyph(color = tokens.colors.label)
        Spacer(Modifier.width(tokens.spacing.sm))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = "Search workspaces",
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    fontSize = tokens.typography.compactSize,
                    lineHeight = tokens.typography.controlLineHeight,
                    fontFamily = TerminalFontFamily,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = tokens.typography.compactSize,
                    fontFamily = TerminalFontFamily,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            onQueryChange("")
                            true
                        } else {
                            false
                        }
                    },
            )
        }
        Spacer(Modifier.width(tokens.spacing.sm))
        Text(
            text = "⌘ K",
            color = tokens.colors.label,
            fontSize = tokens.typography.microSize,
            lineHeight = tokens.typography.compactLineHeight,
            fontFamily = TerminalFontFamily,
            modifier = Modifier
                .background(tokens.colors.panelHeaderSurface, RoundedCornerShape(3.dp))
                .border(
                    tokens.dimensions.borderWidth,
                    tokens.colors.panelBorder,
                    RoundedCornerShape(3.dp),
                )
                .padding(horizontal = tokens.spacing.xs, vertical = tokens.spacing.xxs),
        )
    }
}

@Composable
private fun NavigatorFolderRow(
    item: NavigatorItem.FolderItem,
    collapsed: Boolean,
    dropHighlight: Boolean,
    showLineAbove: Boolean,
    showLineBelow: Boolean,
    isDragged: Boolean,
    draggable: Boolean,
    onToggle: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTerminalDesignTokens.current
    val accent = MaterialTheme.colorScheme.primary
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    val currentDragCancel by rememberUpdatedState(onDragCancel)

    Box(modifier = modifier.fillMaxWidth().graphicsLayer { alpha = if (isDragged) 0.4f else 1f }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
                .background(
                    when {
                        dropHighlight -> accent.copy(alpha = 0.16f)
                        hovered -> tokens.colors.panelHeaderSurface
                        else -> Color.Transparent
                    },
                )
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) {
                    if (!editing) onToggle()
                }
                .pointerInput(draggable, item.key) {
                    if (draggable) {
                        detectDragGestures(
                            onDragStart = { currentDragStart(it) },
                            onDrag = { change, _ -> currentDrag(change.position) },
                            onDragEnd = { currentDragEnd() },
                            onDragCancel = { currentDragCancel() },
                        )
                    }
                }
                .padding(horizontal = tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChevronGlyph(collapsed = collapsed, color = tokens.colors.label)
            Spacer(Modifier.width(tokens.spacing.xs))
            FolderGlyph(color = accent.copy(alpha = 0.9f), size = 13.dp)
            Spacer(Modifier.width(tokens.spacing.sm))
            if (editing) {
                InlineRenameField(
                    initial = item.folder.name,
                    onSubmit = { value ->
                        editing = false
                        val trimmed = value.trim()
                        if (trimmed.isNotEmpty() && trimmed != item.folder.name) onRename(trimmed)
                    },
                    onCancel = { editing = false },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Text(
                    text = item.folder.name,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = tokens.typography.controlSize,
                    lineHeight = tokens.typography.controlLineHeight,
                    fontWeight = FontWeight.Medium,
                    fontFamily = TerminalFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (collapsed && item.containsActive) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                    Spacer(Modifier.width(tokens.spacing.xs))
                }
                Text(
                    text = item.folder.workspaces.size.toString(),
                    color = tokens.colors.label,
                    fontSize = tokens.typography.smallSize,
                    lineHeight = tokens.typography.compactLineHeight,
                    fontFamily = TerminalFontFamily,
                )
                Spacer(Modifier.width(tokens.spacing.xs))
                RowActionDots(visible = hovered || menuExpanded) { menuExpanded = true }
            }
        }
        if (showLineAbove) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(tokens.dimensions.workspaceDropIndicatorWidth)
                    .background(accent),
            )
        }
        if (showLineBelow) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(tokens.dimensions.workspaceDropIndicatorWidth)
                    .background(accent),
            )
        }
        NavigatorMenu(expanded = menuExpanded, onDismiss = { menuExpanded = false }) {
            NavigatorMenuItem("Rename") {
                menuExpanded = false
                editing = true
            }
            NavigatorMenuItem("Delete", destructive = true) {
                menuExpanded = false
                confirmDelete = true
            }
        }
        if (confirmDelete) {
            TerminalContextPopup(
                expanded = true,
                onDismiss = { confirmDelete = false },
                title = "Delete folder",
                message = "Folder \"${item.folder.name}\" will be deleted; " +
                    "${item.folder.workspaces.size} workspace(s) will move to $NAVIGATOR_UNSORTED_LABEL.",
                confirmText = "Delete",
                destructive = true,
                onConfirm = {
                    confirmDelete = false
                    onDelete()
                },
            )
        }
    }
}

/** Workspace-строка навигатора в стиле Market Watch: две линии, иконка в колонке как star. */
private val NAVIGATOR_WORKSPACE_ROW_HEIGHT = 46.dp
private val NAVIGATOR_WORKSPACE_ICON_WIDTH = 34.dp
private val NAVIGATOR_WORKSPACE_GLYPH_SIZE = 16.dp

/** Окно распознавания двойного клика (мс): одиночный срабатывает мгновенно. */
private const val NAVIGATOR_DOUBLE_TAP_WINDOW_MS = 400L

@Composable
private fun NavigatorWorkspaceRow(
    item: NavigatorItem.WorkspaceItem,
    isActive: Boolean,
    showLineAbove: Boolean,
    showLineBelow: Boolean,
    isDragged: Boolean,
    draggable: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onMakeTemplate: (() -> Unit)?,
    onDuplicate: (() -> Unit)?,
    onDragStart: (Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTerminalDesignTokens.current
    val accent = MaterialTheme.colorScheme.primary
    val ws = item.entry.workspace
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var lastTapAt by remember { mutableStateOf(0L) }
    val currentDragStart by rememberUpdatedState(onDragStart)
    val currentDrag by rememberUpdatedState(onDrag)
    val currentDragEnd by rememberUpdatedState(onDragEnd)
    val currentDragCancel by rememberUpdatedState(onDragCancel)
    val subline = navigatorWorkspaceSubline(ws)

    Box(modifier = modifier.fillMaxWidth().graphicsLayer { alpha = if (isDragged) 0.4f else 1f }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NAVIGATOR_WORKSPACE_ROW_HEIGHT)
                .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
                .background(
                    when {
                        isActive -> tokens.colors.selection
                        hovered -> tokens.colors.panelHeaderSurface
                        else -> Color.Transparent
                    },
                )
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) {
                    if (!editing) {
                        val now = currentTime()
                        val doubleTap = now - lastTapAt <= NAVIGATOR_DOUBLE_TAP_WINDOW_MS
                        lastTapAt = now
                        onClick()
                        if (doubleTap) onDoubleClick()
                    }
                }
                .pointerInput(draggable, item.key) {
                    if (draggable) {
                        detectDragGestures(
                            onDragStart = { currentDragStart(it) },
                            onDrag = { change, _ -> currentDrag(change.position) },
                            onDragEnd = { currentDragEnd() },
                            onDragCancel = { currentDragCancel() },
                        )
                    }
                }
                .padding(end = tokens.spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .fillMaxHeight()
                    .background(if (isActive) accent else Color.Transparent),
            )
            Box(
                modifier = Modifier
                    .width(NAVIGATOR_WORKSPACE_ICON_WIDTH)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                MosaicGlyph(
                    color = if (isActive) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                    size = NAVIGATOR_WORKSPACE_GLYPH_SIZE,
                )
            }
            if (editing) {
                InlineRenameField(
                    initial = ws.name,
                    onSubmit = { value ->
                        editing = false
                        val trimmed = value.trim()
                        if (trimmed.isNotEmpty() && trimmed != ws.name) onRename(trimmed)
                    },
                    onCancel = { editing = false },
                    modifier = Modifier.weight(1f),
                )
            } else {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = highlightedName(ws.name, item.entry.highlight, accent),
                        color = if (isActive) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = tokens.typography.controlSize,
                        lineHeight = tokens.typography.controlLineHeight,
                        fontFamily = TerminalFontFamily,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subline.isNotEmpty()) {
                        Text(
                            text = subline,
                            color = tokens.colors.label,
                            fontSize = tokens.typography.microSize,
                            lineHeight = tokens.typography.compactLineHeight,
                            fontFamily = TerminalFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                Spacer(Modifier.width(tokens.spacing.xs))
                RowActionDots(visible = hovered || menuExpanded) { menuExpanded = true }
            }
        }
        if (showLineAbove) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .height(tokens.dimensions.workspaceDropIndicatorWidth)
                    .background(accent),
            )
        }
        if (showLineBelow) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .height(tokens.dimensions.workspaceDropIndicatorWidth)
                    .background(accent),
            )
        }
        NavigatorMenu(expanded = menuExpanded, onDismiss = { menuExpanded = false }) {
            NavigatorMenuItem("Rename") {
                menuExpanded = false
                editing = true
            }
            onDuplicate?.let { fn ->
                NavigatorMenuItem("Duplicate") {
                    menuExpanded = false
                    fn()
                }
            }
            onMakeTemplate?.let { fn ->
                NavigatorMenuItem("Make Template") {
                    menuExpanded = false
                    fn()
                }
            }
            NavigatorMenuItem("Delete", destructive = true) {
                menuExpanded = false
                confirmDelete = true
            }
        }
        if (confirmDelete) {
            TerminalContextPopup(
                expanded = true,
                onDismiss = { confirmDelete = false },
                title = "Delete workspace",
                message = "Workspace \"${ws.name}\" will be permanently deleted.",
                confirmText = "Delete",
                destructive = true,
                onConfirm = {
                    confirmDelete = false
                    onDelete()
                },
            )
        }
    }
    HorizontalDivider(
        color = tokens.colors.panelBorder,
        thickness = tokens.dimensions.borderWidth,
    )
}

@Composable
private fun InlineRenameField(
    initial: String,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTerminalDesignTokens.current
    var value by remember { mutableStateOf(initial) }
    var finished by remember { mutableStateOf(false) }
    var hadFocus by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
    fun finish(action: () -> Unit) {
        if (!finished) {
            finished = true
            action()
        }
    }
    BasicTextField(
        value = value,
        onValueChange = { value = it },
        singleLine = true,
        textStyle = TextStyle(
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = tokens.typography.compactSize,
            fontFamily = TerminalFontFamily,
        ),
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        modifier = modifier
            .height(20.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                RoundedCornerShape(tokens.cornerRadii.smallCard),
            )
            .border(
                tokens.dimensions.borderWidth,
                MaterialTheme.colorScheme.primary,
                RoundedCornerShape(tokens.cornerRadii.smallCard),
            )
            .padding(horizontal = tokens.spacing.sm)
            .focusRequester(focusRequester)
            .onFocusChanged { state ->
                if (state.isFocused) hadFocus = true
                else if (hadFocus) finish { onSubmit(value) }
            }
            .onPreviewKeyEvent { event ->
                when {
                    event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                        finish { onSubmit(value) }
                        true
                    }

                    event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                        finish { onCancel() }
                        true
                    }

                    else -> false
                }
            },
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) { inner() }
        },
    )
}

@Composable
private fun NavigatorMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    TerminalDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.widthIn(min = 150.dp).padding(vertical = tokens.spacing.xs)) {
            content()
        }
    }
}

@Composable
private fun NavigatorMenuItem(
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.xs)
            .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
            .background(if (hovered) tokens.colors.panelHeaderSurface else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (destructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.9f),
            fontSize = tokens.typography.compactSize,
            lineHeight = tokens.typography.controlLineHeight,
            fontFamily = TerminalFontFamily,
            maxLines = 1,
        )
    }
}

@Composable
private fun NavigatorActionButton(
    onClick: () -> Unit,
    description: String,
    content: @Composable () -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
            .background(if (hovered) tokens.colors.panelHeaderSurface else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun RowActionDots(visible: Boolean, onClick: () -> Unit) {
    val tokens = LocalTerminalDesignTokens.current
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
            .background(if (hovered) tokens.colors.panelBorder.copy(alpha = 0.4f) else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        DotsGlyph(color = if (visible) tokens.colors.label else Color.Transparent, size = 12.dp)
    }
}

private fun highlightedName(name: String, range: IntRange?, accent: Color): AnnotatedString {
    if (range == null || range.isEmpty()) return AnnotatedString(name)
    return buildAnnotatedString {
        append(name)
        if (range.first in name.indices) {
            addStyle(
                SpanStyle(color = accent, fontWeight = FontWeight.Medium),
                range.first,
                (range.last + 1).coerceAtMost(name.length),
            )
        }
    }
}

@Composable
private fun ChevronGlyph(collapsed: Boolean, color: Color) {
    // База — шеврон «>» (смотрит на папку). Открытая папка — «v» (поворот +90°).
    val rotation by animateFloatAsState(if (collapsed) 0f else 90f, label = "navigator-chevron")
    Canvas(modifier = Modifier.size(12.dp).graphicsLayer { rotationZ = rotation }) {
        val path = Path().apply {
            moveTo(size.width * 0.25f, size.height * 0.2f)
            lineTo(size.width * 0.68f, size.height * 0.5f)
            lineTo(size.width * 0.25f, size.height * 0.8f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun FolderGlyph(color: Color, size: androidx.compose.ui.unit.Dp = 13.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.06f, h * 0.08f),
            size = Size(w * 0.5f, h * 0.34f),
            cornerRadius = CornerRadius(h * 0.12f, h * 0.12f),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, h * 0.28f),
            size = Size(w, h * 0.64f),
            cornerRadius = CornerRadius(h * 0.14f, h * 0.14f),
        )
    }
}

@Composable
private fun FolderPlusGlyph(color: Color, size: androidx.compose.ui.unit.Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.06f, h * 0.08f),
            size = Size(w * 0.44f, h * 0.3f),
            cornerRadius = CornerRadius(h * 0.11f, h * 0.11f),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(0f, h * 0.26f),
            size = Size(w * 0.78f, h * 0.58f),
            cornerRadius = CornerRadius(h * 0.13f, h * 0.13f),
        )
        val stroke = 1.2.dp.toPx()
        val plusX = w * 0.82f
        val plusY = h * 0.74f
        val arm = w * 0.16f
        drawLine(
            color = color,
            start = Offset(plusX - arm, plusY),
            end = Offset(plusX + arm, plusY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(plusX, plusY - arm),
            end = Offset(plusX, plusY + arm),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun PlusGlyph(color: Color, size: androidx.compose.ui.unit.Dp = 12.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val stroke = 1.3.dp.toPx()
        drawLine(
            color = color,
            start = Offset(this.size.width * 0.5f, this.size.height * 0.15f),
            end = Offset(this.size.width * 0.5f, this.size.height * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(this.size.width * 0.15f, this.size.height * 0.5f),
            end = Offset(this.size.width * 0.85f, this.size.height * 0.5f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun DotsGlyph(color: Color, size: androidx.compose.ui.unit.Dp = 12.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val radius = 1.1.dp.toPx()
        listOf(0.25f, 0.5f, 0.75f).forEach { fraction ->
            drawCircle(
                color = color,
                radius = radius,
                center = Offset(this.size.width * fraction, this.size.height * 0.5f),
            )
        }
    }
}

@Composable
private fun SearchGlyph(color: Color) {
    Canvas(modifier = Modifier.size(12.dp)) {
        val stroke = 1.2.dp.toPx()
        val radius = size.minDimension * 0.32f
        val center = Offset(size.width * 0.4f, size.height * 0.4f)
        drawCircle(color = color, radius = radius, center = center, style = Stroke(stroke))
        drawLine(
            color = color,
            start = Offset(center.x + radius * 0.7f, center.y + radius * 0.7f),
            end = Offset(size.width * 0.92f, size.height * 0.92f),
            strokeWidth = stroke,
        )
    }
}

@Composable
private fun LayersGlyph(color: Color, size: androidx.compose.ui.unit.Dp = 13.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        repeat(3) { index ->
            val centerY = h * (0.25f + index * 0.25f)
            val path = Path().apply {
                moveTo(w * 0.5f, centerY - h * 0.15f)
                lineTo(w * 0.95f, centerY)
                lineTo(w * 0.5f, centerY + h * 0.15f)
                lineTo(w * 0.05f, centerY)
                close()
            }
            drawPath(path = path, color = color)
        }
    }
}

@Composable
private fun MosaicGlyph(color: Color, size: androidx.compose.ui.unit.Dp = 12.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val radius = CornerRadius(w * 0.14f, w * 0.14f)
        drawRoundRect(color, Offset(w * 0.06f, h * 0.04f), Size(w * 0.36f, h * 0.5f), radius)
        drawRoundRect(color, Offset(w * 0.06f, h * 0.64f), Size(w * 0.36f, h * 0.32f), radius)
        drawRoundRect(color, Offset(w * 0.56f, h * 0.04f), Size(w * 0.38f, h * 0.32f), radius)
        drawRoundRect(color, Offset(w * 0.56f, h * 0.46f), Size(w * 0.38f, h * 0.5f), radius)
    }
}
