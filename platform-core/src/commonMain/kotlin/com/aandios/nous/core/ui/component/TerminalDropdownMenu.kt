/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens

/**
 * Попап меню в стиле тематического «островка» (как панели workspace):
 * `panelSurface`, рамка `panelBorder`, скругление `workspacePanel`,
 * без tonal/shadow elevation. Единая обёртка для всех выпадающих меню
 * терминала — не вызывать M3 `DropdownMenu` напрямую.
 */
@Composable
fun TerminalDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        shape = RoundedCornerShape(tokens.cornerRadii.workspacePanel),
        containerColor = tokens.colors.panelSurface,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
        border = BorderStroke(tokens.dimensions.borderWidth, tokens.colors.panelBorder),
        content = content,
    )
}
