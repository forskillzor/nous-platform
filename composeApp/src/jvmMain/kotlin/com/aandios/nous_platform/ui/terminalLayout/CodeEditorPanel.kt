/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Divider
import androidx.compose.material.IconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous_platform.ui.components.TerminalButton
import org.jetbrains.compose.resources.painterResource
import nous_platform.composeapp.generated.resources.Res
import nous_platform.composeapp.generated.resources.icon_plus

// Редактор кода
@Composable
fun CodeEditorPanel(
    modifier: Modifier = Modifier.Companion
) {
    var selectedFile by remember { mutableStateOf("Moving Average Strategy") }
    var code by remember {
        mutableStateOf(
            """// Moving Average Crossover Strategy
indicator("MA Crossover", overlay=true)

fastLength = input(9, "Fast MA")
slowLength = input(21, "Slow MA")

fastMA = ta.sma(close, fastLength)
slowMA = ta.sma(close, slowLength)

plot(fastMA, color=color.blue)
plot(slowMA, color=color.red)

// Entry signals
if (ta.crossover(fastMA, slowMA))
    strategy.entry("Long", strategy.long)

if (ta.crossunder(fastMA, slowMA))
    strategy.entry("Short", strategy.short)"""
        )
    }

    val files = listOf(
        "Moving Average Strategy",
        "RSI Strategy",
        "MACD Divergence",
        "Grid Bot",
        "Scalping Strategy"
    )

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Верхняя панель с файлами
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.Companion.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.Companion
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                files.forEach { file ->
                    FilterChip(
                        selected = selectedFile == file,
                        onClick = { selectedFile = file },
                        label = {
                            Text(
                                text = file,
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        modifier = Modifier.Companion.height(32.dp)
                    )
                }

                IconButton(
                    onClick = { /* New file */ },
                    modifier = Modifier.Companion.size(32.dp)
                ) {
                    Icon(
                        painter = painterResource(Res.drawable.icon_plus),
                        contentDescription = "New",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.Companion.size(16.dp)
                    )
                }
            }
        }

        Divider()

        // Редактор кода
        Box(
            modifier = Modifier.Companion
                .weight(1f)
                .background(Color(0xFF1E1E1E)) // Темный фон как в IDE
                .padding(8.dp)
        ) {
            BasicTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier.Companion.fillMaxSize(),
                textStyle = TextStyle(
                    color = Color(0xFFD4D4D4),
                    fontFamily = FontFamily.Companion.Monospace,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.Companion.fillMaxSize()
                    ) {
                        innerTextField()
                    }
                }
            )
        }

        Divider()

        // Нижняя панель с кнопками
        Row(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TerminalButton(
                onClick = { /* Save */ },
                modifier = Modifier.Companion.weight(1f)
            ) {
                Text("Save")
            }

            TerminalButton(
                onClick = { /* Compile */ },
                modifier = Modifier.Companion.weight(1f)
            ) {
                Text("Compile")
            }

            TerminalButton(
                onClick = { /* Run */ },
                modifier = Modifier.Companion.weight(1f),
                isActive = true
            ) {
                Text("Run")
            }
        }
    }
}