/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens

@Composable
fun TerminalPanelTitle(
    text: String,
    modifier: Modifier = Modifier
) {
    val spacing = LocalTerminalDesignTokens.current.spacing
    Text(
        text = text,
        modifier = modifier.padding(horizontal = spacing.lg, vertical = spacing.md),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        style = MaterialTheme.typography.titleSmall
    )
}
