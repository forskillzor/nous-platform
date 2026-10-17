/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

data class TerminalSemanticColors(
    val bullish: Color,
    val bearish: Color,
    val candleShadow: Color,
    val dojiLine: Color,
    val chartBackground: Color,
    val gridLine: Color,
    val gridLineMajor: Color,
    val axisText: Color,
    val volumeBullish: Color,
    val volumeBearish: Color,
    val selection: Color,
    val crosshair: Color,
    val cursorLine: Color,
    val orderBackground: Color,
    val buyOrder: Color,
    val sellOrder: Color,
    val profit: Color,
    val loss: Color,
    val neutral: Color,
    val sma: Color,
    val ema: Color,
    val ema2: Color,
    val bollinger: Color,
    val rsi: Color,
    val macd: Color,
    val signal: Color,
    val supportLevel: Color,
    val resistanceLevel: Color,
    val workspaceBackground: Color,
    val panelSurface: Color,
    val panelHeaderSurface: Color,
    val panelBorder: Color,
    val inactiveTabSurface: Color,
    val splitHandle: Color,
    val warning: Color,
    val accent: Color,
    val label: Color,
    val pnlPlate: Color,
    val switchTrack: Color,
)

data class TerminalSpacing(
    val none: Dp = 0.dp,
    val xxs: Dp = 2.dp,
    val xs: Dp = 4.dp,
    val sm: Dp = 6.dp,
    val md: Dp = 8.dp,
    val lg: Dp = 12.dp,
    val xl: Dp = 16.dp,
    val xxl: Dp = 24.dp,
)

data class TerminalDimensions(
    val controlHeight: Dp = 32.dp,
    val compactControlHeight: Dp = 20.dp,
    val compactFieldHeight: Dp = 28.dp,
    val toolbarHeight: Dp = 48.dp,
    val borderWidth: Dp = 0.5.dp,
    val dividerThickness: Dp = 1.dp,
    val dropdownWidth: Dp = 120.dp,
    val inlineSelectMenuWidth: Dp = 140.dp,
    val dropdownIconSize: Dp = 16.dp,
    val dropdownLabelSeparatorWidth: Dp = 2.dp,
    val dropdownLabelSeparatorHeight: Dp = 18.dp,
    val contextPopupWidth: Dp = 280.dp,
    val themeSettingsDialogWidth: Dp = 360.dp,
    val tradeFilterMenuWidth: Dp = 200.dp,
    val marketWatchListMenuWidth: Dp = 180.dp,
    val marketWatchAddListMenuWidth: Dp = 170.dp,
    val marketWatchTemplateMenuWidth: Dp = 460.dp,
    val marketWatchTemplateCardWidth: Dp = 124.dp,
    val marketWatchTemplatePreviewHeight: Dp = 68.dp,
    val workspaceTabBarHeight: Dp = 32.dp,
    val workspaceTabHeight: Dp = 28.dp,
    val workspacePanelHeaderHeight: Dp = 30.dp,
    val workspaceTabMaxWidth: Dp = 140.dp,
    val workspaceSidebarWidth: Dp = 48.dp,
    val workspaceToolSize: Dp = 36.dp,
    val workspaceToolIconSize: Dp = 20.dp,
    val splitHandleSize: Dp = 2.dp,
    val resizeGripDotRadius: Dp = 1.dp,
    val resizeGripDotSpacing: Dp = 3.dp,
    val workspaceTabDragEstimate: Dp = 120.dp,
    val workspaceDropIndicatorWidth: Dp = 2.dp,
    val workspaceDropIndicatorHeight: Dp = 24.dp,
    val workspaceDropTargetBand: Dp = 28.dp,
    val workspaceDropPreviewStrip: Dp = 6.dp,
    val chartMinimumWidth: Dp = 200.dp,
    val workspacePanelGap: Dp = 8.dp,
    val workspaceToolPanelWidth: Dp = 200.dp,
    val workspaceToolPanelMinWidth: Dp = 200.dp,
    val workspaceToolPanelMaxWidth: Dp = 300.dp,
    val workspaceBottomPanelInitialHeight: Dp = 300.dp,
    val workspaceBottomPanelMinHeight: Dp = 120.dp,
    val workspaceProjectTreeMinWidth: Dp = 140.dp,
)

data class TerminalTypographyTokens(
    val nanoSize: TextUnit = 8.sp,
    val microSize: TextUnit = 9.sp,
    val smallSize: TextUnit = 10.sp,
    val compactSize: TextUnit = 11.sp,
    val controlSize: TextUnit = 12.sp,
    val compactActionSize: TextUnit = 14.sp,
    val inlineSelectValueSize: TextUnit = 11.sp,
    val bodySize: TextUnit = 13.sp,
    val compactLineHeight: TextUnit = 12.sp,
    val controlLineHeight: TextUnit = 16.sp,
    val controlLetterSpacing: TextUnit = 0.2.sp,
    val compactLetterSpacing: TextUnit = 0.1.sp,
)

data class TerminalCornerRadii(
    val card: Dp = 0.dp,
    val smallCard: Dp = 4.dp,
    val toolbar: Dp = 0.dp,
    val workspacePanel: Dp = 6.dp,
    val workspaceTab: Dp = 4.dp,
)

data class TerminalDesignTokens(
    val colors: TerminalSemanticColors,
    val spacing: TerminalSpacing = TerminalSpacing(),
    val dimensions: TerminalDimensions = TerminalDimensions(),
    val typography: TerminalTypographyTokens = TerminalTypographyTokens(),
    val cornerRadii: TerminalCornerRadii = TerminalCornerRadii(),
)

val LocalTerminalDesignTokens = staticCompositionLocalOf {
    TerminalThemes.Dark.tokens
}
