/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.aandios.nous.feature.localstorage.LocalStorage
import com.aandios.nous.feature.localstorage.LocalStorage.CacheStats
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun SettingsWindow(
    storage: LocalStorage,
    onClose: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Storage", "Settings")

    DialogWindow(
        onCloseRequest = onClose,
        title = "Settings",
        state = rememberDialogState(width = 640.dp, height = 500.dp)
    ) {
        Surface(color = Color(0xFF0A0E14), modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                Row(Modifier.fillMaxWidth().background(Color(0xFF12171F)).padding(4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    tabs.forEachIndexed { i, name ->
                        val active = selectedTab == i
                        Text(
                            name, fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            color = if (active) Color(0xFF5B9BD5) else Color(0xFF888888),
                            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                            modifier = Modifier
                                .background(if (active) Color(0xFF1E2733) else Color.Transparent, RoundedCornerShape(4.dp))
                                .clickable { selectedTab = i }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                when (selectedTab) {
                    0 -> StorageTab(storage)
                    1 -> SettingsTab(storage)
                }
            }
        }
    }
}

@Composable
private fun StorageTab(storage: LocalStorage) {
    val scope = rememberCoroutineScope()
    var stats by remember { mutableStateOf<List<CacheStats>>(emptyList()) }
    var dbSize by remember { mutableStateOf(0L) }
    var confirmAction by remember { mutableStateOf<String?>(null) }
    var deleteBeforeDays by remember { mutableStateOf("7") }

    fun refresh() { scope.launch { val (s, sz) = storage.getDetailedStats(); stats = s; dbSize = sz } }
    LaunchedEffect(Unit) { refresh() }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        // Header
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Local Storage", color = Color(0xFF5B9BD5), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
            Text(formatBytes(dbSize), color = Color(0xFF888888), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        if (stats.isEmpty()) {
            Text("No cached data", color = Color(0xFF888888), fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(vertical = 20.dp))
        }

        // Per-symbol stats
        stats.forEach { stat ->
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF12171F)), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(10.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stat.key, color = Color(0xFF5B9BD5), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Text(formatBytes(stat.sizeBytes), color = Color(0xFF73D0A1), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${stat.count} rows · ${formatDuration(stat.durationMs)} · ${formatTs(stat.firstTs)} – ${formatTs(stat.lastTs)}", color = Color(0xFF888888), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { confirmAction = stat.key }, modifier = Modifier.height(24.dp)) {
                            Text("Clear", color = Color(0xFFF07178), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Period-based cleanup
        Text("Delete older than", color = Color(0xFF888888), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = deleteBeforeDays, onValueChange = { deleteBeforeDays = it.filter { c -> c.isDigit() } },
                modifier = Modifier.width(60.dp), singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Color(0xFF5B9BD5))
            )
            Text(" days ago", color = Color(0xFF888888), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    confirmAction = "delete_older_${deleteBeforeDays}d"
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.8f)),
                shape = RoundedCornerShape(4.dp), modifier = Modifier.height(32.dp)
            ) { Text("Delete", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
        }

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = { confirmAction = "all" },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
            shape = RoundedCornerShape(4.dp), modifier = Modifier.fillMaxWidth()
        ) { Text("Clear ALL Data", color = Color.White, fontSize = 12.sp, fontFamily = FontFamily.Monospace) }
    }

    // Confirmation dialog
    if (confirmAction != null) {
        val msg = confirmAction!!
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text("Confirm deletion", color = Color(0xFFF07178), fontSize = 14.sp, fontFamily = FontFamily.Monospace) },
            text = { Text("Delete ${msg.replace("_", " ")}?\nThis cannot be undone.", color = Color(0xFF888888), fontSize = 12.sp, fontFamily = FontFamily.Monospace) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            when {
                                msg.startsWith("Footprint") -> storage.clearFootprint(symbol = msg.removePrefix("Footprint "), olderThan = null)
                                msg.startsWith("Candles") -> {
                                    val parts = msg.removePrefix("Candles ").split(" ")
                                    storage.clearCandles(symbol = parts[0], timeframe = parts.getOrNull(1), olderThan = null)
                                }
                                msg.startsWith("delete_older") -> {
                                    val days = msg.removePrefix("delete_older_").removeSuffix("d").toIntOrNull() ?: 7
                                    val cutoff = System.currentTimeMillis() - (days * 86_400_000L)
                                    storage.clearCandles(olderThan = cutoff)
                                    storage.clearFootprint(olderThan = cutoff)
                                }
                                msg == "all" -> storage.clearAll()
                                else -> { /* per-symbol clear handled above */ }
                            }
                            confirmAction = null
                            refresh()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F))
                ) { Text("Delete", color = Color.White) }
            },
            dismissButton = { TextButton(onClick = { confirmAction = null }) { Text("Cancel", color = Color(0xFF888888)) } }
        )
    }
}

@Composable
private fun SettingsTab(storage: LocalStorage) {
    val scope = rememberCoroutineScope()
    var chartState by remember { mutableStateOf<LocalStorage.ChartState?>(null) }
    var domOptions by remember { mutableStateOf<String?>(null) }
    var tradesOptions by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        chartState = storage.loadChartState()
        domOptions = storage.loadDomOptions()
        tradesOptions = storage.loadTradesOptions()
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Chart State", color = Color(0xFF5B9BD5), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        if (chartState != null) {
            SettingsRow("Symbol", chartState!!.symbol)
            SettingsRow("Timeframe", chartState!!.timeframe)
            SettingsRow("Mode", chartState!!.mode)
        } else {
            Text("No saved chart state", color = Color(0xFF888888), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
        }

        Spacer(Modifier.height(8.dp))
        Text("DOM Options", color = Color(0xFF5B9BD5), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(domOptions ?: "No saved DOM options", color = Color(0xFF888888), fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 3)

        Spacer(Modifier.height(8.dp))
        Text("Trades Options", color = Color(0xFF5B9BD5), fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
        Text(tradesOptions ?: "No saved Trades options", color = Color(0xFF888888), fontSize = 11.sp, fontFamily = FontFamily.Monospace, maxLines = 3)

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                scope.launch {
                    storage.clearSettings()
                    chartState = null; domOptions = null; tradesOptions = null
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.6f)),
            shape = RoundedCornerShape(4.dp)
        ) { Text("Clear Settings", color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace) }
    }
}

@Composable
private fun SettingsRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = Color(0xFF888888), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
        Text(value, color = Color(0xFF5B9BD5), fontFamily = FontFamily.Monospace, fontSize = 11.sp)
    }
}

private val dateFmt = SimpleDateFormat("MM-dd HH:mm", Locale.US)
private fun formatTs(ts: Long): String = if (ts <= 0) "—" else dateFmt.format(Date(ts))

private fun formatDuration(ms: Long): String = when {
    ms <= 0 -> "—"
    ms < 3_600_000 -> "${ms / 60_000}m"
    ms < 86_400_000 -> "${ms / 3_600_000}h ${(ms % 3_600_000) / 60_000}m"
    else -> "${ms / 86_400_000}d ${(ms % 86_400_000) / 3_600_000}h"
}

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1_000_000_000 -> "${"%.2f".format(bytes / 1_000_000_000.0)} GB"
    bytes >= 1_000_000 -> "${"%.1f".format(bytes / 1_000_000.0)} MB"
    bytes >= 1_000 -> "${"%.1f".format(bytes / 1_000.0)} KB"
    else -> "$bytes B"
}
