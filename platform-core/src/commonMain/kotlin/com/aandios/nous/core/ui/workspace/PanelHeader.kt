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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.component.TerminalGripDots
import com.aandios.nous.core.ui.component.TerminalGripOrientation
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import com.aandios.nous.core.workspace.PanelConfig
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
    onClose: (() -> Unit)? = null,
    onSplitH: ((PanelType) -> Unit)? = null,
    onSplitV: ((PanelType) -> Unit)? = null,
    onDrag: PanelDragHandlers? = null,
    modifier: Modifier = Modifier,
    titleContent: (@Composable () -> Unit)? = null,
    settingsContent: (@Composable (dismiss: () -> Unit) -> Unit)? = null,
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

        if (titleContent != null) {
            titleContent()
        } else {
            val prefix = when (config.type) {
                PanelType.CHART -> "▤"; PanelType.DOM -> "▥"; PanelType.TRADES -> "▦"; PanelType.TRADING -> "▧"
            }
            Text(
                prefix,
                color = tokens.colors.accent,
                fontSize = tokens.typography.smallSize,
                fontFamily = TerminalFontFamily,
            )
            Spacer(Modifier.width(tokens.spacing.sm))
            Text(
                text = "${config.symbol} · ${panelStateLabel(config)}",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = tokens.typography.smallSize,
                fontFamily = TerminalFontFamily,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.weight(1f))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            if (settingsContent != null) {
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
                    DropdownMenu(
                        expanded = settingsMenuExpanded,
                        onDismissRequest = { settingsMenuExpanded = false },
                        modifier = Modifier.widthIn(min = 200.dp),
                    ) {
                        settingsContent { settingsMenuExpanded = false }
                    }
                }
            }

            if (onSplitH != null || onSplitV != null) {
                Box {
                    PanelAddButton(onClick = { addMenuExpanded = true })
                    DropdownMenu(
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

            if (onClose != null) {
                Text(
                    "×",
                    color = tokens.colors.label,
                    fontSize = tokens.typography.compactActionSize,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onClose() }
                        .padding(horizontal = tokens.spacing.xxs),
                )
            }
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
    val tokens = LocalTerminalDesignTokens.current
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = tokens.typography.compactSize,
                fontFamily = TerminalFontFamily,
            )
        },
        onClick = onClick,
    )
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
        drawLine(color, Offset(bottomKnobX + knobRadius, bottomY), Offset(size.width - edge, bottomY), strokeWidth = stroke)
        drawCircle(color, radius = knobRadius, center = Offset(bottomKnobX, bottomY), style = Stroke(stroke))
    }
}

/** Кнопка «+» в тайтле: тёмный круг с акцентным плюсом по центру (прототип). */
@Composable
private fun PanelAddButton(onClick: () -> Unit) {
    val circleFill = MaterialTheme.colorScheme.surfaceVariant
    val circleBorder = MaterialTheme.colorScheme.outlineVariant
    val plusColor = MaterialTheme.colorScheme.primary
    Canvas(
        modifier = Modifier
            .size(20.dp)
            .clip(CircleShape)
            .clickable { onClick() },
    ) {
        val stroke = 1.5.dp.toPx()
        val radius = size.minDimension / 2f
        drawCircle(color = circleFill, radius = radius)
        drawCircle(color = circleBorder, radius = radius - stroke / 2f, style = Stroke(stroke))
        val arm = radius * 0.5f
        val cx = size.width / 2f
        val cy = size.height / 2f
        drawLine(plusColor, Offset(cx - arm, cy), Offset(cx + arm, cy), strokeWidth = stroke, cap = StrokeCap.Round)
        drawLine(plusColor, Offset(cx, cy - arm), Offset(cx, cy + arm), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}

private fun panelStateLabel(config: PanelConfig): String = when (val state = config.state) {
    is com.aandios.nous.core.workspace.PanelState.Chart -> state.timeframe
    is com.aandios.nous.core.workspace.PanelState.Dom -> "${state.depth}lvl"
    is com.aandios.nous.core.workspace.PanelState.Trades -> "trades"
    is com.aandios.nous.core.workspace.PanelState.Trading -> "trading"
}
