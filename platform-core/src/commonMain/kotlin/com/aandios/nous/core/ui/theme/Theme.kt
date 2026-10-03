/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Глубокая темная цветовая схема в стиле Bloomberg/TradingView
private val DarkTerminalColorScheme = darkColorScheme(
    // Основные цвета
    primary = Color(0xFF00C853),
    secondary = Color(0xFFD32F2F),
    tertiary = Color(0xFF2196F3),

    // Фоны
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF1E1E1E),
    // Без тонирования elevated Surface в primary (иначе панели зеленеют)
    surfaceTint = Color.Transparent,

    // Текст
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onTertiary = Color.White,
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFCCCCCC),
    onSurfaceVariant = Color(0xFF888888),
    inverseOnSurface = Color(0xFFFFEB00),

    // Состояния
    error = Color(0xFFCF6679),
    outline = Color(0xFF333333),
    outlineVariant = Color(0xFF444444),
)

// Еще более темная схема для ночного режима
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

// Определяем моноширинный шрифт
private val TerminalFontFamily = terminalFontFamily()

// Типографика для трейдингового терминала (исправленная)
private val TerminalTypography = Typography(
    displayLarge = Typography().displayLarge.copy(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Normal
    ),
    displayMedium = Typography().displayMedium.copy(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Normal
    ),
    displaySmall = Typography().displaySmall.copy(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Normal
    ),
    headlineLarge = Typography().headlineLarge.copy(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Medium
    ),
    headlineMedium = Typography().headlineMedium.copy(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Medium
    ),
    headlineSmall = Typography().headlineSmall.copy(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Medium
    ),
    titleLarge = Typography().titleLarge.copy(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Medium
    ),
    titleMedium = Typography().titleMedium.copy(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Medium
    ),
    titleSmall = Typography().titleSmall.copy(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Medium
    ),
    bodyLarge = Typography().bodyLarge.copy(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Normal
    ),
    bodyMedium = Typography().bodyMedium.copy(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Normal
    ),
    bodySmall = Typography().bodySmall.copy(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Normal
    ),
    labelLarge = Typography().labelLarge.copy(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Medium
    ),
    labelMedium = Typography().labelMedium.copy(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Medium
    ),
    labelSmall = Typography().labelSmall.copy(
        fontFamily = TerminalFontFamily,
        fontWeight = FontWeight.Medium
    )
)

// Формы (скругления) - исправляем для десктопа
private val TerminalShapes = Shapes(
    extraSmall = RoundedCornerShape(2.dp),
    small = RoundedCornerShape(4.dp),
    medium = RoundedCornerShape(6.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(12.dp)
)

// Цвета специально для графиков
object ChartColors {
    // Свечи
    val bullish = DarkTerminalColorScheme.primary
    val bearish = DarkTerminalColorScheme.secondary
    val candleShadow = Color(0xFF666666)
    val dojiLine = Color(0xFFAAAAAA)

    // Фон графика
    val chartBackground = Color(0xFF050505) // Чуть темнее основного фона

    // Сетка и оси
    val gridLine = Color(0xFF222222)
    val gridLineMajor = Color(0xFF333333)
    val axisText = Color(0xFF888888)

    // Объем
    val volumeBullish = Color(0x4D00C853) // 30% прозрачности
    val volumeBearish = Color(0x4DD32F2F)

    // Индикаторы
    val sma = Color(0xFF2196F3)
    val ema = Color(0xFFFF9800)
    val ema2 = Color(0xFFFF5722)
    val bollingerUpper = Color(0xFF9C27B0)
    val bollingerLower = Color(0xFF9C27B0)
    val rsi = Color(0xFF00BCD4)
    val macd = Color(0xFFE91E63)
    val signal = Color(0xFF8BC34A)

    // Уровни
    val supportLevel = Color(0xFF4CAF50)
    val resistanceLevel = Color(0xFFF44336)

    // Выделение
    val selection = Color(0x33FFEB3B) // 20% прозрачности
    val crosshair = Color(0x66FFFFFF) // 40% прозрачности
    val cursorLine = Color(0x44FFFFFF)

    // Ордера
    val buyOrder = Color(0xFF00C853)
    val sellOrder = Color(0xFFD32F2F)
    val orderBackground = Color(0x1AFFFFFF)

    // Портфель
    val profit = Color(0xFF00C853)
    val loss = Color(0xFFD32F2F)
    val neutral = Color(0xFF757575)
}

@Composable
fun TradingTerminalTheme(
    darkTheme: Boolean = true, // Всегда темная тема
    nightMode: Boolean = false, // Дополнительный ночной режим
    content: @Composable () -> Unit
) {
    val colorScheme = if (nightMode) NightTerminalColorScheme else DarkTerminalColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = TerminalTypography,
        shapes = TerminalShapes,
        content = content
    )
}
