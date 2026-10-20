/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily

/**
 * Единое поле поиска терминала (Workspaces navigator, Market Watch, symbol
 * explorer): лупа, placeholder, Ctrl/⌘K-хинт и × очистки, Esc очищает ввод.
 */
@Composable
fun TerminalSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    shortcutHint: String = "CTRL K",
) {
    val tokens = LocalTerminalDesignTokens.current
    val shape = RoundedCornerShape(tokens.cornerRadii.smallCard)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape,
            )
            .border(tokens.dimensions.borderWidth, tokens.colors.panelBorder, shape)
            .padding(horizontal = tokens.spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchGlyph(color = tokens.colors.label)
        Spacer(Modifier.width(tokens.spacing.sm))
        Box(modifier = Modifier.weight(1f)) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                    fontSize = tokens.typography.compactSize,
                    lineHeight = tokens.typography.controlLineHeight,
                    fontFamily = TerminalFontFamily,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = TextStyle(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = tokens.typography.compactSize,
                    lineHeight = tokens.typography.controlLineHeight,
                    fontFamily = TerminalFontFamily,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            onQueryChange("")
                            true
                        } else {
                            false
                        }
                    },
            )
        }
        Spacer(Modifier.width(tokens.spacing.sm))
        if (query.isEmpty()) {
            Text(
                text = shortcutHint,
                color = tokens.colors.label,
                fontSize = tokens.typography.microSize,
                lineHeight = tokens.typography.compactLineHeight,
                letterSpacing = tokens.typography.compactLetterSpacing,
                fontFamily = TerminalFontFamily,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .clickable { onQueryChange("") },
                contentAlignment = Alignment.Center,
            ) {
                ClearGlyph(color = tokens.colors.label, glyphSize = 8.dp)
            }
        }
    }
}

@Composable
private fun SearchGlyph(color: Color) {
    Canvas(modifier = Modifier.size(12.dp)) {
        val stroke = 1.2.dp.toPx()
        val radius = size.minDimension * 0.32f
        val center = Offset(size.width * 0.4f, size.height * 0.4f)
        drawCircle(color = color, radius = radius, center = center, style = Stroke(stroke))
        drawLine(
            color = color,
            start = Offset(center.x + radius * 0.7f, center.y + radius * 0.7f),
            end = Offset(size.width * 0.92f, size.height * 0.92f),
            strokeWidth = stroke,
        )
    }
}

@Composable
private fun ClearGlyph(color: Color, glyphSize: Dp = 10.dp) {
    Canvas(modifier = Modifier.size(glyphSize)) {
        val stroke = 1.2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(this.size.width * 0.15f, this.size.height * 0.15f),
            end = Offset(this.size.width * 0.85f, this.size.height * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = color,
            start = Offset(this.size.width * 0.85f, this.size.height * 0.15f),
            end = Offset(this.size.width * 0.15f, this.size.height * 0.85f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}
