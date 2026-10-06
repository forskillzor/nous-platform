/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.model.SymbolInfo

/**
 * Водяной знак панели как на графике: крупный тикер, ниже — биржа и тип
 * контракта, всё в столбик, очень прозрачно (нижний слой, не перехватывает
 * клики). Для DOM/Trades.
 */
@Composable
fun PanelSymbolWatermark(
    symbolInfo: SymbolInfo?,
    currentSymbol: String,
    exchange: String?,
    modifier: Modifier = Modifier,
    symbolFontSize: TextUnit = 44.sp,
    infoFontSize: TextUnit = 16.sp,
) {
    val color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)
    Column(modifier = modifier) {
        Text(
            text = symbolInfo?.baseAsset ?: currentSymbol,
            color = color,
            fontSize = symbolFontSize,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
        if (!exchange.isNullOrBlank()) {
            Text(
                text = exchange,
                color = color,
                fontSize = infoFontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        val contractLabel = symbolInfo
            ?.takeIf { it.contractType == "PERPETUAL" }
            ?.let { "${it.marginAsset ?: it.quoteAsset}-M Perp" }
        if (contractLabel != null) {
            Text(
                text = contractLabel,
                color = color,
                fontSize = infoFontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
    }
}
