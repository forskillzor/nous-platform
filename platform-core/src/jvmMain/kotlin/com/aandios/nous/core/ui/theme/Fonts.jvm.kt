/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.theme

import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.platform.Font

actual fun terminalFontFamily(): FontFamily = FontFamily(
    Font("fonts/JetBrainsMono-Regular.ttf", FontWeight.Normal),
    Font("fonts/JetBrainsMono-Medium.ttf", FontWeight.Medium),
    Font("fonts/JetBrainsMono-Bold.ttf", FontWeight.Bold),
)
