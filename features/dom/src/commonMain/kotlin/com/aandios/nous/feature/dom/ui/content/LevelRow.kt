/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.content

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.core.ui.format.SymbolFormatter
import com.aandios.nous.feature.dom.ui.model.DomLevel

/**
 * Строка ценовой лесенки. `level == null` — пустой уровень (только цена):
 * лесенка рендерит сплошную ось цен, объёмы появляются на своих строках.
 */
@Composable
fun LevelRow(
    priceTicks: Long,
    level: DomLevel?,
    maxSteps: Long,
    selectedDisplayTicks: Long?,
    lastPriceDisplayTicks: Long?,
    tickSize: Double,
    stepSize: Double,
    formatter: SymbolFormatter,
    onPriceClick: (Long, Double) -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val isSelected = selectedDisplayTicks?.let { it == priceTicks } ?: false
    val isLastPrice = lastPriceDisplayTicks?.let { it == priceTicks } ?: false

    val price = priceTicks * tickSize
    val bidQty = level?.bidSteps?.let { it * stepSize }
    val askQty = level?.askSteps?.let { it * stepSize }

    val backgroundColor = when {
        isSelected -> Color.Yellow.copy(alpha = 0.3f)
        isLastPrice -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
        isHovered -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    val priceColor = when {
        isSelected -> MaterialTheme.colorScheme.onSurface
        isLastPrice -> MaterialTheme.colorScheme.tertiary
        level == null -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
        else -> Color.White
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onPriceClick(priceTicks, price) }
            .background(backgroundColor)
            .padding(horizontal = 8.dp, vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bid Volume
        Box(
            modifier = Modifier.weight(0.8f).height(20.dp)
        ) {
            val bidSteps = level?.bidSteps ?: 0L
            if (bidSteps > 0) {
                val volumeWidth = if (maxSteps > 0) (bidSteps.toFloat() / maxSteps.toFloat()).coerceIn(0f, 1f) else 0f
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(volumeWidth)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
                )
            }
            if (bidSteps > 0 && bidQty != null) {
                Text(
                    text = formatter.formatVolumeFull(bidQty),
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.align(Alignment.CenterStart)
                )
            }
        }

        // Price
        Text(
            text = formatter.formatPrice(price),
            color = priceColor,
            style = MaterialTheme.typography.bodySmall.copy(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            ),
            modifier = Modifier.weight(0.6f)
        )

        // Ask Volume
        Box(
            modifier = Modifier.weight(0.8f).height(20.dp)
        ) {
            val askSteps = level?.askSteps ?: 0L
            if (askSteps > 0) {
                val volumeWidth = if (maxSteps > 0) (askSteps.toFloat() / maxSteps.toFloat()).coerceIn(0f, 1f) else 0f
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(volumeWidth)
                        .align(Alignment.CenterEnd)
                        .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.3f))
                )
            }
            if (askSteps > 0 && askQty != null) {
                Text(
                    text = formatter.formatVolumeFull(askQty),
                    color = MaterialTheme.colorScheme.secondary,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}
