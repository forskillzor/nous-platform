/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens

@Composable
fun TerminalDivider(
    modifier: Modifier = Modifier
) {
    val tokens = LocalTerminalDesignTokens.current
    Divider(
        modifier = modifier,
        color = MaterialTheme.colorScheme.outline,
        thickness = tokens.dimensions.dividerThickness
    )
}
