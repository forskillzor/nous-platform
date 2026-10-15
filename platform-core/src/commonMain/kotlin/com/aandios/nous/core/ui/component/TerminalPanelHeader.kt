/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens

@Composable
fun TerminalPanelHeader(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current

    Column(modifier = modifier.fillMaxWidth()) {
        Surface(
            color = tokens.colors.panelHeaderSurface,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = tokens.spacing.sm,
                        vertical = tokens.spacing.sm,
                    ),
                verticalArrangement = Arrangement.spacedBy(tokens.spacing.xxs),
                content = content,
            )
        }
        HorizontalDivider(
            color = tokens.colors.panelBorder,
            thickness = tokens.dimensions.dividerThickness,
        )
    }
}
