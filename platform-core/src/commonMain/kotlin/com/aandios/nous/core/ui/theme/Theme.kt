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
import androidx.compose.material3.lightColorScheme
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

private val NousTerminalColorScheme = darkColorScheme(
    primary = Color(0xFFF28C18),
    onPrimary = Color(0xFF101722),
    primaryContainer = Color(0xFF4B2D10),
    onPrimaryContainer = Color(0xFFFFD7A6),
    secondary = Color(0xFFD45155),
    onSecondary = Color(0xFF160B0D),
    secondaryContainer = Color(0xFF492126),
    onSecondaryContainer = Color(0xFFFFDADA),
    tertiary = Color(0xFF5E91C7),
    onTertiary = Color(0xFF0B1420),
    background = Color(0xFF0A111C),
    onBackground = Color(0xFFD8E1EE),
    surface = Color(0xFF0F1825),
    onSurface = Color(0xFFD8E1EE),
    surfaceVariant = Color(0xFF172233),
    onSurfaceVariant = Color(0xFF879AB3),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFFD8E1EE),
    inverseOnSurface = Color(0xFF172233),
    error = Color(0xFFD45155),
    onError = Color(0xFF160B0D),
    outline = Color(0xFF29384C),
    outlineVariant = Color(0xFF202E41),
)

/**
 * Светлая тема: белые панели на холодном светло-сером workspace,
 * синий accent (primary) и классические зелёный/красный для buy/sell.
 * Схема — единственный источник accent/selection; точечные поля токенов
 * переопределяются в [TerminalThemes.Light].
 */
private val LightTerminalColorScheme = lightColorScheme(
    primary = Color(0xFF2563EB),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFDBEAFE),
    onPrimaryContainer = Color(0xFF1E3A8A),
    secondary = Color(0xFFDC2626),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFEE2E2),
    onSecondaryContainer = Color(0xFF7F1D1D),
    tertiary = Color(0xFF0891B2),
    onTertiary = Color(0xFFFFFFFF),
    background = Color(0xFFF2F4F8),
    onBackground = Color(0xFF1B2430),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1F2937),
    surfaceVariant = Color(0xFFEDF0F5),
    onSurfaceVariant = Color(0xFF5B6675),
    surfaceTint = Color.Transparent,
    inverseSurface = Color(0xFF1F2937),
    inverseOnSurface = Color(0xFFF9FAFB),
    error = Color(0xFFDC2626),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFEE2E2),
    onErrorContainer = Color(0xFF7F1D1D),
    outline = Color(0xFFC9D2DD),
    outlineVariant = Color(0xFFDCE2EA),
)

private fun terminalTokens(colorScheme: ColorScheme, selection: Color) = TerminalDesignTokens(
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
        selection = selection,
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
        workspaceBackground = colorScheme.background,
        panelSurface = colorScheme.surface,
        panelHeaderSurface = colorScheme.surfaceVariant,
        panelBorder = Color(0xFF222222),
        inactiveTabSurface = Color(0xFF111111),
        splitHandle = Color(0xFF333333),
        warning = Color(0xFFE0A95B),
        accent = colorScheme.primary,
        label = Color(0xFF8A97A5),
        pnlPlate = Color(0xFF1B222B),
        switchTrack = colorScheme.primary.copy(alpha = 0.65f),
        watermark = colorScheme.onBackground.copy(alpha = 0.07f),
    ),
)

object TerminalThemes {
    val Dark = TerminalThemeDefinition(
        name = "Dark",
        colorScheme = DarkTerminalColorScheme,
        typography = TerminalTypography,
        shapes = TerminalShapes,
        tokens = terminalTokens(DarkTerminalColorScheme, selection = Color(0xFF262626)),
    )

    val Night = TerminalThemeDefinition(
        name = "Night",
        colorScheme = NightTerminalColorScheme,
        typography = TerminalTypography,
        shapes = TerminalShapes,
        tokens = terminalTokens(NightTerminalColorScheme, selection = Color(0xFF1B1B1B)),
    )

    val Nous = TerminalThemeDefinition(
        name = "Nous",
        colorScheme = NousTerminalColorScheme,
        typography = TerminalTypography,
        shapes = TerminalShapes,
        tokens = terminalTokens(NousTerminalColorScheme, selection = Color(0xFF1A2940)).let { base ->
            base.copy(
                colors = base.colors.copy(
                    bullish = Color(0xFF168B72),
                    bearish = Color(0xFFD45155),
                    candleShadow = Color(0xFF8A9AAF),
                    chartBackground = Color(0xFF0E1724),
                    gridLine = Color(0xFF223147),
                    gridLineMajor = Color(0xFF2B3B50),
                    axisText = Color(0xFF8295AE),
                    volumeBullish = Color(0x66168B72),
                    volumeBearish = Color(0x66D45155),
                    crosshair = Color(0x80A9BCD0),
                    cursorLine = Color(0x66A9BCD0),
                    orderBackground = Color(0xAA172233),
                    buyOrder = Color(0xFF168B72),
                    sellOrder = Color(0xFFD45155),
                    profit = Color(0xFF168B72),
                    loss = Color(0xFFD45155),
                    neutral = Color(0xFF8295AE),
                    workspaceBackground = Color(0xFF0A111C),
                    panelSurface = Color(0xFF0F1825),
                    panelHeaderSurface = Color(0xFF141F2E),
                    panelBorder = Color(0xFF29384C),
                    inactiveTabSurface = Color(0xFF111A27),
                    splitHandle = Color(0xFF304158),
                    warning = Color(0xFFF28C18),
                    label = Color(0xFF8295AE),
                    pnlPlate = Color(0xFF151F2D),
                ),
                cornerRadii = base.cornerRadii.copy(
                    card = 4.dp,
                    toolbar = 6.dp,
                    workspacePanel = 6.dp,
                ),
            )
        },
    )

    val Light = TerminalThemeDefinition(
        name = "Light",
        colorScheme = LightTerminalColorScheme,
        typography = TerminalTypography,
        shapes = TerminalShapes,
        tokens = terminalTokens(LightTerminalColorScheme, selection = Color(0xFFDCE4EF)).let { base ->
            base.copy(
                colors = base.colors.copy(
                    bullish = Color(0xFF16A34A),
                    bearish = Color(0xFFDC2626),
                    candleShadow = Color(0xFF8E9AAB),
                    dojiLine = Color(0xFF6B7280),
                    chartBackground = Color(0xFFFFFFFF),
                    gridLine = Color(0xFFEEF1F5),
                    gridLineMajor = Color(0xFFE1E6ED),
                    axisText = Color(0xFF64748B),
                    volumeBullish = Color(0x4016A34A),
                    volumeBearish = Color(0x40DC2626),
                    crosshair = Color(0x801F2937),
                    cursorLine = Color(0x401F2937),
                    orderBackground = Color(0x141F2937),
                    buyOrder = Color(0xFF16A34A),
                    sellOrder = Color(0xFFDC2626),
                    profit = Color(0xFF16A34A),
                    loss = Color(0xFFDC2626),
                    neutral = Color(0xFF6B7280),
                    sma = Color(0xFF2563EB),
                    ema = Color(0xFFF59E0B),
                    ema2 = Color(0xFFEA580C),
                    bollinger = Color(0xFF7C3AED),
                    rsi = Color(0xFF0891B2),
                    macd = Color(0xFFDB2777),
                    signal = Color(0xFF65A30D),
                    supportLevel = Color(0xFF16A34A),
                    resistanceLevel = Color(0xFFDC2626),
                    panelBorder = Color(0xFFDFE4EB),
                    inactiveTabSurface = Color(0xFFE7EBF1),
                    splitHandle = Color(0xFFCBD3DD),
                    warning = Color(0xFFB45309),
                    label = Color(0xFF5B6675),
                    pnlPlate = Color(0xFFE9EEF5),
                    watermark = Color(0xFF1B2430).copy(alpha = 0.14f),
                ),
                cornerRadii = base.cornerRadii.copy(
                    card = 4.dp,
                    toolbar = 6.dp,
                    workspacePanel = 6.dp,
                ),
                dimensions = base.dimensions.copy(borderWidth = 1.dp),
            )
        },
    )

    val available = listOf(Nous, Dark, Night, Light)
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
