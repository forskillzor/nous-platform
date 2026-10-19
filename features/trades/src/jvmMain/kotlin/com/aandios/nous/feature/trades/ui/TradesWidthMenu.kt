/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.trades.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import kotlin.math.roundToInt

/**
 * Настройки Trades для ⚙ тайтла панели (содержимое меню): ширина ползунком
 * от измеренного минимума (текст строки сделки, TradesWidget →
 * `measuredMinWidthDp`) до рекомендованной ширины. Превью ширины — на каждое
 * движение ползунка, персист — по окончании жеста.
 */
@Composable
fun TradesWidthMenuContent(viewModel: TradesViewModel) {
    val tokens = LocalTerminalDesignTokens.current
    val panelWidthDp by viewModel.panelWidthDp.collectAsState()
    val measuredMinWidthDp by viewModel.measuredMinWidthDp.collectAsState()
    val maxWidth = TradesRecommendedWidth.value
    val lo = measuredMinWidthDp.coerceAtMost((maxWidth - 8f).coerceAtLeast(40f))
    val hi = maxWidth.coerceAtLeast(lo + 8f)
    val current = (panelWidthDp ?: maxWidth).coerceIn(lo, hi)

    Column(
        modifier = Modifier
            .width(200.dp)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = "Width  ${current.roundToInt()} dp",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = tokens.typography.compactSize,
            fontFamily = TerminalFontFamily,
        )
        Spacer(Modifier.height(2.dp))
        Slider(
            value = current,
            onValueChange = { viewModel.setPanelWidth(it) },
            onValueChangeFinished = { viewModel.persistPanelWidth() },
            valueRange = lo..hi,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
