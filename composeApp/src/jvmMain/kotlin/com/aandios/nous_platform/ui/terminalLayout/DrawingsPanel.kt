/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun DrawingsPanel(
    modifier: Modifier = Modifier.Companion
) {
    val drawingTools = listOf(
        "Trend Line",
        "Horizontal Line",
        "Vertical Line",
        "Fibonacci Retracement",
        "Fibonacci Extension",
        "Rectangle",
        "Ellipse",
        "Triangle",
        "Channel",
        "Text"
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 4.dp)
    ) {
        items(drawingTools) { tool ->
            Surface(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .clickable { /* Выбрать инструмент */ }
            ) {
                Text(
                    text = tool,
                    modifier = Modifier.Companion
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}