/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.header

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import com.aandios.nous.feature.dom.domain.TradingSymbol

/**
 * Компактное отображение provider и symbol в одну строку без dropdown.
 * Используется в свернутом режиме DomHeader.
 */
@Composable
fun CompactProviderSymbol(
    providerDisplayName: String,
    tradingSymbol: TradingSymbol,
    modifier: Modifier = Modifier.Companion
) {
    val tokens = LocalTerminalDesignTokens.current
    Surface(
        shape = RoundedCornerShape(tokens.cornerRadii.card),
        color = tokens.colors.panelHeaderSurface,
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(tokens.spacing.md),
            modifier = Modifier.padding(horizontal = tokens.spacing.md, vertical = tokens.spacing.xs)
        ) {
            // Provider
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
            ) {
                Text(
                    text = providerDisplayName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = tokens.typography.controlSize,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Medium
                )
            }

            // Разделитель
            Spacer(
                modifier = Modifier
                    .width(tokens.dimensions.dividerThickness)
                    .height(tokens.spacing.xl)
                    .background(tokens.colors.panelBorder)
            )

            // Symbol
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(tokens.spacing.xs)
            ) {
                Text(
                    text = tradingSymbol.displayName,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = tokens.typography.controlSize,
                    fontFamily = TerminalFontFamily,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}