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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.aandios.nous.core.ui.component.TerminalContextPopup
import com.aandios.nous.core.ui.component.TerminalDropdownMenu
import com.aandios.nous.core.ui.component.TerminalMenuItem
import com.aandios.nous.core.ui.component.TerminalSearchField
import com.aandios.nous.core.ui.component.ToolPanelTitleBar
import com.aandios.nous.core.ui.component.highlightedMatch
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
    previewId: String? = null,
    onMakeTemplate: ((WorkspaceConfig) -> Unit)? = null,
    onDuplicateWorkspace: ((WorkspaceConfig) -> Unit)? = null,
) {
    val tokens = LocalTerminalDesignTokens.current
    val accent = MaterialTheme.colorScheme.primary
    val searchFocusRequester = remember { FocusRequester() }

    var query by remember { mutableStateOf("") }
    var newFolderName by remember { mutableStateOf<String?>(null) }
    var templatesExpanded by remember { mutableStateOf(false) }
    var emptyMenuExpanded by remember { mutableStateOf(false) }

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(tokens.colors.panelSurface)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.K &&
                    (event.isCtrlPressed || event.isMetaPressed)
                ) {
                    runCatching { searchFocusRequester.requestFocus() }
                    true
                } else {
                    false
                }
            },
    ) {
        ToolPanelTitleBar(
            title = "Workspaces",
            count = workspaces.size,
            leading = { MosaicGlyph(color = MaterialTheme.colorScheme.onSurfaceVariant, size = 13.dp) },
        ) {
            NavigatorActionButton(onClick = onNewWorkspace, description = "New workspace") {
                PlusGlyph(color = MaterialTheme.colorScheme.onSurfaceVariant, glyphSize = 15.dp)
            }
            Spacer(Modifier.width(tokens.spacing.xxs))
            NavigatorActionButton(onClick = { newFolderName = "" }, description = "New folder") {
                FolderPlusGlyph(color = MaterialTheme.colorScheme.onSurfaceVariant, size = 15.dp)
            }
        }

        TerminalSearchField(
            query = query,
            onQueryChange = { query = it },
            placeholder = "Search workspaces",
            focusRequester = searchFocusRequester,
            modifier = Modifier.padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xxs),
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

        newFolderName?.let { name ->
            NavigatorNewFolderRow(
                value = name,
                onValueChange = { newFolderName = it },
                onSubmit = { value ->
                    val trimmed = value.trim()
                    newFolderName = null
                    if (trimmed.isNotEmpty() && trimmed != NAVIGATOR_UNSORTED_LABEL) onCreateFolder(trimmed)
                },
                onCancel = { newFolderName = null },
                modifier = Modifier.padding(horizontal = tokens.spacing.sm, vertical = tokens.spacing.xs),
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press &&
                                event.buttons.isSecondaryPressed &&
                                event.changes.none { it.isConsumed }
                            ) {
                                emptyMenuExpanded = true
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                },
        ) {
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
                                isPreview = item.entry.workspace.id == previewId,
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

            NavigatorContextMenu(
                expanded = emptyMenuExpanded,
                onDismiss = { emptyMenuExpanded = false },
                title = "Workspaces",
            ) {
                NavigatorMenuItem("New Workspace", onClick = {
                    emptyMenuExpanded = false
                    onNewWorkspace()
                })
                NavigatorMenuItem("New Folder", onClick = {
                    emptyMenuExpanded = false
                    newFolderName = ""
                })
                NavigatorMenuDivider()
                NavigatorMenuItem("Expand All", onClick = {
                    emptyMenuExpanded = false
                    onCollapsedFoldersChange(emptySet())
                })
                NavigatorMenuItem("Collapse All", onClick = {
                    emptyMenuExpanded = false
                    onCollapsedFoldersChange(tree.folders.map { it.rawGroup }.toSet())
                })
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
            )
            Spacer(Modifier.width(tokens.spacing.xs))
            Text(
                text = templates.size.toString(),
                color = tokens.colors.label.copy(alpha = 0.6f),
                fontSize = tokens.typography.microSize,
                lineHeight = tokens.typography.compactLineHeight,
                fontFamily = TerminalFontFamily,
                modifier = Modifier.weight(1f),
            )
            ChevronGlyph(collapsed = !templatesExpanded, color = tokens.colors.label)
        }

        if (templatesExpanded) {
            if (templates.isEmpty()) {
                Text(
                    text = "No templates",
                    color = tokens.colors.label.copy(alpha = 0.6f),
                    fontSize = tokens.typography.smallSize,
                    lineHeight = tokens.typography.compactLineHeight,
                    fontFamily = TerminalFontFamily,
                    modifier = Modifier.padding(start = tokens.spacing.lg, end = tokens.spacing.md, bottom = tokens.spacing.xs),
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 180.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    templates.forEach { (label, config) ->
                        NavigatorTemplateRow(
                            label = label,
                            config = config,
                            onClick = { onCreateFromTemplate(label, config) },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(tokens.spacing.xs))
    }
}

@Composable
private fun NavigatorNewFolderRow(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTerminalDesignTokens.current
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .drawBehind {
                val stroke = tokens.dimensions.borderWidth.toPx()
                val radius = tokens.cornerRadii.smallCard.toPx()
                drawRoundRect(
                    color = accent.copy(alpha = 0.7f),
                    cornerRadius = CornerRadius(radius, radius),
                    style = Stroke(
                        width = stroke,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            }
            .padding(horizontal = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolderGlyph(color = MaterialTheme.colorScheme.onSurfaceVariant, size = 13.dp)
        Spacer(Modifier.width(tokens.spacing.sm))
        InlineRenameField(
            initial = value,
            onSubmit = onSubmit,
            onCancel = onCancel,
            modifier = Modifier.weight(1f),
            placeholder = "New folder",
            boxed = false,
        )
        Spacer(Modifier.width(tokens.spacing.sm))
        Text(
            text = "ENTER",
            color = tokens.colors.label,
            fontSize = tokens.typography.microSize,
            lineHeight = tokens.typography.compactLineHeight,
            letterSpacing = tokens.typography.compactLetterSpacing,
            fontFamily = TerminalFontFamily,
            maxLines = 1,
        )
    }
}

@Composable
private fun NavigatorTemplateRow(
    label: String,
    config: WorkspaceConfig,
    onClick: () -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = tokens.spacing.md)
            .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
            .clickable { onClick() }
            .padding(horizontal = tokens.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(NAVIGATOR_WORKSPACE_ICON_WIDTH),
            contentAlignment = Alignment.Center,
        ) {
            NavigatorMiniMapGlyph(
                config = config,
                modifier = Modifier.size(14.dp),
            )
        }
        Spacer(Modifier.width(tokens.spacing.xs))
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
                .height(28.dp)
                .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
                .background(
                    when {
                        dropHighlight -> accent.copy(alpha = 0.16f)
                        hovered -> tokens.colors.panelHeaderSurface
                        item.containsActive -> accent.copy(alpha = 0.08f)
                        else -> Color.Transparent
                    },
                )
                .hoverable(interaction)
                .clickable(interactionSource = interaction, indication = null) {
                    if (!editing) onToggle()
                }
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                menuExpanded = true
                                event.changes.forEach { it.consume() }
                            }
                        }
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
                .padding(start = tokens.spacing.md, end = tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChevronGlyph(
                collapsed = collapsed,
                color = if (item.containsActive) accent else tokens.colors.label,
            )
            Spacer(Modifier.width(tokens.spacing.xs))
            FolderGlyph(
                color = if (item.containsActive) accent else tokens.colors.label,
                size = 16.dp,
                open = !collapsed,
            )
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
                    text = item.folder.name.uppercase(),
                    color = tokens.colors.label,
                    fontSize = tokens.typography.smallSize,
                    lineHeight = tokens.typography.compactLineHeight,
                    letterSpacing = tokens.typography.compactLetterSpacing,
                    fontWeight = FontWeight.Medium,
                    fontFamily = TerminalFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(tokens.spacing.xs))
                Text(
                    text = item.folder.workspaces.size.toString(),
                    color = tokens.colors.label.copy(alpha = 0.6f),
                    fontSize = tokens.typography.smallSize,
                    lineHeight = tokens.typography.compactLineHeight,
                    fontFamily = TerminalFontFamily,
                )
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
        NavigatorContextMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            title = item.folder.name,
        ) {
            NavigatorMenuItem("Rename", onClick = {
                menuExpanded = false
                editing = true
            }, icon = { PencilGlyph(color = tokens.colors.label, glyphSize = 12.dp) })
            NavigatorMenuDivider()
            NavigatorMenuItem("Delete", onClick = {
                menuExpanded = false
                confirmDelete = true
            }, icon = { TrashGlyph(color = MaterialTheme.colorScheme.error, glyphSize = 12.dp) })
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

/** Workspace-строка навигатора: неактивная односрочная, активная двухсрочная с мини-картой. */
private val NAVIGATOR_WORKSPACE_ROW_HEIGHT = 32.dp
private val NAVIGATOR_WORKSPACE_ACTIVE_ROW_HEIGHT = 48.dp
private val NAVIGATOR_WORKSPACE_ICON_WIDTH = 24.dp
private val NAVIGATOR_WORKSPACE_GLYPH_SIZE = 16.dp

/**
 * X гайд-линии вложенности: центр зазора между краем панели и глифом ребёнка
 * (зазор слева = зазор справа до глифа).
 */
private val NAVIGATOR_TREE_GUIDE_X = 10.5.dp
private val NAVIGATOR_TREE_GUIDE_WIDTH = 1.dp

/** Отступ контента ребёнка папки от края панели до колонки иконки. */
private val NAVIGATOR_CHILD_INDENT = 18.dp

/** Окно распознавания двойного клика (мс): одиночный срабатывает мгновенно. */
private const val NAVIGATOR_DOUBLE_TAP_WINDOW_MS = 400L

@Composable
private fun NavigatorWorkspaceRow(
    item: NavigatorItem.WorkspaceItem,
    isActive: Boolean,
    isPreview: Boolean,
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
    val exchange = navigatorExchangeLabel(ws)
    val symbol = navigatorPanelSymbol(ws)
    val rowHeight = if (isActive) NAVIGATOR_WORKSPACE_ACTIVE_ROW_HEIGHT else NAVIGATOR_WORKSPACE_ROW_HEIGHT

    Box(modifier = modifier.fillMaxWidth().graphicsLayer { alpha = if (isDragged) 0.4f else 1f }) {
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth()
                .height(rowHeight)
                .padding(start = NAVIGATOR_TREE_GUIDE_X)
                .clip(RoundedCornerShape(topEnd = tokens.cornerRadii.smallCard, bottomEnd = tokens.cornerRadii.smallCard))
                .background(
                    when {
                        isActive -> tokens.colors.selection
                        hovered -> tokens.colors.panelHeaderSurface
                        else -> Color.Transparent
                    },
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(rowHeight)
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
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                                menuExpanded = true
                                event.changes.forEach { it.consume() }
                            }
                        }
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
                .padding(end = tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(NAVIGATOR_CHILD_INDENT))
            Box(
                modifier = Modifier
                    .width(NAVIGATOR_WORKSPACE_ICON_WIDTH)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center,
            ) {
                NavigatorMiniMapGlyph(
                    config = ws,
                    active = isActive,
                    preview = isPreview && !isActive,
                    modifier = Modifier.size(NAVIGATOR_WORKSPACE_GLYPH_SIZE),
                )
            }
            Spacer(Modifier.width(tokens.spacing.xs))
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
            } else if (isActive) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = highlightedMatch(ws.name, item.entry.highlight, accent),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = tokens.typography.controlSize,
                            lineHeight = tokens.typography.controlLineHeight,
                            fontFamily = TerminalFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        if (exchange.isNotEmpty()) {
                            Spacer(Modifier.width(tokens.spacing.xs))
                            Text(
                                text = exchange.uppercase(),
                                color = tokens.colors.label,
                                fontSize = tokens.typography.microSize,
                                lineHeight = tokens.typography.compactLineHeight,
                                letterSpacing = tokens.typography.compactLetterSpacing,
                                fontFamily = TerminalFontFamily,
                                maxLines = 1,
                            )
                        }
                    }
                    if (symbol.isNotEmpty()) {
                        Text(
                            text = symbol,
                            color = tokens.colors.label,
                            fontSize = tokens.typography.compactSize,
                            lineHeight = tokens.typography.controlLineHeight,
                            fontFamily = TerminalFontFamily,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            } else {
                Text(
                    text = highlightedMatch(ws.name, item.entry.highlight, accent),
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                    fontSize = tokens.typography.controlSize,
                    lineHeight = tokens.typography.controlLineHeight,
                    fontStyle = if (isPreview) FontStyle.Italic else FontStyle.Normal,
                    fontFamily = TerminalFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (exchange.isNotEmpty()) {
                    Spacer(Modifier.width(tokens.spacing.xs))
                    Text(
                        text = exchange.uppercase(),
                        color = tokens.colors.label,
                        fontSize = tokens.typography.microSize,
                        lineHeight = tokens.typography.compactLineHeight,
                        letterSpacing = tokens.typography.compactLetterSpacing,
                        fontFamily = TerminalFontFamily,
                        maxLines = 1,
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .offset(x = NAVIGATOR_TREE_GUIDE_X)
                .width(NAVIGATOR_TREE_GUIDE_WIDTH)
                .height(rowHeight)
                .background(tokens.colors.label.copy(alpha = 0.35f)),
        )
        if (isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(x = NAVIGATOR_TREE_GUIDE_X)
                    .width(2.dp)
                    .height(rowHeight)
                    .background(accent),
            )
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
        NavigatorContextMenu(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            title = ws.name,
        ) {
            NavigatorMenuItem("Rename", onClick = {
                menuExpanded = false
                editing = true
            }, icon = { PencilGlyph(color = tokens.colors.label, glyphSize = 12.dp) })
            onDuplicate?.let { fn ->
                NavigatorMenuItem("Duplicate", onClick = {
                    menuExpanded = false
                    fn()
                }, icon = { CopyGlyph(color = tokens.colors.label, glyphSize = 12.dp) })
            }
            onMakeTemplate?.let { fn ->
                NavigatorMenuItem("Make Template", onClick = {
                    menuExpanded = false
                    fn()
                }, icon = { LayersGlyph(color = tokens.colors.label, size = 12.dp) })
            }
            NavigatorMenuDivider()
            NavigatorMenuItem("Delete", onClick = {
                menuExpanded = false
                confirmDelete = true
            }, icon = { TrashGlyph(color = MaterialTheme.colorScheme.error, glyphSize = 12.dp) })
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
}

@Composable
private fun InlineRenameField(
    initial: String,
    onSubmit: (String) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    boxed: Boolean = true,
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
            .then(
                if (boxed) {
                    Modifier
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
                } else {
                    Modifier
                },
            )
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
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty() && placeholder.isNotEmpty()) {
                    Text(
                        text = placeholder,
                        color = tokens.colors.label,
                        fontSize = tokens.typography.compactSize,
                        lineHeight = tokens.typography.controlLineHeight,
                        fontFamily = TerminalFontFamily,
                        maxLines = 1,
                    )
                }
                inner()
            }
        },
    )
}

@Composable
private fun NavigatorContextMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    title: String? = null,
    content: @Composable () -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    TerminalDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
    ) {
        Column(modifier = Modifier.widthIn(min = 180.dp).padding(vertical = tokens.spacing.xs)) {
            title?.let {
                Text(
                    text = it.uppercase(),
                    color = tokens.colors.label,
                    fontSize = tokens.typography.microSize,
                    lineHeight = tokens.typography.compactLineHeight,
                    letterSpacing = tokens.typography.compactLetterSpacing,
                    fontWeight = FontWeight.Medium,
                    fontFamily = TerminalFontFamily,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xxs),
                )
                NavigatorMenuDivider()
            }
            content()
        }
    }
}

@Composable
private fun NavigatorMenuDivider() {
    val tokens = LocalTerminalDesignTokens.current
    HorizontalDivider(
        color = tokens.colors.panelBorder,
        thickness = tokens.dimensions.borderWidth,
    )
}

@Composable
private fun NavigatorMenuItem(
    label: String,
    destructive: Boolean = false,
    icon: (@Composable () -> Unit)? = null,
    onClick: () -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    TerminalMenuItem(onClick = onClick) {
        icon?.let {
            it()
            Spacer(Modifier.width(tokens.spacing.sm))
        }
        Text(
            text = label,
            color = if (destructive) MaterialTheme.colorScheme.error
            else MaterialTheme.colorScheme.onSurface,
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

/**
 * Контур папки в сетке 24×24 (как в иконочных наборах): закрытая — тело с
 * ушком, открытая — задняя стенка с ушком и передний карман со скосом.
 */
private fun navigatorFolderPath(open: Boolean, ox: Float, oy: Float, scale: Float): Path {
    fun x(v: Float) = ox + v * scale
    fun y(v: Float) = oy + v * scale
    return Path().apply {
        if (!open) {
            moveTo(x(20f), y(20f))
            quadraticTo(x(22f), y(20f), x(22f), y(18f))
            lineTo(x(22f), y(8f))
            quadraticTo(x(22f), y(6f), x(20f), y(6f))
            lineTo(x(12.1f), y(6f))
            quadraticTo(x(11f), y(6.05f), x(10.41f), y(5.1f))
            lineTo(x(9.6f), y(3.9f))
            quadraticTo(x(9f), y(3.1f), x(7.93f), y(3f))
            lineTo(x(4f), y(3f))
            quadraticTo(x(2f), y(3f), x(2f), y(5f))
            lineTo(x(2f), y(18f))
            quadraticTo(x(2f), y(20f), x(4f), y(20f))
            close()
        } else {
            moveTo(x(6f), y(14f))
            lineTo(x(7.45f), y(11.1f))
            quadraticTo(x(8.1f), y(10f), x(9.24f), y(10f))
            lineTo(x(20f), y(10f))
            quadraticTo(x(21.9f), y(10.3f), x(21.94f), y(12.5f))
            lineTo(x(20.39f), y(18.5f))
            quadraticTo(x(20.2f), y(20f), x(18.45f), y(20f))
            lineTo(x(4f), y(20f))
            quadraticTo(x(2f), y(20f), x(2f), y(18f))
            lineTo(x(2f), y(5f))
            quadraticTo(x(2f), y(3f), x(4f), y(3f))
            lineTo(x(7.93f), y(3f))
            quadraticTo(x(8.7f), y(3.05f), x(9.59f), y(3.9f))
            lineTo(x(10.41f), y(5.1f))
            quadraticTo(x(11.1f), y(6f), x(12.07f), y(6f))
            lineTo(x(18f), y(6f))
            quadraticTo(x(20f), y(6f), x(20f), y(8f))
            lineTo(x(20f), y(10f))
        }
    }
}

@Composable
private fun FolderGlyph(color: Color, size: androidx.compose.ui.unit.Dp = 13.dp, open: Boolean = false) {
    Canvas(modifier = Modifier.size(size)) {
        val frame = this.size
        val scale = frame.minDimension / 24f
        val ox = (frame.width - 24f * scale) / 2f
        val oy = (frame.height - 24f * scale) / 2f
        drawPath(
            path = navigatorFolderPath(open, ox, oy, scale),
            color = color,
            style = Stroke(
                width = (1.9f * scale).coerceAtLeast(1f),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}

@Composable
private fun FolderPlusGlyph(color: Color, size: androidx.compose.ui.unit.Dp = 14.dp) {
    Canvas(modifier = Modifier.size(size)) {
        val frame = this.size
        val scale = frame.minDimension / 24f
        val stroke = (1.9f * scale).coerceAtLeast(1f)
        drawPath(
            path = navigatorFolderPath(false, 0f, 0f, scale),
            color = color,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        val plusX = frame.width * 0.5f
        val plusY = frame.height * (13.5f / 24f)
        val arm = frame.minDimension * (3.4f / 24f)
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
fun PlusGlyph(color: Color, glyphSize: androidx.compose.ui.unit.Dp = 14.dp) {
    Canvas(modifier = Modifier.size(glyphSize)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.1.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.06f, h * 0.06f),
            size = Size(w * 0.88f, h * 0.88f),
            cornerRadius = CornerRadius(w * 0.18f, w * 0.18f),
            style = Stroke(stroke),
        )
        val plusX = w * 0.5f
        val plusY = h * 0.5f
        val arm = w * 0.18f
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
private fun PencilGlyph(color: Color, glyphSize: androidx.compose.ui.unit.Dp = 12.dp) {
    Canvas(modifier = Modifier.size(glyphSize)) {
        val stroke = 1.1.dp.toPx()
        drawLine(
            color = color,
            start = Offset(this.size.width * 0.18f, this.size.height * 0.82f),
            end = Offset(this.size.width * 0.78f, this.size.height * 0.22f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(this.size.width * 0.62f, this.size.height * 0.38f),
            end = Offset(this.size.width * 0.86f, this.size.height * 0.62f),
            strokeWidth = stroke * 2f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun CopyGlyph(color: Color, glyphSize: androidx.compose.ui.unit.Dp = 12.dp) {
    Canvas(modifier = Modifier.size(glyphSize)) {
        val stroke = 1.1.dp.toPx()
        val w = this.size.width
        val h = this.size.height
        val corner = CornerRadius(w * 0.16f, w * 0.16f)
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.28f, h * 0.06f),
            size = Size(w * 0.66f, h * 0.66f),
            cornerRadius = corner,
            style = Stroke(stroke),
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.06f, h * 0.28f),
            size = Size(w * 0.66f, h * 0.66f),
            cornerRadius = corner,
            style = Stroke(stroke),
        )
    }
}

@Composable
private fun TrashGlyph(color: Color, glyphSize: androidx.compose.ui.unit.Dp = 12.dp) {
    Canvas(modifier = Modifier.size(glyphSize)) {
        val stroke = 1.1.dp.toPx()
        val w = this.size.width
        val h = this.size.height
        drawLine(
            color = color,
            start = Offset(w * 0.12f, h * 0.2f),
            end = Offset(w * 0.88f, h * 0.2f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.38f, h * 0.2f),
            end = Offset(w * 0.42f, h * 0.06f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.62f, h * 0.2f),
            end = Offset(w * 0.58f, h * 0.06f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.2f, h * 0.28f),
            size = Size(w * 0.6f, h * 0.62f),
            cornerRadius = CornerRadius(w * 0.1f, w * 0.1f),
            style = Stroke(stroke),
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
        val stroke = 1.1.dp.toPx()
        drawRoundRect(
            color = color,
            topLeft = Offset(w * 0.06f, h * 0.06f),
            size = Size(w * 0.88f, h * 0.88f),
            cornerRadius = CornerRadius(w * 0.16f, w * 0.16f),
            style = Stroke(stroke),
        )
        drawLine(
            color = color,
            start = Offset(w * 0.5f, h * 0.06f),
            end = Offset(w * 0.5f, h * 0.94f),
            strokeWidth = stroke,
        )
        drawLine(
            color = color,
            start = Offset(w * 0.06f, h * 0.5f),
            end = Offset(w * 0.94f, h * 0.5f),
            strokeWidth = stroke,
        )
    }
}
