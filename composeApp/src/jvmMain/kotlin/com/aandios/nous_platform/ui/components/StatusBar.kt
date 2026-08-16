/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun TerminalStatusBar(
    modifier: Modifier = Modifier,
    connectionStatus: String = "Connected",
    latency: String = "45ms"
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Левая часть - статус
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatusIndicator(
                    text = connectionStatus,
                    isOnline = connectionStatus == "Connected"
                )

                StatusIndicator(
                    text = "Latency: $latency",
                    isOnline = latency.toIntOrNull() ?: 100 < 100
                )
            }

            // Правая часть - время и системная информация
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                CurrentTime()

                Text(
                    text = "v0.1.0 • Kotlin ${KotlinVersion.CURRENT}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun StatusIndicator(
    text: String,
    isOnline: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .background(
                    color = if (isOnline) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    shape = MaterialTheme.shapes.extraSmall
                )
        )

        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
private fun CurrentTime() {
    val currentTime = remember {
//        Clock.System.now()
//            .toLocalDateTime(TimeZone.currentSystemDefault())
//            .toString()
//            .substring(11, 19) // HH:mm:ss
    }

//    Text(
//        text = currentTime,
//        color = MaterialTheme.colorScheme.onSurfaceVariant,
//        style = MaterialTheme.typography.labelSmall
//    )
}