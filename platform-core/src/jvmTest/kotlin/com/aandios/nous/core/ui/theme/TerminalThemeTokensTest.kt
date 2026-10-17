/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Инварианты цветовой системы тем: accent всегда равен primary темы,
 * switchTrack — primary с alpha 0.65, selection — непрозрачная нейтральная
 * элевация (раньше Dark/Night делили жёлтый `0x33FFEB3B`).
 */
class TerminalThemeTokensTest {
    @Test
    fun `accent is the theme primary in every theme`() {
        TerminalThemes.available.forEach { theme ->
            assertEquals(
                theme.colorScheme.primary,
                theme.tokens.colors.accent,
                "accent must equal colorScheme.primary in ${theme.name}",
            )
        }
    }

    @Test
    fun `switch track is the theme primary at 65 percent alpha`() {
        TerminalThemes.available.forEach { theme ->
            assertEquals(
                theme.colorScheme.primary.copy(alpha = 0.65f),
                theme.tokens.colors.switchTrack,
                "switchTrack must derive from colorScheme.primary in ${theme.name}",
            )
        }
    }

    @Test
    fun `selection is an opaque neutral elevation per theme`() {
        val expected = mapOf(
            "Dark" to Color(0xFF262626),
            "Night" to Color(0xFF1B1B1B),
            "Nous" to Color(0xFF1A2940),
            "Light" to Color(0xFFDCE4EF),
        )

        TerminalThemes.available.forEach { theme ->
            val selection = theme.tokens.colors.selection
            assertEquals(expected.getValue(theme.name), selection, "selection in ${theme.name}")
            assertTrue(selection.alpha == 1f, "selection must be opaque in ${theme.name}")
        }
    }

    @Test
    fun `watermark is a faint tint, stronger in Light`() {
        val expectedAlpha = mapOf(
            "Dark" to 0.07f,
            "Night" to 0.07f,
            "Nous" to 0.07f,
            "Light" to 0.14f,
        )

        TerminalThemes.available.forEach { theme ->
            val expected = expectedAlpha.getValue(theme.name)
            val actual = theme.tokens.colors.watermark.alpha
            assertTrue(
                kotlin.math.abs(actual - expected) < 0.01f,
                "watermark alpha in ${theme.name}: expected ~$expected but was $actual",
            )
        }
    }

    @Test
    fun `Light uses 1dp borders, dark themes keep half-dp`() {
        TerminalThemes.available.forEach { theme ->
            val expected = if (theme.name == "Light") 1.dp else 0.5.dp
            assertEquals(
                expected,
                theme.tokens.dimensions.borderWidth,
                "borderWidth in ${theme.name}",
            )
        }
    }
}
