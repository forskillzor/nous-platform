/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.workspace

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.core.workspace.Templates
import com.aandios.nous.core.workspace.WorkspaceConfig

@Composable
fun WelcomeScreen(
    recentWorkspaces: List<WorkspaceConfig>,
    onSelectTemplate: (WorkspaceConfig) -> Unit,
    onOpenRecent: (WorkspaceConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .verticalScroll(rememberScrollState())
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Logo
        Text("[Nous Platform]", color = Color(0xFF00C853), fontSize = 24.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("v1.0 — Professional Crypto Trading Terminal", color = Color(0xFF666666), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(40.dp))

        // Templates
        Text("NEW WORKSPACE", color = Color(0xFF888888), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            TemplateCard("Scalping", "Chart + DOM + Trades\nBTCUSDT · 1m", "⚡") {
                onSelectTemplate(Templates.scalping())
            }
            TemplateCard("DOM Grid", "12 DOM panels\n10 instruments at once", "📊") {
                val symbols = listOf("BTCUSDT","ETHUSDT","SOLUSDT","BNBUSDT","XRPUSDT","DOGEUSDT","ADAUSDT","LINKUSDT","AVAXUSDT","DOTUSDT","TRXUSDT","LTCUSDT")
                onSelectTemplate(Templates.domGrid(symbols))
            }
            TemplateCard("Order Flow", "Footprint chart\n+ Trades stream", "🔍") {
                onSelectTemplate(Templates.orderFlow())
            }
            TemplateCard("Empty", "Add panels\nmanually", "📄") {
                onSelectTemplate(Templates.empty())
            }
        }

        // Recent
        if (recentWorkspaces.isNotEmpty()) {
            Spacer(Modifier.height(40.dp))
            Text("RECENT WORKSPACES", color = Color(0xFF888888), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                recentWorkspaces.take(10).forEach { ws ->
                    Row(
                        modifier = Modifier
                            .width(500.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF121212))
                            .clickable { onOpenRecent(ws) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(ws.name, color = Color(0xFFE0E0E0), fontSize = 13.sp, fontFamily = FontFamily.Monospace)
                        Text(ws.group, color = Color(0xFF666666), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        Spacer(Modifier.height(40.dp))
        Text("What's new in v1.0", color = Color(0xFF888888), fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier.width(500.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF121212)).padding(16.dp)
        ) {
            Column {
                listOf(
                    "• Workspace system — IDE-like project tabs",
                    "• Drawing tools on chart — trend lines, rectangles, ruler",
                    "• Undo/Redo with Ctrl+Z / Ctrl+Y",
                    "• Pluggable chart renderers (candles, footprint, bars)",
                    "• Bidasker SaaS — footprint chart web app"
                ).forEach {
                    Text(it, color = Color(0xFFAAAAAA), fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
private fun TemplateCard(title: String, desc: String, emoji: String, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF121212))
            .clickable { onClick() }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(emoji, fontSize = 24.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, color = Color(0xFFE0E0E0), fontSize = 13.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Text(desc, color = Color(0xFF888888), fontSize = 11.sp, fontFamily = FontFamily.Monospace, lineHeight = 16.sp)
    }
}
