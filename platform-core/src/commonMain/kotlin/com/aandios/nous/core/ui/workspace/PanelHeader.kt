/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

@Composable
fun PanelHeader(
    config: PanelConfig,
    onClose: (() -> Unit)? = null,
    onSplitH: ((PanelType) -> Unit)? = null,
    onSplitV: ((PanelType) -> Unit)? = null,
    onDrag: PanelDragHandlers? = null,
    modifier: Modifier = Modifier
) {
    var splitMenuExpanded by remember { mutableStateOf(false) }
    var splitDirection by remember { mutableStateOf(false) } // false=H, true=V
    var menuX by remember { mutableFloatStateOf(0f) }
    var headerOrigin by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    // Порог, после которого «нажатие» превращается в перетаскивание
    // (сплит срабатывает только когда панель реально тащат, а не при клике)
    val dragThresholdPx = with(density) { 12.dp.toPx() }
    // Стабильная ссылка на обработчики: dragState обновляется на каждое движение
    // (рекомпозиция), из-за чего pointerInput с ключом onDrag обрывал бы жест
    val currentOnDrag by rememberUpdatedState(onDrag)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(22.dp)
            .background(Color(0xFF1A1A1A))
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
            .padding(horizontal = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val prefix = when (config.type) {
            PanelType.CHART -> "▤"; PanelType.DOM -> "▥"; PanelType.TRADES -> "▦"; PanelType.TRADING -> "▧"
        }
        Text(prefix, color = Color(0xFF00C853), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.width(4.dp))
        Text(
            text = "${config.symbol} · ${panelStateLabel(config)}",
            color = Color(0xFFAAAAAA), fontSize = 10.sp, fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        // Split buttons — each opens a menu to choose panel type
        if (onSplitV != null || onSplitH != null) {
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (onSplitV != null) {
                    Box {
                        Text("┃", color = Color(0xFF555555), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable { splitDirection = true; splitMenuExpanded = true }.padding(horizontal = 2.dp))
                        DropdownMenu(expanded = splitMenuExpanded && splitDirection, onDismissRequest = { splitMenuExpanded = false }) {
                            listOf(PanelType.CHART to "Chart", PanelType.DOM to "DOM", PanelType.TRADES to "Trades", PanelType.TRADING to "Trading").forEach { (t, label) ->
                                DropdownMenuItem(text = { Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }, onClick = {
                                    splitMenuExpanded = false; onSplitV(t)
                                })
                            }
                        }
                    }
                }
                if (onSplitH != null) {
                    Box {
                        Text("━", color = Color(0xFF555555), fontSize = 12.sp, fontFamily = FontFamily.Monospace,
                            modifier = Modifier.clickable { splitDirection = false; splitMenuExpanded = true }.padding(horizontal = 2.dp))
                        DropdownMenu(expanded = splitMenuExpanded && !splitDirection, onDismissRequest = { splitMenuExpanded = false }) {
                            listOf(PanelType.CHART to "Chart", PanelType.DOM to "DOM", PanelType.TRADES to "Trades", PanelType.TRADING to "Trading").forEach { (t, label) ->
                                DropdownMenuItem(text = { Text(label, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }, onClick = {
                                    splitMenuExpanded = false; onSplitH(t)
                                })
                            }
                        }
                    }
                }
            }
        }

        if (onClose != null) {
            Text("×", color = Color(0xFF555555), fontSize = 14.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onClose() }.padding(start = 4.dp))
        }
    }
}

private fun panelStateLabel(config: PanelConfig): String = when (val state = config.state) {
    is com.aandios.nous.core.workspace.PanelState.Chart -> state.timeframe
    is com.aandios.nous.core.workspace.PanelState.Dom -> "${state.depth}lvl"
    is com.aandios.nous.core.workspace.PanelState.Trades -> "trades"
    is com.aandios.nous.core.workspace.PanelState.Trading -> "trading"
}
