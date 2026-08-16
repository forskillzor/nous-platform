/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Divider
import androidx.compose.material.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource
import nous_platform.composeapp.generated.resources.Res
import nous_platform.composeapp.generated.resources.send
import nous_platform.composeapp.generated.resources.trash
import java.text.SimpleDateFormat
import java.util.Date

// Консоль (исправленная версия)
@Composable
fun ConsolePanel(
    modifier: Modifier = Modifier.Companion
) {
    val consoleLines = remember {
        mutableStateListOf(
            "[INFO] Terminal initialized",
            "[INFO] Connected to Binance",
            "[INFO] WebSocket connected for BTCUSDT",
            "[DEBUG] Order book snapshot received",
            "[TRADE] BUY 0.1 BTC @ 46,500.00",
            "[INFO] Position opened: BTCUSDT",
            "[WARN] High volatility detected",
            "[ERROR] Failed to place order: Insufficient balance",
            "[INFO] Reconnecting WebSocket...",
            "[INFO] WebSocket reconnected",
            "[TRADE] SELL 0.05 BTC @ 46,800.00",
            "[INFO] Position closed: Profit $150.00"
        )
    }

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope() // 👈 Добавляем coroutine scope

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Заголовок с кнопкой очистки
        Row(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Companion.CenterVertically
        ) {
            Text(
                text = "REPL Console",
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.labelMedium
            )

            IconButton(
                onClick = {
                    consoleLines.clear()
                    // Можно добавить системное сообщение
                    consoleLines.add("[INFO] Console cleared")
                },
                modifier = Modifier.Companion.size(24.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.trash),
                    contentDescription = "Clear",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.Companion.size(16.dp)
                )
            }
        }

        Divider()

        // Лог консоли
        LazyColumn(
            state = listState,
            modifier = Modifier.Companion.weight(1f),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            reverseLayout = true // 👈 Переворачиваем список чтобы новые сообщения были снизу
        ) {
            items(consoleLines.size) { index ->
                val line = consoleLines[index] // Теперь индексы в правильном порядке
                val color = when {
                    line.startsWith("[ERROR]") -> MaterialTheme.colorScheme.secondary
                    line.startsWith("[WARN]") -> Color.Companion.Yellow
                    line.startsWith("[TRADE]") -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                }

                Text(
                    text = line,
                    color = color,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Companion.Monospace,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.Companion.padding(vertical = 2.dp)
                )
            }
        }

        Divider()

        // Поле ввода
        Row(
            modifier = Modifier.Companion
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.Companion.CenterVertically
        ) {
            Text(
                text = ">",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.Companion.padding(end = 4.dp)
            )

            BasicTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.Companion
                    .weight(1f)
                    .height(32.dp),
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Companion.Monospace,
                    fontSize = 12.sp
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    Box(
                        modifier = Modifier.Companion
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = MaterialTheme.shapes.small
                            )
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Type command...",
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        innerTextField()
                    }
                }
            )

            IconButton(
                onClick = {
                    if (inputText.isNotBlank()) {
                        // Добавляем команду в лог
                        consoleLines.add(0, "[INPUT] $inputText") // Добавляем в начало (сверху)

                        // Обработка команд
                        processCommand(inputText, consoleLines)

                        // Очищаем поле ввода
                        inputText = ""

                        // Скроллим к началу (к новым сообщениям)
                        coroutineScope.launch {
                            listState.animateScrollToItem(0)
                        }
                    }
                },
                modifier = Modifier.Companion.size(32.dp)
            ) {
                Icon(
                    painter = painterResource(Res.drawable.send),
                    contentDescription = "Send",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.Companion.size(16.dp)
                )
            }
        }
    }
}
// Функция для обработки команд
private fun processCommand(command: String, consoleLines: MutableList<String>) {
    val trimmed = command.trim().lowercase()

    when {
        trimmed == "clear" || trimmed == "cls" -> {
            consoleLines.clear()
            consoleLines.add("[INFO] Console cleared")
        }

        trimmed == "help" -> {
            consoleLines.add(0, "[INFO] Available commands:")
            consoleLines.add(0, "[INFO]   help     - Show this help")
            consoleLines.add(0, "[INFO]   clear    - Clear console")
            consoleLines.add(0, "[INFO]   status   - Show system status")
            consoleLines.add(0, "[INFO]   symbols  - List available symbols")
            consoleLines.add(0, "[INFO]   time     - Show current time")
        }

        trimmed == "status" -> {
            consoleLines.add(0, "[INFO] System Status:")
            consoleLines.add(0, "[INFO]   Connected to Binance: ✅")
            consoleLines.add(0, "[INFO]   WebSocket: ✅")
            consoleLines.add(0, "[INFO]   Memory usage: 256MB")
            consoleLines.add(0, "[INFO]   Uptime: 2h 15m")
        }

        trimmed == "symbols" -> {
            consoleLines.add(0, "[INFO] Available symbols:")
            consoleLines.add(0, "[INFO]   BTCUSDT, ETHUSDT, BNBUSDT, SOLUSDT, XRPUSDT")
            consoleLines.add(0, "[INFO]   ADAUSDT, DOGEUSDT, DOTUSDT, LINKUSDT, LTCUSDT")
        }

        trimmed.startsWith("echo ") -> {
            val message = command.substring(5)
            consoleLines.add(0, "[ECHO] $message")
        }

        trimmed == "time" -> {
            val time = SimpleDateFormat("HH:mm:ss").format(Date())
            consoleLines.add(0, "[INFO] Current time: $time")
        }

        command.isNotBlank() -> {
            consoleLines.add(0, "[ERROR] Unknown command: $command")
            consoleLines.add(0, "[INFO] Type 'help' for available commands")
        }
    }
}
