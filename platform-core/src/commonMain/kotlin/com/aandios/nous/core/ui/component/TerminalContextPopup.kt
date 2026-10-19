/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens

@Composable
fun TerminalContextPopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    title: String,
    confirmText: String,
    onConfirm: () -> Unit,
    message: String? = null,
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    placeholder: String = "",
    destructive: Boolean = false,
) {
    val tokens = LocalTerminalDesignTokens.current
    TerminalDropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.width(tokens.dimensions.contextPopupWidth),
    ) {
        Column(Modifier.padding(horizontal = tokens.spacing.lg, vertical = tokens.spacing.md)) {
            Text(
                title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = tokens.typography.bodySize,
                fontWeight = FontWeight.Bold,
            )
            message?.let {
                Text(
                    it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = tokens.typography.compactSize,
                    lineHeight = tokens.typography.controlLineHeight,
                    modifier = Modifier.padding(top = tokens.spacing.md),
                )
            }
            if (value != null && onValueChange != null) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = tokens.typography.bodySize,
                        fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.fillMaxWidth().padding(top = tokens.spacing.lg)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                            MaterialTheme.shapes.small,
                        )
                        .padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.md),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = tokens.typography.controlSize,
                            )
                        }
                        inner()
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = tokens.spacing.md),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text("Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onConfirm) {
                    Text(
                        confirmText,
                        color = if (destructive) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
