/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.aandios.nous.api.market.model.SymbolInfo

/**
 * Водяной знак панели как на графике: крупный тикер, ниже — биржа и тип
 * контракта, всё в столбик, очень прозрачно (нижний слой, не перехватывает
 * клики). Размеры шрифта — как у водяного знака chart. Вызывающий центрует
 * блок относительно контент-области (Modifier.align(Alignment.Center)).
 */
@Composable
fun PanelSymbolWatermark(
    symbolInfo: SymbolInfo?,
    currentSymbol: String,
    exchange: String?,
    modifier: Modifier = Modifier,
    symbolFontSize: TextUnit = 72.sp,
    infoFontSize: TextUnit = 26.sp,
) {
    val contractLabel = symbolInfo
        ?.takeIf { it.contractType == "PERPETUAL" }
        ?.let { "${it.marginAsset ?: it.quoteAsset}-M Perp" }
    PanelTextWatermark(
        bigText = symbolInfo?.baseAsset ?: currentSymbol,
        subTexts = listOfNotNull(exchange?.takeIf { it.isNotBlank() }, contractLabel),
        modifier = modifier,
        bigFontSize = symbolFontSize,
        subFontSize = infoFontSize,
    )
}

/**
 * Водяной знак из строк: крупное имя + подписи под ним (столбиком),
 * альфа 7%, нижний слой. Вызывающий центрует блок (align(Alignment.Center)).
 * Для Trading panel: «MEXC» + «USDT-M».
 */
@Composable
fun PanelTextWatermark(
    bigText: String,
    subTexts: List<String>,
    modifier: Modifier = Modifier,
    bigFontSize: TextUnit = 72.sp,
    subFontSize: TextUnit = 26.sp,
) {
    val color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.07f)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = bigText,
            color = color,
            fontSize = bigFontSize,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
        subTexts.forEach { line ->
            Text(
                text = line,
                color = color,
                fontSize = subFontSize,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
    }
}
