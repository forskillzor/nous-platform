/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.window

import androidx.compose.ui.awt.ComposeWindow
import java.awt.Color

/**
 * Тёмный фон AWT-окна. Вызывать в `init`-блоке [androidx.compose.ui.awt.v2.SwingWindow]
 * — ДО того, как окно станет displayable: тогда нативный background brush
 * создаётся уже тёмным, и при живом ресайзе AWT заливает вновь открытую
 * область тёмным цветом, а не белым (работает и на Windows, и на Linux).
 */
fun applyWindowDarkBackground(window: ComposeWindow, darkRgb: Int = 0x0A0A0A) {
    val awtColor = Color(darkRgb)
    window.background = awtColor
    window.rootPane.background = awtColor
    window.contentPane.background = awtColor
}
