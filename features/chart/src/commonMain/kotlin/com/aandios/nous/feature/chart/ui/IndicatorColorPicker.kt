/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.core.ui.component.TerminalDropdownMenu
import com.aandios.nous.core.ui.component.TerminalMenuItem
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import com.aandios.nous.core.ui.theme.TerminalSemanticColors

internal fun indicatorColorArgb(color: Color): Long = color.toArgb().toLong() and 0xFFFFFFFFL

internal fun indicatorColorFromArgb(argb: Long): Color = Color(argb and 0xFFFFFFFFL)

internal fun indicatorColorHex(argb: Long): String =
    "#" + (argb and 0xFFFFFFL).toString(16).uppercase().padStart(6, '0')

internal fun parseIndicatorColorArgb(raw: String): Long? {
    val text = raw.trim().removePrefix("#")
    if (text.length != 6 && text.length != 8) return null
    val value = text.toLongOrNull(16) ?: return null
    return (if (text.length == 6) value or 0xFF000000L else value) and 0xFFFFFFFFL
}

private fun indicatorPalette(colors: TerminalSemanticColors): List<Color> = listOf(
    colors.accent,
    colors.sma,
    colors.ema,
    colors.bollinger,
    colors.bullish,
    colors.bearish,
    colors.warning,
    Color(0xFFFFFFFF),
    Color(0xFF8A97A5),
    Color(0xFFFFD54F),
    Color(0xFFFF9800),
    Color(0xFFF44336),
    Color(0xFFE91E63),
    Color(0xFF9C27B0),
    Color(0xFF3F51B5),
    Color(0xFF2196F3),
    Color(0xFF00BCD4),
    Color(0xFF4CAF50),
)

/** One line color setting: label + current swatch, opening a palette/custom picker. */
@Composable
internal fun IndicatorColorRow(
    label: String,
    color: Color,
    custom: Boolean,
    onColorSelected: (Long?) -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    var expanded by remember { mutableStateOf(false) }
    val currentArgb = indicatorColorArgb(color)

    Box {
        TerminalMenuItem(onClick = { expanded = true }) {
            Text(
                text = label,
                color = tokens.colors.label,
                fontSize = 9.sp,
                lineHeight = 16.sp,
                fontFamily = TerminalFontFamily,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (custom) "CUSTOM" else "THEME",
                color = tokens.colors.label.copy(alpha = 0.75f),
                fontSize = 8.sp,
                lineHeight = 12.sp,
                fontFamily = TerminalFontFamily,
                modifier = Modifier.padding(horizontal = 6.dp),
            )
            Box(
                Modifier
                    .size(14.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
                    .border(
                        tokens.dimensions.borderWidth,
                        tokens.colors.panelBorder,
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
        TerminalDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(276.dp),
        ) {
            Text(
                text = "PALETTE",
                color = tokens.colors.label,
                fontSize = 8.sp,
                lineHeight = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = TerminalFontFamily,
                modifier = Modifier.padding(
                    start = tokens.spacing.md,
                    top = tokens.spacing.sm,
                    bottom = tokens.spacing.xs,
                ),
            )
            val palette = remember(tokens.colors) { indicatorPalette(tokens.colors) }
            palette.chunked(6).forEach { rowColors ->
                Row(
                    modifier = Modifier.padding(horizontal = tokens.spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    rowColors.forEach { swatch ->
                        val selected = indicatorColorArgb(swatch) == currentArgb
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(swatch)
                                .border(
                                    if (selected) 2.dp else 0.5.dp,
                                    if (selected) tokens.colors.accent else tokens.colors.panelBorder,
                                    RoundedCornerShape(4.dp),
                                )
                                .clickable {
                                    onColorSelected(indicatorColorArgb(swatch))
                                    expanded = false
                                },
                        )
                    }
                }
            }
            TerminalMenuItem(
                onClick = {
                    onColorSelected(null)
                    expanded = false
                },
            ) {
                Text(
                    text = "THEME DEFAULT",
                    color = tokens.colors.label,
                    fontSize = 9.sp,
                    lineHeight = 12.sp,
                    fontFamily = TerminalFontFamily,
                )
            }
            IndicatorCustomColorSection(
                initial = color,
                onApply = { argb ->
                    onColorSelected(argb)
                    expanded = false
                },
            )
        }
    }
}

@Composable
private fun IndicatorCustomColorSection(
    initial: Color,
    onApply: (Long) -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    var red by remember(initial) { mutableStateOf(initial.red) }
    var green by remember(initial) { mutableStateOf(initial.green) }
    var blue by remember(initial) { mutableStateOf(initial.blue) }
    var hex by remember(initial) { mutableStateOf(indicatorColorHex(indicatorColorArgb(initial))) }
    val preview = Color(red, green, blue)

    fun syncHex() {
        hex = indicatorColorHex(indicatorColorArgb(Color(red, green, blue)))
    }

    Text(
        text = "CUSTOM",
        color = tokens.colors.label,
        fontSize = 8.sp,
        lineHeight = 10.sp,
        fontWeight = FontWeight.Bold,
        fontFamily = TerminalFontFamily,
        modifier = Modifier.padding(
            start = tokens.spacing.md,
            top = tokens.spacing.sm,
            bottom = tokens.spacing.xs,
        ),
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(preview)
                .border(
                    tokens.dimensions.borderWidth,
                    tokens.colors.panelBorder,
                    RoundedCornerShape(3.dp),
                ),
        )
        BasicTextField(
            value = hex,
            onValueChange = { raw ->
                hex = raw
                parseIndicatorColorArgb(raw)?.let { argb ->
                    val parsed = indicatorColorFromArgb(argb)
                    red = parsed.red
                    green = parsed.green
                    blue = parsed.blue
                }
            },
            singleLine = true,
            textStyle = TextStyle(
                color = tokens.colors.accent,
                fontSize = 10.sp,
                lineHeight = 12.sp,
                fontFamily = TerminalFontFamily,
            ),
            cursorBrush = SolidColor(tokens.colors.accent),
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(3.dp))
                .background(tokens.colors.selection)
                .padding(horizontal = 6.dp, vertical = 3.dp),
        )
        Text(
            text = "APPLY",
            color = tokens.colors.accent,
            fontSize = 9.sp,
            fontFamily = TerminalFontFamily,
            modifier = Modifier
                .clip(RoundedCornerShape(3.dp))
                .clickable {
                    onApply(parseIndicatorColorArgb(hex) ?: indicatorColorArgb(preview))
                }
                .padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
    IndicatorChannelSlider("R", red) {
        red = it
        syncHex()
    }
    IndicatorChannelSlider("G", green) {
        green = it
        syncHex()
    }
    IndicatorChannelSlider("B", blue) {
        blue = it
        syncHex()
    }
    Spacer(Modifier.height(tokens.spacing.xs))
}

@Composable
private fun IndicatorChannelSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
) {
    val tokens = LocalTerminalDesignTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = tokens.spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = tokens.colors.label,
            fontSize = 8.sp,
            fontFamily = TerminalFontFamily,
            modifier = Modifier.width(12.dp),
        )
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = tokens.colors.accent,
                activeTrackColor = tokens.colors.accent,
                inactiveTrackColor = tokens.colors.selection,
            ),
            modifier = Modifier
                .weight(1f)
                .height(24.dp),
        )
    }
}
