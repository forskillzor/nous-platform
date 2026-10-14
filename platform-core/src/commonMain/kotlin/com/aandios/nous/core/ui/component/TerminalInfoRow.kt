/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens

@Composable
fun TerminalInfoRow(
    label: String,
    value: String,
    isPositive: Boolean? = null,
    modifier: Modifier = Modifier
) {
    val tokens = LocalTerminalDesignTokens.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )

        val valueColor = when (isPositive) {
            true -> tokens.colors.profit
            false -> tokens.colors.loss
            null -> MaterialTheme.colorScheme.onSurface
        }

        Text(
            text = value,
            color = valueColor,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
