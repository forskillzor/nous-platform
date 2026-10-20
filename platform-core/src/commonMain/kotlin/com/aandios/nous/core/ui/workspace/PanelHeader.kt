/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.workspace

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.component.TerminalDropdownMenu
import com.aandios.nous.core.ui.component.TerminalGripDots
import com.aandios.nous.core.ui.component.TerminalGripOrientation
import com.aandios.nous.core.ui.component.TerminalMenuItem
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import com.aandios.nous.core.workspace.PanelConfig
import com.aandios.nous.core.workspace.PanelState
import com.aandios.nous.core.workspace.PanelType

/**
 * Обработчики перетаскивания панели за заголовок.
 */
class PanelDragHandlers(
    val onDragStart: () -> Unit,
    val onDrag: (Offset) -> Unit,
    val onDragEnd: () -> Unit,
    val onDragCancel: () -> Unit = onDragEnd,
)

private val splitableTypes = listOf(
    PanelType.CHART to "Chart",
    PanelType.DOM to "DOM",
    PanelType.TRADES to "Trades",
    PanelType.TRADING to "Trading",
)

/**
 * Верхний тайтл панели: грип перетаскивания, панель-специфичные контролы
 * ([titleContent] — дропдауны/кнопки), затем справа настройки (⚙), добавление
 * панели split'ом (+) и крупный крестик закрытия.
 */
@Composable
fun PanelHeader(
    config: PanelConfig,
    onClose: (() -> Unit),
    onSplitH: ((PanelType) -> Unit)? = null,
    onSplitV: ((PanelType) -> Unit)? = null,
    onDrag: PanelDragHandlers? = null,
    modifier: Modifier = Modifier,
    titleContent: (@Composable () -> Unit),
    settingsContent: (@Composable (dismiss: () -> Unit) -> Unit),
) {
    val tokens = LocalTerminalDesignTokens.current
    var addMenuExpanded by remember { mutableStateOf(false) }
    var settingsMenuExpanded by remember { mutableStateOf(false) }
    var headerOrigin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    // Порог, после которого «нажатие» превращается в перетаскивание
    // (сплит срабатывает только когда панель реально тащат, а не при клике)
    val dragThresholdPx = with(density) { tokens.spacing.xl.toPx() }
    // Стабильная ссылка на обработчики: dragState обновляется на каждое движение
    // (рекомпозиция), из-за чего pointerInput с ключом onDrag обрывал бы жест
    val currentOnDrag by rememberUpdatedState(onDrag)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(tokens.dimensions.workspacePanelHeaderHeight)
                .background(tokens.colors.panelSurface)
                .onGloballyPositioned { headerOrigin = it.positionInWindow() }
                .then(
                    if (onDrag != null) {
                        Modifier.pointerInput(dragThresholdPx) {
                            var totalDrag = 0f
                            var dragActive = false
                            detectDragGestures(
                                onDragStart = {
                                    totalDrag = 0f
                                    dragActive = false
                                },
                                onDrag = { change, dragAmount ->
                                    totalDrag += dragAmount.getDistance()
                                    val handler = currentOnDrag ?: return@detectDragGestures
                                    if (!dragActive && totalDrag >= dragThresholdPx) {
                                        dragActive = true
                                        handler.onDragStart()
                                    }
                                    if (dragActive) {
                                        handler.onDrag(headerOrigin + change.position)
                                    }
                                },
                                onDragEnd = {
                                    val handler = currentOnDrag
                                    if (dragActive) handler?.onDragEnd()
                                    dragActive = false
                                },
                                onDragCancel = {
                                    val handler = currentOnDrag
                                    if (dragActive) handler?.onDragCancel()
                                    dragActive = false
                                },
                            )
                        }
                    } else Modifier
                )
                .padding(start = tokens.spacing.md, end = tokens.spacing.md),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onDrag != null) {
                Box(
                    modifier = Modifier
                        .width(10.dp)
                        .height(14.dp),
                ) {
                    TerminalGripDots(
                        orientation = TerminalGripOrientation.VERTICAL,
                        color = tokens.colors.label,
                    )
                }
                Spacer(Modifier.width(tokens.spacing.md))
            }

            titleContent()

            Spacer(Modifier.weight(1f))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            ) {
                Box {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(RoundedCornerShape(tokens.cornerRadii.smallCard))
                            .clickable { settingsMenuExpanded = true },
                        contentAlignment = Alignment.Center,
                    ) {
                        TerminalSlidersGlyph(tokens.colors.label)
                    }
                    TerminalDropdownMenu(
                        expanded = settingsMenuExpanded,
                        onDismissRequest = { settingsMenuExpanded = false },
                        modifier = Modifier.widthIn(min = 200.dp),
                    ) {
                        settingsContent { settingsMenuExpanded = false }
                    }
                }

                if (onSplitH != null || onSplitV != null) {
                    Box {
                        PanelAddButton(onClick = { addMenuExpanded = true })
                        TerminalDropdownMenu(
                            expanded = addMenuExpanded,
                            onDismissRequest = { addMenuExpanded = false },
                            modifier = Modifier.width(160.dp),
                        ) {
                            if (onSplitH != null) {
                                PanelMenuCaption("SPLIT RIGHT")
                                splitableTypes.forEach { (type, label) ->
                                    PanelMenuRow(label) {
                                        addMenuExpanded = false
                                        onSplitH(type)
                                    }
                                }
                                if (onSplitV != null) {
                                    HorizontalDivider(
                                        color = tokens.colors.panelBorder,
                                        thickness = tokens.dimensions.dividerThickness,
                                    )
                                }
                            }
                            if (onSplitV != null) {
                                PanelMenuCaption("SPLIT DOWN")
                                splitableTypes.forEach { (type, label) ->
                                    PanelMenuRow(label) {
                                        addMenuExpanded = false
                                        onSplitV(type)
                                    }
                                }
                            }
                        }
                    }
                }

                PanelCloseButton(
                    color = tokens.colors.label,
                    cornerRadius = tokens.cornerRadii.smallCard,
                    onClick = onClose,
                )
            }
        }

        HorizontalDivider(
            color = tokens.colors.panelBorder,
            thickness = tokens.dimensions.dividerThickness,
        )
    }
}

@Composable
private fun PanelMenuCaption(text: String) {
    val tokens = LocalTerminalDesignTokens.current
    Text(
        text = text,
        color = tokens.colors.label,
        fontSize = tokens.typography.nanoSize,
        fontFamily = TerminalFontFamily,
        letterSpacing = tokens.typography.compactLetterSpacing,
        modifier = Modifier.padding(
            start = tokens.spacing.lg,
            end = tokens.spacing.lg,
            top = tokens.spacing.xs,
            bottom = tokens.spacing.xxs,
        ),
    )
}

@Composable
private fun PanelMenuRow(label: String, onClick: () -> Unit) {
    TerminalMenuItem(text = label, onClick = onClick)
}

/** Компактная иконка настроек (ползунки) для тайтла панели. */
@Composable
private fun TerminalSlidersGlyph(color: Color) {
    Canvas(modifier = Modifier.size(14.dp)) {
        val stroke = 1.2.dp.toPx()
        val knobRadius = size.minDimension * 0.155f
        val topY = size.height * 0.27f
        val bottomY = size.height * 0.73f
        val topKnobX = size.width * 0.36f
        val bottomKnobX = size.width * 0.64f
        val edge = size.width * 0.04f

        drawLine(color, Offset(edge, topY), Offset(topKnobX - knobRadius, topY), strokeWidth = stroke)
        drawLine(color, Offset(topKnobX + knobRadius, topY), Offset(size.width - edge, topY), strokeWidth = stroke)
        drawCircle(color, radius = knobRadius, center = Offset(topKnobX, topY), style = Stroke(stroke))

        drawLine(color, Offset(edge, bottomY), Offset(bottomKnobX - knobRadius, bottomY), strokeWidth = stroke)
        drawLine(
            color,
            Offset(bottomKnobX + knobRadius, bottomY),
            Offset(size.width - edge, bottomY),
            strokeWidth = stroke
        )
        drawCircle(color, radius = knobRadius, center = Offset(bottomKnobX, bottomY), style = Stroke(stroke))
    }
}

/** Кнопка закрытия панели: глиф × того же размера, что и соседняя «+». */
@Composable
private fun PanelCloseButton(
    color: Color,
    cornerRadius: Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clip(RoundedCornerShape(cornerRadius))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        CloseGlyph(color = color, glyphSize = 20.dp)
    }
}

/** Контурный крестик закрытия в сетке 20×20, как у глифа «+». */
@Composable
private fun CloseGlyph(color: Color, glyphSize: Dp) {
    Canvas(modifier = Modifier.size(glyphSize)) {
        val stroke = 1.2.dp.toPx()
        val inset = size.minDimension * 0.24f
        drawLine(
            color = color,
            start = Offset(inset, inset),
            end = Offset(size.width - inset, size.height - inset),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(size.width - inset, inset),
            end = Offset(inset, size.height - inset),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

/** Кнопка «+» в тайтле: тёмный круг с акцентным плюсом по центру (прототип). */
@Composable
private fun PanelAddButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(20.dp)
            .clickable { onClick() },
    ) {
        PlusGlyph(color = MaterialTheme.colorScheme.onSurfaceVariant, glyphSize = 20.dp)
    }
}

private fun panelStateLabel(config: PanelConfig): String = when (val state = config.state) {
    is PanelState.Chart -> state.timeframe
    is PanelState.Dom -> "${state.depth}lvl"
    is PanelState.Trades -> "trades"
    is PanelState.Trading -> "trading"
}
