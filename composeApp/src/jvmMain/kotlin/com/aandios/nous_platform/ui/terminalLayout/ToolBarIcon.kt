/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.ui.terminalLayout

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

// Остальные функции остаются без изменений...
@Composable
fun ToolBarIcon(
    icon: DrawableResource,
    description: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.Companion
) {
    val tokens = LocalTerminalDesignTokens.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .size(tokens.dimensions.workspaceToolSize)
            .clip(RoundedCornerShape(tokens.cornerRadii.workspaceTab))
            .background(
                color = when {
                    isSelected -> tokens.colors.accent
                    isHovered -> tokens.colors.panelHeaderSurface
                    else -> Color.Companion.Transparent
                }
            )
            .hoverable(interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Companion.Center
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = description,
            modifier = Modifier.Companion.size(tokens.dimensions.workspaceToolIconSize),
            tint = if (isSelected) tokens.colors.workspaceBackground
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}