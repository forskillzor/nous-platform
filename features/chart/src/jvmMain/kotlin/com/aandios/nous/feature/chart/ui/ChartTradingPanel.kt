/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Компактная панель настроек chart trading (поверх графика, справа сверху):
 * количество ордера (minQty инструмента / своё значение / слот под
 * computed-манименеджмент) + тумблер подтверждения ордеров + последнее
 * сообщение.
 */
@Composable
fun ChartTradingPanel(
    minQty: Double?,
    quantity: Double?,
    confirmOrders: Boolean,
    lastMessage: String?,
    onQuantityChanged: (Double?) -> Unit,
    onConfirmChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
        horizontalAlignment = Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Qty:", color = Color(0xFFAAAAAA), fontSize = 11.sp, fontFamily = FontFamily.Monospace)

            // minQty инструмента — быстрый выбор
            minQty?.takeIf { it > 0 }?.let { mq ->
                Text(
                    text = "min ${trimZeros(mq)}",
                    color = if (quantity == null) Color(0xFF00C853) else Color(0xFF6B7A88),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .background(
                            if (quantity == null) Color(0xFF00C853).copy(alpha = 0.2f) else Color.Transparent,
                            RoundedCornerShape(3.dp)
                        )
                        .clickableNoIndication { onQuantityChanged(null) }
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }

            // Своё значение
            BasicTextField(
                value = quantity?.let { trimZeros(it) } ?: "",
                onValueChange = { text ->
                    onQuantityChanged(text.toDoubleOrNull()?.takeIf { it > 0 })
                },
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 11.sp, fontFamily = FontFamily.Monospace),
                cursorBrush = SolidColor(Color(0xFF5B9BD5)),
                modifier = Modifier
                    .width(70.dp)
                    .background(Color(0xFF1A1A1A), RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }

        // Подтверждение ордеров
        Text(
            text = if (confirmOrders) "Confirm orders: ON" else "Confirm orders: OFF",
            color = if (confirmOrders) Color(0xFF5B9BD5) else Color(0xFF6B7A88),
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .background(
                    if (confirmOrders) Color(0xFF5B9BD5).copy(alpha = 0.2f) else Color.Transparent,
                    RoundedCornerShape(3.dp)
                )
                .clickableNoIndication { onConfirmChanged(!confirmOrders) }
                .padding(horizontal = 5.dp, vertical = 2.dp),
        )

        lastMessage?.let { msg ->
            Text(
                text = msg,
                color = Color(0xFF00C853),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun trimZeros(v: Double): String {
    var s = v.toString()
    if ('.' in s) {
        s = s.trimEnd('0').trimEnd('.')
    }
    return s
}

@Composable
private fun Modifier.clickableNoIndication(onClick: () -> Unit): Modifier {
    val interaction = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    return this.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    )
}
