/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.paper.PaperTrading
import com.aandios.nous.feature.trading.ui.PaperSettingsController
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

private val cardBg = Color(0xFF12181F)
private val borderColor = Color(0xFF3A4550)
private val labelColor = Color(0xFF8A97A5)
private val accent = Color(0xFF5B9BD5)
private val okColor = Color(0xFF26A69A)
private val warnColor = Color(0xFFE0A95B)
private val dangerColor = Color(0xFFEF5350)

/**
 * Окно настроек paper trading (оверлей на корне приложения — Dialog в
 * SwingWindow не рендерится): баланс/пополнение/сбросы. Данные — из общего
 * paper-движка; изменения сразу улетают в UI Trading panel через его потоки.
 */
@Composable
fun PaperSettingsOverlay() {
    val controller: PaperSettingsController = koinInject()
    val visible by controller.visible.collectAsState()
    if (!visible) return

    val adapter = PaperTrading.adapter
    val balances by adapter.balancesFlow.collectAsState()
    val scope = rememberCoroutineScope()
    var amountText by remember { mutableStateOf("1000") }
    var status by remember { mutableStateOf<String?>(null) }

    val usdt = balances.firstOrNull { it.currency == "USDT" }
    val amount = amountText.toDoubleOrNull()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.6f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { controller.close() },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .width(400.dp)
                .background(cardBg, RoundedCornerShape(8.dp))
                .border(1.dp, borderColor, RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* клики внутри карточки не закрывают окно */ }
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Paper trading settings",
                    color = Color(0xFFD5DBE1),
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "✕",
                    color = labelColor,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .clickable { controller.close() }
                        .padding(horizontal = 4.dp),
                )
            }

            Text(
                text = "USDT  available ${usdt?.amount ?: "0"}   margin ${usdt?.margin ?: "0"}   equity ${usdt?.equity ?: "0"}",
                color = labelColor,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Amount", color = labelColor, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                BasicTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(accent),
                    modifier = Modifier
                        .weight(1f)
                        .height(24.dp)
                        .background(Color(0xFF0D1117), RoundedCornerShape(3.dp))
                        .border(1.dp, borderColor, RoundedCornerShape(3.dp))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionChip("Top up", okColor, enabled = amount != null) {
                    amount?.let {
                        scope.launch {
                            adapter.topUp("USDT", it)
                            status = "Top up +$it USDT"
                        }
                    }
                }
                ActionChip("Set balance", accent, enabled = amount != null) {
                    amount?.let {
                        scope.launch {
                            adapter.setBalance("USDT", it)
                            status = "Balance set to $it USDT"
                        }
                    }
                }
                ActionChip("Withdraw", warnColor, enabled = amount != null) {
                    amount?.let {
                        scope.launch {
                            adapter.topUp("USDT", -it)
                            status = "Withdrawn $it USDT"
                        }
                    }
                }
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(borderColor))

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionChip("Reset balance", warnColor, enabled = true) {
                    scope.launch {
                        adapter.resetBalance(10_000.0)
                        status = "Balance reset (10000 USDT), positions closed"
                    }
                }
                ActionChip("Reset history", warnColor, enabled = true) {
                    scope.launch {
                        adapter.resetHistory()
                        status = "Trade history cleared"
                    }
                }
                ActionChip("Reset all", dangerColor, enabled = true) {
                    scope.launch {
                        adapter.reset(10_000.0)
                        status = "Full paper reset (10000 USDT)"
                    }
                }
            }

            status?.let {
                Text(
                    text = it,
                    color = okColor,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Text(
                text = "Reset balance closes positions/cancels orders but keeps history. " +
                    "Fees come from the active exchange when available.",
                color = Color(0xFF5A6674),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun ActionChip(
    label: String,
    color: Color,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        color = if (enabled) color else color.copy(alpha = 0.35f),
        fontSize = 11.sp,
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        modifier = Modifier
            .background(color.copy(alpha = if (enabled) 0.12f else 0.04f), RoundedCornerShape(3.dp))
            .border(1.dp, color.copy(alpha = if (enabled) 0.55f else 0.2f), RoundedCornerShape(3.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}
