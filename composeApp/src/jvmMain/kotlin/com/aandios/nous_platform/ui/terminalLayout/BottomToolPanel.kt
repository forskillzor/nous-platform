/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aandios.nous.api.market.paper.PaperTrading
import com.aandios.nous.feature.trading.ui.TradingViewModel
import org.jetbrains.compose.resources.painterResource
import org.koin.compose.koinInject
import nous_platform.composeapp.generated.resources.Res
import nous_platform.composeapp.generated.resources.close

private val accentColor = Color(0xFF00C853)

/**
 * Ручка изменения высоты нижней панели: вертикальный drag.
 */
@Composable
fun BottomResizeHandle(
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier.Companion,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .background(if (hovered) accentColor.copy(alpha = 0.4f) else Color(0xFF333333))
            .hoverable(interaction)
            .pointerInput(Unit) {
                detectVerticalDragGestures { _, dragAmount -> onDrag(dragAmount) }
            }
    )
}

@Composable
fun BottomToolPanel(
    type: BottomToolType,
    onClose: () -> Unit,
    portfolioTab: PortfolioTab = PortfolioTab.POSITIONS,
    onPortfolioTabChange: (PortfolioTab) -> Unit = {},
    modifier: Modifier = Modifier.Companion
) {
    // VM портфолио нужен и заголовку (тумблер Paper), и контенту
    val tradingViewModel: TradingViewModel? =
        if (type == BottomToolType.PORTFOLIO) koinInject() else null

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.Companion.fillMaxSize()
        ) {
            // Компактная строка заголовка: название + табы (для Portfolio) + закрыть
            Row(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .height(30.dp)
                    .padding(start = 12.dp, end = 6.dp),
                verticalAlignment = Alignment.Companion.CenterVertically
            ) {
                Text(
                    text = when (type) {
                        BottomToolType.PORTFOLIO -> "Portfolio"
                        BottomToolType.CONSOLE -> "Console"
                        BottomToolType.EDITOR -> "Code Editor"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )

                if (type == BottomToolType.PORTFOLIO) {
                    Spacer(Modifier.Companion.width(16.dp))
                    // Табы занимают оставшееся место; при нехватке — скроллятся
                    Row(
                        modifier = Modifier.Companion
                            .weight(1f)
                            .height(30.dp)
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.Companion.CenterVertically
                    ) {
                        PortfolioTab.values().forEach { tab ->
                            PortfolioTabItem(
                                tab = tab,
                                selected = portfolioTab == tab,
                                onClick = { onPortfolioTabChange(tab) }
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.Companion.weight(1f))
                }

                // Тумблер Paper-торговли для портфолио
                if (type == BottomToolType.PORTFOLIO) {
                    var paperEnabled by remember { mutableStateOf(PaperTrading.enabled) }
                    Text(
                        text = if (paperEnabled) "Paper ✔" else "Paper",
                        color = if (paperEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.Companion
                            .background(
                                if (paperEnabled) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                else Color.Transparent,
                                MaterialTheme.shapes.small,
                            )
                            .clickable {
                                paperEnabled = !paperEnabled
                                PaperTrading.enabled = paperEnabled
                                tradingViewModel?.reload()
                            }
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.Companion.width(6.dp))
                }

                Box(
                    modifier = Modifier.Companion
                        .size(20.dp)
                        .clickable(onClick = onClose),
                    contentAlignment = Alignment.Companion.Center
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.close),
                        contentDescription = "Close",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.Companion.size(14.dp)
                    )
                }
            }

            Box(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.outline)
            )

            // Контент
            when (type) {
                BottomToolType.PORTFOLIO -> {
                    tradingViewModel?.let { vm ->
                        PortfolioPanel(
                            tradingViewModel = vm,
                            selectedTab = portfolioTab,
                            modifier = Modifier.Companion.weight(1f)
                        )
                    } ?: Spacer(Modifier.Companion.weight(1f))
                }
                BottomToolType.CONSOLE -> ConsolePanel(modifier = Modifier.Companion.weight(1f))
                BottomToolType.EDITOR -> CodeEditorPanel(modifier = Modifier.Companion.weight(1f))
            }
        }
    }
}

/** Компактный таб с подчёркиванием выбранного (в стиле m3-индикатора). */
@Composable
private fun PortfolioTabItem(
    tab: PortfolioTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier.Companion
            .height(30.dp)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Companion.Center
    ) {
        Text(
            text = tab.name,
            color = if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelMedium
        )
        if (selected) {
            Box(
                modifier = Modifier.Companion
                    .align(Alignment.Companion.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)
                    )
            )
        }
    }
}
