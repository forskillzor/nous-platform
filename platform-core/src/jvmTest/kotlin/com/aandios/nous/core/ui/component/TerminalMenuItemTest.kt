/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Density
import com.aandios.nous.core.ui.theme.TerminalThemes
import com.aandios.nous.core.ui.theme.TradingTerminalTheme
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

/**
 * Регрессия единой строки меню: высота 26dp (как у строк списка workspaces)
 * и акцентная полоска 2dp слева только у выбранного пункта (стиль explorer).
 */
@OptIn(ExperimentalComposeUiApi::class)
class TerminalMenuItemTest {

    @Test
    fun selectedItemIsCompactAndHasAccentBar() {
        val width = 300
        val height = 100
        val theme = TerminalThemes.Dark
        val accent = theme.tokens.colors.accent.toArgb()
        val selection = theme.tokens.colors.selection.toArgb()
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            TradingTerminalTheme(theme = theme) {
                Column(Modifier.fillMaxSize()) {
                    TerminalMenuItem(text = "VOLUME", selected = true, onClick = {})
                    TerminalMenuItem(text = "PRICE", selected = false, onClick = {})
                }
            }
        }
        val png = scene.render().encodeToData()!!.bytes
        scene.close()
        val bi = ImageIO.read(ByteArrayInputStream(png))

        var barRows = 0
        for (y in 0 until height) {
            if (bi.getRGB(0, y) == accent && bi.getRGB(1, y) == accent) barRows++
        }
        assertEquals(26, barRows, "accent bar must span the full 26dp menu row")
        assertEquals(
            selection,
            bi.getRGB(width - 1, 13),
            "selected row must fill with the selection background",
        )
        assertNotEquals(
            accent,
            bi.getRGB(0, 39),
            "unselected row must not draw the accent bar",
        )
    }
}
