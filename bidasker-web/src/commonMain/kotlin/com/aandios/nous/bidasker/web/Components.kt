/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.bidasker.web

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun FootprintToolbar(
    symbol: String,
    timeframe: String,
    availableSymbols: List<String>,
    onSymbolChange: (String) -> Unit,
    onTimeframeChange: (String) -> Unit,
    tariffName: String
) {
    var symbolMenuOpen by remember { mutableStateOf(false) }
    var timeframeMenuOpen by remember { mutableStateOf(false) }

    val timeframes = listOf("1m", "5m", "15m", "30m", "1h", "4h", "1d")

    Surface(
        color = Color(0xFF121212),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Symbol selector
            Box {
                Text(
                    modifier = Modifier
                        .clickable { symbolMenuOpen = true }
                        .padding(horizontal = 8.dp),
                    text = symbol,
                    color = Color(0xFF00C853),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
                DropdownMenu(
                    expanded = symbolMenuOpen,
                    onDismissRequest = { symbolMenuOpen = false }
                ) {
                    for (s in availableSymbols) {
                        DropdownMenuItem(
                            text = { Text(s, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                            onClick = {
                                onSymbolChange(s)
                                symbolMenuOpen = false
                            }
                        )
                    }
                }
            }

            Text(" | ", color = Color(0xFF444444), fontSize = 13.sp)

            // Timeframe selector
            Box {
                Text(
                    modifier = Modifier
                        .clickable { timeframeMenuOpen = true }
                        .padding(horizontal = 4.dp),
                    text = timeframe,
                    color = Color(0xFFE0E0E0),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                DropdownMenu(
                    expanded = timeframeMenuOpen,
                    onDismissRequest = { timeframeMenuOpen = false }
                ) {
                    for (tf in timeframes) {
                        DropdownMenuItem(
                            text = { Text(tf, fontFamily = FontFamily.Monospace, fontSize = 12.sp) },
                            onClick = {
                                onTimeframeChange(tf)
                                timeframeMenuOpen = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Tariff badge
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = when (tariffName) {
                    "Pro" -> Color(0xFF00C853)
                    "Registered" -> Color(0xFF2196F3)
                    else -> Color(0xFF555555)
                }.copy(alpha = 0.2f)
            ) {
                Text(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                    text = tariffName,
                    color = when (tariffName) {
                        "Pro" -> Color(0xFF00C853)
                        "Registered" -> Color(0xFF2196F3)
                        else -> Color(0xFF888888)
                    },
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun StatusBar(
    symbol: String,
    timeframe: String,
    candleCount: Int
) {
    Surface(
        color = Color(0xFF0A0A0A),
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "$symbol | $timeframe | $candleCount candles",
                color = Color(0xFF666666),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.weight(1f))
            Text(
                "Bidasker v0.1",
                color = Color(0xFF444444),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun UpSellOverlay(limits: TariffLimits) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A).copy(alpha = 0.95f)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Free tier limit reached",
                color = Color(0xFFE0E0E0),
                fontSize = 18.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Max ${limits.maxInstruments} instrument · ${limits.timeframes.joinToString()} · ${limits.maxHistoryHours}h history",
                color = Color(0xFF888888),
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFF00C853)
            ) {
                Text(
                    modifier = Modifier
                        .padding(horizontal = 24.dp, vertical = 10.dp),
                    text = "Upgrade to Registered (Free)",
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
