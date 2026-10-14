/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens

// Темные карточки для панелей
@Composable
fun TerminalCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val tokens = LocalTerminalDesignTokens.current
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = BorderStroke(tokens.dimensions.borderWidth, MaterialTheme.colorScheme.outline),
        shape = RoundedCornerShape(tokens.cornerRadii.card)
    ) {
        content()
    }
}
