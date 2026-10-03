/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.aandios.nous.core.workspace.LayoutNode
import com.aandios.nous.core.workspace.PanelState
import com.aandios.nous.core.workspace.PanelType
import com.aandios.nous.core.workspace.Templates
import com.aandios.nous.core.workspace.WorkspaceConfig

/**
 * Экран-заставка терминала (показывается, когда нет открытых workspace):
 * Quick Start → шаблоны (встроенные + пользовательские) → недавние workspace.
 * Превью карточек рисуется из фактической конфигурации панелей.
 * Карточки встроенных/пользовательских шаблонов и недавних workspace'ов
 * можно удалять (кнопка-корзина видна только при наведении).
 * У пользовательских шаблонов есть меню ⋮: Rename / Duplicate / Change (описание).
 */
@Composable
fun WelcomeScreen(
    recentWorkspaces: List<WorkspaceConfig>,
    userTemplates: List<WorkspaceConfig>,
    hiddenBuiltinTemplates: Set<String> = emptySet(),
    onSelectTemplate: (WorkspaceConfig) -> Unit,
    onOpenRecent: (WorkspaceConfig) -> Unit,
    onDeleteTemplate: (WorkspaceConfig) -> Unit,
    onHideBuiltinTemplate: (String) -> Unit = {},
    onDeleteRecent: (WorkspaceConfig) -> Unit = {},
    onRenameTemplate: (WorkspaceConfig, String) -> Unit = { _, _ -> },
    onDuplicateTemplate: (WorkspaceConfig) -> Unit = {},
    onChangeTemplateDescription: (WorkspaceConfig, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val builtinTemplates = remember {
        val gridSymbols = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT", "BNBUSDT", "XRPUSDT", "DOGEUSDT",
            "ADAUSDT", "LINKUSDT", "AVAXUSDT", "DOTUSDT", "TRXUSDT", "LTCUSDT")
        listOf(
            BuiltinCard("builtin-scalping", "Scalping", Templates.scalping()) { Templates.scalping() },
            BuiltinCard("builtin-classic", "Classic", Templates.classic()) { Templates.classic() },
            BuiltinCard("builtin-orderflow", "Order Flow", Templates.orderFlow()) { Templates.orderFlow() },
            BuiltinCard("builtin-multichart", "Multi-Chart", Templates.multiChart()) { Templates.multiChart() },
            BuiltinCard("builtin-domgrid", "DOM Grid", Templates.domGrid(gridSymbols)) { Templates.domGrid(gridSymbols) },
            BuiltinCard("builtin-empty", "Empty", Templates.empty()) { Templates.empty() },
        )
    }
    val visibleBuiltins = remember(builtinTemplates, hiddenBuiltinTemplates) {
        builtinTemplates.filter { it.id !in hiddenBuiltinTemplates }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .verticalScroll(rememberScrollState())
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo
        Text("[Nous Platform]", color = Color(0xFF00C853), fontSize = 24.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("v0.1 — Professional Crypto Trading Terminal", color = Color(0xFF666666), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(32.dp))

        // ── Quick Start (в самом верху) ────────────────────────────────────
        SectionTitle("QUICK START")
        Box(
            modifier = Modifier.width(900.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF121212)).padding(16.dp)
        ) {
            Column {
                listOf(
                    "Drag — pan the chart        Ctrl + wheel — zoom at cursor",
                    "Double-click — reset zoom    Alt + hover (footprint) — level popup",
                    "Alt + drag — vertical scroll Ctrl+Z / Ctrl+Y — undo / redo drawings",
                    "Drag a panel border — split panel into two",
                    "Left toolbar — workspaces, symbols, portfolio, console, editor"
                ).forEach {
                    Text(it, color = Color(0xFFAAAAAA), fontSize = 12.sp, fontFamily = FontFamily.Monospace, lineHeight = 20.sp)
                }
            }
        }

        // ── Templates (встроенные + пользовательские, единый раздел) ───────
        if (visibleBuiltins.isNotEmpty() || userTemplates.isNotEmpty()) {
            Spacer(Modifier.height(36.dp))
            SectionTitle("TEMPLATES")
            val entries = buildList {
                visibleBuiltins.forEach { card ->
                    add(
                        TemplateEntry(
                            title = card.title,
                            config = card.preview,
                            onClick = { onSelectTemplate(card.build()) },
                            onDelete = { onHideBuiltinTemplate(card.id) },
                        )
                    )
                }
                userTemplates.forEach { template ->
                    add(
                        TemplateEntry(
                            title = template.name,
                            config = template,
                            onClick = { onSelectTemplate(template) },
                            onDelete = { onDeleteTemplate(template) },
                            onRename = { newName -> onRenameTemplate(template, newName) },
                            onDuplicate = { onDuplicateTemplate(template) },
                            onChangeDescription = { desc -> onChangeTemplateDescription(template, desc) },
                        )
                    )
                }
            }
            entries.chunked(6).forEach { rowEntries ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    rowEntries.forEach { entry ->
                        WorkspaceCard(
                            title = entry.title,
                            config = entry.config,
                            onClick = entry.onClick,
                            onDelete = entry.onDelete,
                            onRename = entry.onRename,
                            onDuplicate = entry.onDuplicate,
                            onChangeDescription = entry.onChangeDescription,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        // ── Recent workspaces ──────────────────────────────────────────────
        if (recentWorkspaces.isNotEmpty()) {
            Spacer(Modifier.height(36.dp))
            SectionTitle("RECENT WORKSPACES")
            recentWorkspaces
                .sortedByDescending { it.updatedAt }
                .take(8)
                .chunked(4)
                .forEach { rowConfigs ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        rowConfigs.forEach { config ->
                            WorkspaceCard(
                                title = config.name,
                                config = config,
                                onClick = { onOpenRecent(config) },
                                onDelete = { onDeleteRecent(config) },
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color(0xFF888888), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
}

private data class BuiltinCard(
    val id: String,
    val title: String,
    val preview: WorkspaceConfig,
    val build: () -> WorkspaceConfig,
)

private data class TemplateEntry(
    val title: String,
    val config: WorkspaceConfig,
    val onClick: () -> Unit,
    val onDelete: () -> Unit,
    val onRename: ((String) -> Unit)? = null,
    val onDuplicate: (() -> Unit)? = null,
    val onChangeDescription: ((String) -> Unit)? = null,
)

private enum class CardDialogMode { RENAME, DESCRIPTION }

@Composable
private fun WorkspaceCard(
    title: String,
    config: WorkspaceConfig,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null,
    onRename: ((String) -> Unit)? = null,
    onDuplicate: (() -> Unit)? = null,
    onChangeDescription: ((String) -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var dialogMode by remember { mutableStateOf<CardDialogMode?>(null) }
    var dialogText by remember { mutableStateOf("") }

    val hasMenu = onRename != null || onDuplicate != null || onChangeDescription != null

    Box(
        modifier = Modifier
            .width(180.dp)
            .hoverable(interaction)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF121212))
                .clickable(onClick = onClick)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            LayoutPreview(config = config, modifier = Modifier.width(156.dp).height(90.dp))
            Spacer(Modifier.height(10.dp))
            Text(title, color = Color(0xFFE0E0E0), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold, maxLines = 1)
            Spacer(Modifier.height(4.dp))
            Text(config.description.ifBlank { descOf(config) }, color = Color(0xFF888888), fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 15.sp, maxLines = 2)
            Spacer(Modifier.height(4.dp))
            Text(infoOf(config), color = Color(0xFF5B9BD5), fontSize = 10.sp, fontFamily = FontFamily.Monospace, maxLines = 1)
        }
        if (hovered || menuExpanded) {
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (hasMenu) {
                    Box {
                        CardIconButton("\u22EE") { menuExpanded = true }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            onRename?.let { fn ->
                                DropdownMenuItem(
                                    text = { Text("Rename", fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                                    onClick = {
                                        menuExpanded = false
                                        dialogText = title
                                        dialogMode = CardDialogMode.RENAME
                                    }
                                )
                            }
                            onDuplicate?.let { fn ->
                                DropdownMenuItem(
                                    text = { Text("Duplicate", fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                                    onClick = {
                                        menuExpanded = false
                                        fn()
                                    }
                                )
                            }
                            onChangeDescription?.let { fn ->
                                DropdownMenuItem(
                                    text = { Text("Change", fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
                                    onClick = {
                                        menuExpanded = false
                                        dialogText = config.description
                                        dialogMode = CardDialogMode.DESCRIPTION
                                    }
                                )
                            }
                        }
                    }
                }
                if (onDelete != null) {
                    TrashIconButton(onClick = { onDelete() })
                }
            }
        }
    }

    val mode = dialogMode
    if (mode != null) {
        CardDialog(
            title = if (mode == CardDialogMode.RENAME) "Rename template" else "Description",
            initialText = dialogText,
            onDismiss = { dialogMode = null },
            onConfirm = { text ->
                dialogMode = null
                when (mode) {
                    CardDialogMode.RENAME -> onRename?.invoke(text)
                    CardDialogMode.DESCRIPTION -> onChangeDescription?.invoke(text)
                }
            }
        )
    }
}

/**
 * Небольшая кнопка-иконка в углу карточки (три точки), как у корзины.
 */
@Composable
private fun CardIconButton(icon: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF1E1E1E))
            .clickable(onClick = onClick)
            .size(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(icon, color = Color(0xFFB0B0B0), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
    }
}

/**
 * Модальный диалог ввода (переименование / описание шаблона).
 */
@Composable
private fun CardDialog(
    title: String,
    initialText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initialText) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF121212))
                .padding(16.dp)
        ) {
            Text(title, color = Color(0xFFE0E0E0), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            BasicTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                textStyle = TextStyle(color = Color(0xFFE0E0E0), fontSize = 13.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Color(0xFF00C853)),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = Color(0xFF888888), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
                Spacer(Modifier.width(8.dp))
                TextButton(onClick = { onConfirm(text) }) {
                    Text("Save", color = Color(0xFF00C853), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/**
 * Кнопка удаления с иконкой корзины (видна только при наведении на карточку).
 */
@Composable
private fun TrashIconButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF1E1E1E))
            .clickable(onClick = onClick)
            .size(20.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(12.dp)) {
            val w = size.width
            val h = size.height
            val stroke = Color(0xFFB0B0B0)
            // Крышка и ручка
            drawLine(stroke, Offset(w * 0.1f, h * 0.3f), Offset(w * 0.9f, h * 0.3f), strokeWidth = 1.2f)
            drawLine(stroke, Offset(w * 0.4f, h * 0.3f), Offset(w * 0.4f, h * 0.12f), strokeWidth = 1.2f)
            drawLine(stroke, Offset(w * 0.6f, h * 0.3f), Offset(w * 0.6f, h * 0.12f), strokeWidth = 1.2f)
            drawLine(stroke, Offset(w * 0.4f, h * 0.12f), Offset(w * 0.6f, h * 0.12f), strokeWidth = 1.2f)
            // Корпус
            drawRect(
                color = stroke,
                topLeft = Offset(w * 0.2f, h * 0.34f),
                size = Size(w * 0.6f, h * 0.52f),
                style = Stroke(width = 1.2f)
            )
            // Полосы
            drawLine(stroke, Offset(w * 0.3f, h * 0.48f), Offset(w * 0.7f, h * 0.48f), strokeWidth = 1f)
            drawLine(stroke, Offset(w * 0.3f, h * 0.62f), Offset(w * 0.7f, h * 0.62f), strokeWidth = 1f)
            drawLine(stroke, Offset(w * 0.3f, h * 0.76f), Offset(w * 0.7f, h * 0.76f), strokeWidth = 1f)
        }
    }
}

private fun descOf(config: WorkspaceConfig): String {
    val counts = config.panels.groupingBy { it.type }.eachCount()
    return listOfNotNull(
        counts[PanelType.CHART]?.let { if (it > 1) "$it Charts" else "Chart" },
        counts[PanelType.DOM]?.let { if (it > 1) "$it DOM" else "DOM" },
        counts[PanelType.TRADES]?.let { if (it > 1) "$it Trades" else "Trades" },
    ).joinToString(" + ")
}

private fun infoOf(config: WorkspaceConfig): String {
    val symbols = config.panels.map { it.symbol }.distinct().joinToString("/").ifEmpty { return "" }
    val chartPanel = config.panels.firstOrNull { it.type == PanelType.CHART }
    val tf = (chartPanel?.state as? PanelState.Chart)?.timeframe
    return if (tf != null) "$symbols · $tf" else symbols
}

/**
 * Мини-превью раскладки workspace: прямоугольники-панели повторяют дерево
 * сплитов [WorkspaceConfig.layout], а подписи берутся из фактических
 * настроек панелей (таймфрейм графика, глубина DOM, символ).
 */
@Composable
private fun LayoutPreview(config: WorkspaceConfig, modifier: Modifier = Modifier) {
    val panels = remember(config.panels) { config.panels.associateBy { it.id } }
    val textMeasurer = rememberTextMeasurer()

    val tfStyle = TextStyle(color = Color(0xFF9EDBC0), fontSize = 9.sp, fontFamily = FontFamily.Monospace)
    val labelStyle = TextStyle(color = Color(0xFF9AA7B4), fontSize = 8.sp, fontFamily = FontFamily.Monospace)
    val smallStyle = TextStyle(color = Color(0xFF6FA58A), fontSize = 7.sp, fontFamily = FontFamily.Monospace)

    Canvas(modifier = modifier) {
        drawRect(color = Color(0xFF0D0D0D), size = size)
        val gap = 3.dp.toPx()
        collectRects(config.layout, 0f, 0f, size.width, size.height, gap).forEach { (rect, panelId) ->
            val pc = panels[panelId]
            val type = pc?.type
            val fill = when (type) {
                PanelType.CHART -> Color(0xFF14302A)
                PanelType.DOM -> Color(0xFF14202E)
                PanelType.TRADES -> Color(0xFF2A2414)
                null -> Color(0xFF1A1A1A)
            }
            val border = when (type) {
                PanelType.CHART -> Color(0xFF2E7D57)
                PanelType.DOM -> Color(0xFF2E5F7D)
                PanelType.TRADES -> Color(0xFF7D6B2E)
                null -> Color(0xFF444444)
            }
            drawRect(color = fill, topLeft = Offset(rect.left, rect.top), size = Size(rect.width, rect.height))
            drawRect(color = border, topLeft = Offset(rect.left, rect.top), size = Size(rect.width, rect.height), style = Stroke(width = 1.dp.toPx()))

            if (rect.width < 24f || rect.height < 14f) return@forEach

            when (type) {
                PanelType.CHART -> {
                    (pc?.state as? PanelState.Chart)?.timeframe?.let { tf ->
                        val layout = textMeasurer.measure(AnnotatedString(tf), tfStyle)
                        drawText(
                            layout,
                            topLeft = Offset(
                                rect.left + rect.width / 2 - layout.size.width / 2,
                                rect.top + rect.height / 2 - layout.size.height / 2
                            )
                        )
                    }
                    pc?.symbol?.let { sym ->
                        val layout = textMeasurer.measure(AnnotatedString(sym), smallStyle)
                        drawText(layout, topLeft = Offset(rect.left + 3f, rect.top + 2f))
                    }
                }
                PanelType.DOM -> {
                    (pc?.state as? PanelState.Dom)?.depth?.let { depth ->
                        val layout = textMeasurer.measure(AnnotatedString("d$depth"), labelStyle)
                        drawText(
                            layout,
                            topLeft = Offset(
                                rect.left + rect.width / 2 - layout.size.width / 2,
                                rect.top + rect.height / 2 - layout.size.height / 2
                            )
                        )
                    }
                }
                PanelType.TRADES -> {
                    pc?.symbol?.let { sym ->
                        val layout = textMeasurer.measure(AnnotatedString(sym), labelStyle)
                        drawText(
                            layout,
                            topLeft = Offset(
                                rect.left + rect.width / 2 - layout.size.width / 2,
                                rect.top + rect.height / 2 - layout.size.height / 2
                            )
                        )
                    }
                }
                null -> Unit
            }
        }
    }
}

private fun collectRects(
    node: LayoutNode,
    x: Float,
    y: Float,
    w: Float,
    h: Float,
    gap: Float,
): List<Pair<Rect, String>> {
    return when (node) {
        is LayoutNode.Leaf -> listOf(Rect(x, y, x + w, y + h) to node.panelId)
        is LayoutNode.Split -> {
            val children = node.children
            if (children.isEmpty()) return emptyList()
            val result = mutableListOf<Pair<Rect, String>>()
            val horizontal = node.direction == LayoutNode.Direction.HORIZONTAL
            val totalSpan = if (horizontal) w else h
            val usable = (totalSpan - gap * (children.size - 1)).coerceAtLeast(0f)
            var offset = 0f
            children.forEachIndexed { i, child ->
                val span = if (children.size == 2 && i == 0) usable * node.ratio else usable / children.size
                val childRect = if (horizontal) {
                    collectRects(child, x + offset, y, span, h, gap)
                } else {
                    collectRects(child, x, y + offset, w, span, gap)
                }
                result += childRect
                offset += span + gap
            }
            result
        }
    }
}
