/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.ui.component

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.aandios.nous.core.ui.theme.TerminalThemes
import com.aandios.nous.core.ui.theme.TradingTerminalTheme
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Регрессия высоты закрытого триггера explorer-дропдауна: подпись биржи
 * должна иметь явный lineHeight, иначе строка наследует высокий line height
 * Material-стиля и кнопка становится выше соседних «коробок» (28px вместо
 * 20px при density 1). Проверяем попиксельным рендером без окна.
 */
@OptIn(ExperimentalComposeUiApi::class)
class TerminalBoxDropdownTriggerHeightTest {
    private data class Run(val width: Int, val height: Int)

    private fun renderRuns(content: @Composable () -> Unit): List<Run> {
        val width = 700
        val height = 80
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            TradingTerminalTheme(theme = TerminalThemes.Dark) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxSize()) {
                    content()
                }
            }
        }
        val png = scene.render().encodeToData()!!.bytes
        scene.close()
        val bi = ImageIO.read(ByteArrayInputStream(png))
        val tops = IntArray(width) { -1 }
        val bottoms = IntArray(width) { -1 }
        for (x in 0 until width) {
            for (y in 0 until height) {
                if ((bi.getRGB(x, y) ushr 24) > 0) {
                    if (tops[x] == -1) tops[x] = y
                    bottoms[x] = y
                }
            }
        }
        val runs = mutableListOf<Run>()
        var runStart = -1
        for (x in 0..width) {
            val filled = x < width && tops[x] >= 0
            if (filled && runStart == -1) runStart = x
            if (!filled && runStart != -1) {
                var top = Int.MAX_VALUE
                var bottom = -1
                for (cx in runStart until x) {
                    top = minOf(top, tops[cx])
                    bottom = maxOf(bottom, bottoms[cx])
                }
                runs += Run(width = x - runStart, height = bottom - top + 1)
                runStart = -1
            }
        }
        return runs
    }

    @Test
    fun explorerTriggerMatchesPlainBoxHeight() {
        val runs = renderRuns {
            SymbolExplorerDropdown(
                currentSymbol = "SOLUSDT",
                currentExchangeId = "binance",
                rows = listOf(
                    SymbolExplorerRow(
                        symbol = "SOLUSDT",
                        displaySymbol = "SOLUSDT.P",
                        exchangeId = "binance",
                        exchangeLabel = "BINANCE",
                        isPerpetual = true,
                        quoteAsset = "USDT",
                        tickSize = 0.01,
                        lastPrice = 100.0,
                        changePercent24h = 0.0,
                        quoteVolume24h = 1.0,
                    ),
                ),
                onSelect = {},
            )
            Spacer(Modifier.width(20.dp))
            TerminalBoxDropdown(
                current = "Candles",
                options = listOf("Candles"),
                optionLabel = { it },
                onSelect = {},
                style = TerminalBoxStyle.ACCENT,
                menuWidth = 130.dp,
            )
        }
        assertEquals(2, runs.size, "expected explorer trigger and plain box runs: $runs")
        println("TRIGGER-HEIGHTS explorer=${runs[0].height} plain=${runs[1].height}")
        assertEquals(
            runs[1].height,
            runs[0].height,
            "explorer trigger must match plain box height: $runs",
        )
    }
}
