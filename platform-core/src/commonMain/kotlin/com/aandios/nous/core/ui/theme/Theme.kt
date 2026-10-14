/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

data class TerminalThemeDefinition(
    val name: String,
    val colorScheme: ColorScheme,
    val typography: Typography,
    val shapes: Shapes,
    val tokens: TerminalDesignTokens,
)

val TerminalFontFamily = terminalFontFamily()

private val TerminalTypography = Typography().let { defaults ->
    Typography(
        displayLarge = defaults.displayLarge.copy(fontFamily = TerminalFontFamily, fontWeight = FontWeight.Normal),
        displayMedium = defaults.displayMedium.copy(fontFamily = TerminalFontFamily, fontWeight = FontWeight.Normal),
        displaySmall = defaults.displaySmall.copy(fontFamily = TerminalFontFamily, fontWeight = FontWeight.Normal),
        headlineLarge = defaults.headlineLarge.copy(fontFamily = TerminalFontFamily, fontWeight = FontWeight.Medium),
        headlineMedium = defaults.headlineMedium.copy(fontFamily = TerminalFontFamily, fontWeight = FontWeight.Medium),
        headlineSmall = defaults.headlineSmall.copy(fontFamily = TerminalFontFamily, fontWeight = FontWeight.Medium),
        titleLarge = defaults.titleLarge.copy(fontFamily = TerminalFontFamily, fontWeight = FontWeight.Medium),
        titleMedium = defaults.titleMedium.copy(fontFamily = TerminalFontFamily, fontWeight = FontWeight.Medium),
        titleSmall = defaults.titleSmall.copy(fontFamily = TerminalFontFamily, fontWeight = FontWeight.Medium),
        bodyLarge = defaults.bodyLarge.copy(fontFamily = TerminalFontFamily, fontWeight = FontWeight.Normal),
        bodyMedium = defaults.bodyMedium.copy(fontFamily = TerminalFontFamily, fontWeight = FontWeight.Normal),
        bodySmall = defaults.bodySmall.copy(fontFamily = TerminalFontFamily, fontWeight = FontWeight.Normal),
        labelLarge = defaults.labelLarge.copy(fontFamily = TerminalFontFamily, fontWeight = FontWeight.Medium),
        labelMedium = defaults.labelMedium.copy(fontFamily = TerminalFontFamily, fontWeight = FontWeight.Medium),
        labelSmall = defaults.labelSmall.copy(fontFamily = TerminalFontFamily, fontWeight = FontWeight.Medium),
    )
}

private val TerminalShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp),
)

private val DarkTerminalColorScheme = darkColorScheme(
    primary = Color(0xFF00C853),
    secondary = Color(0xFFD32F2F),
    tertiary = Color(0xFF2196F3),
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF1E1E1E),
    surfaceTint = Color.Transparent,
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFCCCCCC),
    onSurfaceVariant = Color(0xFF888888),
    inverseOnSurface = Color(0xFFFFEB00),
    error = Color(0xFFCF6679),
    outline = Color(0xFF333333),
    outlineVariant = Color(0xFF444444),
)

private val NightTerminalColorScheme = darkColorScheme(
    primary = Color(0xFF4CAF50),
    secondary = Color(0xFFF44336),
    tertiary = Color(0xFF64B5F6),
    background = Color(0xFF000000),
    surface = Color(0xFF080808),
    surfaceVariant = Color(0xFF111111),
    surfaceTint = Color.Transparent,
    onBackground = Color(0xFFAAAAAA),
    onSurface = Color(0xFF999999),
    outline = Color(0xFF222222),
)

private fun terminalTokens(colorScheme: ColorScheme) = TerminalDesignTokens(
    colors = TerminalSemanticColors(
        bullish = colorScheme.primary,
        bearish = colorScheme.secondary,
        candleShadow = Color(0xFF666666),
        dojiLine = Color(0xFFAAAAAA),
        chartBackground = Color(0xFF050505),
        gridLine = Color(0xFF222222),
        gridLineMajor = Color(0xFF333333),
        axisText = Color(0xFF888888),
        volumeBullish = Color(0x4D00C853),
        volumeBearish = Color(0x4DD32F2F),
        selection = Color(0x33FFEB3B),
        crosshair = Color(0x66FFFFFF),
        cursorLine = Color(0x44FFFFFF),
        orderBackground = Color(0x1AFFFFFF),
        buyOrder = colorScheme.primary,
        sellOrder = colorScheme.secondary,
        profit = colorScheme.primary,
        loss = colorScheme.secondary,
        neutral = Color(0xFF757575),
        sma = Color(0xFF2196F3),
        ema = Color(0xFFFF9800),
        ema2 = Color(0xFFFF5722),
        bollinger = Color(0xFF9C27B0),
        rsi = Color(0xFF00BCD4),
        macd = Color(0xFFE91E63),
        signal = Color(0xFF8BC34A),
        supportLevel = Color(0xFF4CAF50),
        resistanceLevel = Color(0xFFF44336),
        warning = Color(0xFFE0A95B),
        accent = Color(0xFF5B9BD5),
        label = Color(0xFF8A97A5),
        pnlPlate = Color(0xFF1B222B),
        switchTrack = Color(0xFF00C853).copy(alpha = 0.65f),
    ),
)

object TerminalThemes {
    val Dark = TerminalThemeDefinition(
        name = "Dark",
        colorScheme = DarkTerminalColorScheme,
        typography = TerminalTypography,
        shapes = TerminalShapes,
        tokens = terminalTokens(DarkTerminalColorScheme),
    )

    val Night = TerminalThemeDefinition(
        name = "Night",
        colorScheme = NightTerminalColorScheme,
        typography = TerminalTypography,
        shapes = TerminalShapes,
        tokens = terminalTokens(NightTerminalColorScheme),
    )
}

object ChartColors {
    val bullish = TerminalThemes.Dark.tokens.colors.bullish
    val bearish = TerminalThemes.Dark.tokens.colors.bearish
    val candleShadow = TerminalThemes.Dark.tokens.colors.candleShadow
    val dojiLine = TerminalThemes.Dark.tokens.colors.dojiLine
    val chartBackground = TerminalThemes.Dark.tokens.colors.chartBackground
    val gridLine = TerminalThemes.Dark.tokens.colors.gridLine
    val gridLineMajor = TerminalThemes.Dark.tokens.colors.gridLineMajor
    val axisText = TerminalThemes.Dark.tokens.colors.axisText
    val volumeBullish = TerminalThemes.Dark.tokens.colors.volumeBullish
    val volumeBearish = TerminalThemes.Dark.tokens.colors.volumeBearish
    val sma = TerminalThemes.Dark.tokens.colors.sma
    val ema = TerminalThemes.Dark.tokens.colors.ema
    val ema2 = TerminalThemes.Dark.tokens.colors.ema2
    val bollingerUpper = TerminalThemes.Dark.tokens.colors.bollinger
    val bollingerLower = TerminalThemes.Dark.tokens.colors.bollinger
    val rsi = TerminalThemes.Dark.tokens.colors.rsi
    val macd = TerminalThemes.Dark.tokens.colors.macd
    val signal = TerminalThemes.Dark.tokens.colors.signal
    val supportLevel = TerminalThemes.Dark.tokens.colors.supportLevel
    val resistanceLevel = TerminalThemes.Dark.tokens.colors.resistanceLevel
    val selection = TerminalThemes.Dark.tokens.colors.selection
    val crosshair = TerminalThemes.Dark.tokens.colors.crosshair
    val cursorLine = TerminalThemes.Dark.tokens.colors.cursorLine
    val buyOrder = TerminalThemes.Dark.tokens.colors.buyOrder
    val sellOrder = TerminalThemes.Dark.tokens.colors.sellOrder
    val orderBackground = TerminalThemes.Dark.tokens.colors.orderBackground
    val profit = TerminalThemes.Dark.tokens.colors.profit
    val loss = TerminalThemes.Dark.tokens.colors.loss
    val neutral = TerminalThemes.Dark.tokens.colors.neutral
}

@Composable
fun TradingTerminalTheme(
    darkTheme: Boolean = true, // Всегда темная тема
    nightMode: Boolean = false, // Дополнительный ночной режим
    theme: TerminalThemeDefinition? = null,
    content: @Composable () -> Unit
) {
    val selectedTheme = theme ?: if (nightMode) TerminalThemes.Night else TerminalThemes.Dark

    CompositionLocalProvider(LocalTerminalDesignTokens provides selectedTheme.tokens) {
        MaterialTheme(
            colorScheme = selectedTheme.colorScheme,
            typography = selectedTheme.typography,
            shapes = selectedTheme.shapes,
            content = content,
        )
    }
}
