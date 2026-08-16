/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.theme.TradingTerminalTheme
import org.jetbrains.compose.ui.tooling.preview.Preview

// ─── SimpleTerminalTextField ─────────────────────────────────────────────────

@Preview
@Composable
internal fun PreviewSimpleTerminalTextField() {
    TradingTerminalTheme {
        Box (modifier = Modifier.fillMaxSize()) {
            var text by remember { mutableStateOf("") }
            SimpleTerminalTextField(
                value = text,
                onValueChange = { text = it },
                label = "Enter value"
            )
        }
    }
}

// ─── TerminalStatusBar ────────────────────────────────────────────────────────

@Preview
@Composable
internal fun PreviewTerminalStatusBar() {
    TradingTerminalTheme {
        TerminalStatusBar(
            connectionStatus = "Connected",
            latency = "12ms"
        )
    }
}

// ─── TerminalBadge ────────────────────────────────────────────────────────────

@Preview
@Composable
internal fun PreviewTerminalBadges() {
    TradingTerminalTheme {
        Column(modifier = Modifier.padding(8.dp)) {
            TerminalBadge(text = "Bullish", isBullish = true)
            Spacer(Modifier.height(4.dp))
            TerminalBadge(text = "Bearish", isBullish = false)
            Spacer(Modifier.height(4.dp))
            TerminalBadge(text = "Neutral", isBullish = null)
        }
    }
}

// ─── TerminalButton ───────────────────────────────────────────────────────────

@Preview
@Composable
internal fun PreviewTerminalButtons() {
    TradingTerminalTheme {
        Column(modifier = Modifier.padding(8.dp)) {
            TerminalButton(onClick = { }, isActive = false) { Text("Inactive") }
            Spacer(Modifier.height(4.dp))
            TerminalButton(onClick = { }, isActive = true) { Text("Active") }
        }
    }
}

// ─── TerminalCard ─────────────────────────────────────────────────────────────

@Preview
@Composable
internal fun PreviewTerminalCard() {
    TradingTerminalTheme {
        TerminalCard(modifier = Modifier.size(200.dp, 100.dp)) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Text("Card Content")
            }
        }
    }
}

// ─── TerminalDivider ──────────────────────────────────────────────────────────

@Preview
@Composable
internal fun PreviewTerminalDivider() {
    TradingTerminalTheme {
        Box(modifier = Modifier.width(200.dp).padding(8.dp)) {
            TerminalDivider()
        }
    }
}

// ─── TerminalDropdown ─────────────────────────────────────────────────────────

@Preview
@Composable
internal fun PreviewTerminalDropdown() {
    TradingTerminalTheme {
        val items = listOf("BTCUSDT", "ETHUSDT", "SOLUSDT")
        var selected by remember { mutableStateOf(items.first()) }

        TerminalDropdown(
            currentValue = selected,
            items = items,
            onValueChanged = { selected = it },
            displayText = { it }
        )
    }
}

// ─── TerminalDropdownWithLabel ────────────────────────────────────────────────

@Preview
@Composable
internal fun PreviewTerminalDropdownWithLabel() {
    TradingTerminalTheme {
        val items = listOf("1m", "5m", "15m", "1h", "4h")
        var selected by remember { mutableStateOf(items.first()) }

        TerminalDropdownWithLabel(label = "Interval") {
            TerminalDropdown(
                currentValue = selected,
                items = items,
                onValueChanged = { selected = it },
                displayText = { it },
                menuWidth = 80.dp
            )
        }
    }
}

// ─── TerminalInfoRow ──────────────────────────────────────────────────────────

@Preview
@Composable
internal fun PreviewTerminalInfoRows() {
    TradingTerminalTheme {
        Column(modifier = Modifier.padding(8.dp).width(250.dp)) {
            TerminalInfoRow(label = "Price", value = "45,678.50", isPositive = true)
            TerminalInfoRow(label = "Change", value = "-2.34%", isPositive = false)
            TerminalInfoRow(label = "Volume", value = "12.5M", isPositive = null)
        }
    }
}

// ─── TerminalPanelTitle ───────────────────────────────────────────────────────

@Preview
@Composable
internal fun PreviewTerminalPanelTitle() {
    TradingTerminalTheme {
        TerminalPanelTitle(text = "Order Book")
    }
}

// ─── TerminalSlider ───────────────────────────────────────────────────────────

@Preview
@Composable
internal fun PreviewTerminalSlider() {
    TradingTerminalTheme {
        var value by remember { mutableStateOf(0.5f) }
        TerminalSlider(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier.width(200.dp).padding(8.dp)
        )
    }
}

// ─── TerminalSwitch ───────────────────────────────────────────────────────────

@Preview
@Composable
internal fun PreviewTerminalSwitch() {
    TradingTerminalTheme {
        var checked by remember { mutableStateOf(false) }
        TerminalSwitch(
            checked = checked,
            onCheckedChange = { checked = it }
        )
    }
}

// ─── TerminalTextField ────────────────────────────────────────────────────────

@Preview
@Composable
internal fun PreviewTerminalTextField() {
    TradingTerminalTheme {
        var text by remember { mutableStateOf("") }
        TerminalTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Search") },
            modifier = Modifier.width(200.dp)
        )
    }
}

// ─── TerminalToolbar ──────────────────────────────────────────────────────────

@Preview
@Composable
internal fun PreviewTerminalToolbar() {
    TradingTerminalTheme {
        TerminalToolbar(modifier = Modifier.width(400.dp)) {
            TerminalButton(onClick = { }, isActive = true) {
                Text("Chart", color = androidx.compose.material3.MaterialTheme.colorScheme.onPrimary)
            }
            TerminalButton(onClick = { }, isActive = false) { Text("Depth") }
            Spacer(Modifier.weight(1f))
            Icon(
                imageVector = Icons.Default.ArrowDropDown,
                contentDescription = "More",
                tint = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
