/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import com.aandios.nous.core.ui.workspace.WorkspaceLayoutThumbnail
import com.aandios.nous.core.workspace.WorkspaceConfig

/**
 * Содержимое меню выбора шаблона workspace: заголовок + горизонтальная
 * лента карточек-миниатюр. Общее для попапа Market Watch и кнопки «+».
 */
@Composable
fun WorkspaceTemplatePicker(
    title: String,
    templates: List<Pair<String, WorkspaceConfig>>,
    onPick: (String, WorkspaceConfig) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTerminalDesignTokens.current
    Column(
        modifier = modifier
            .width(tokens.dimensions.marketWatchTemplateMenuWidth)
            .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.sm),
    ) {
        Text(
            text = title,
            color = tokens.colors.label,
            fontSize = tokens.typography.microSize,
            fontFamily = TerminalFontFamily,
            modifier = Modifier.padding(horizontal = tokens.spacing.xs, vertical = tokens.spacing.xs),
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
        ) {
            templates.forEach { (name, template) ->
                WorkspaceTemplateCard(
                    name = name,
                    template = template,
                    onClick = { onPick(name, template) },
                )
            }
        }
    }
}

/** Карточка-миниатюра шаблона workspace (цвета — из активной темы). */
@Composable
fun WorkspaceTemplateCard(
    name: String,
    template: WorkspaceConfig,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTerminalDesignTokens.current
    Column(
        modifier = modifier
            .width(tokens.dimensions.marketWatchTemplateCardWidth)
            .clickable(onClick = onClick)
            .background(
                tokens.colors.panelHeaderSurface.copy(alpha = 0.45f),
                RoundedCornerShape(tokens.cornerRadii.smallCard),
            )
            .padding(tokens.spacing.sm),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WorkspaceLayoutThumbnail(
            config = template,
            modifier = Modifier.fillMaxWidth()
                .height(tokens.dimensions.marketWatchTemplatePreviewHeight),
        )
        Text(
            text = name,
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = tokens.typography.microSize,
            fontFamily = TerminalFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = tokens.spacing.sm),
        )
    }
}
