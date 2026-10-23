/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.ui.chart

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aandios.nous.core.ui.theme.LocalTerminalDesignTokens
import com.aandios.nous.core.ui.theme.TerminalFontFamily
import com.aandios.nous.research.indicator.IndicatorInstance
import com.aandios.nous.research.indicator.IndicatorLineStyle
import com.aandios.nous.research.indicator.IndicatorOutput
import com.aandios.nous.research.indicator.indicatorDefinition

/** Compact pane legend: one chip per rendered line, colors match the renderer. */
@Composable
fun IndicatorPaneHeader(
    outputs: List<IndicatorOutput>,
    instances: List<IndicatorInstance>,
    colors: IndicatorChartColors,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalTerminalDesignTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(start = 4.dp, end = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        outputs.forEach { output ->
            output.lines.forEach { line ->
                val color = when {
                    line.colorArgb != null -> Color(line.colorArgb!!)
                    line.style == IndicatorLineStyle.HISTOGRAM -> colors.neutral
                    else -> colors.color(line.colorRole)
                }
                IndicatorLegendChip(
                    label = line.label.ifBlank { indicatorDefinition(output.type).label },
                    color = color,
                    tokens = tokens,
                )
            }
        }
        val rendered = outputs.filter { it.lines.isNotEmpty() }
            .mapTo(mutableSetOf()) { it.instanceId }
        instances.filter { it.id !in rendered }.forEach { instance ->
            IndicatorLegendChip(
                label = indicatorDefinition(instance.type).label,
                color = colors.neutral,
                tokens = tokens,
            )
        }
    }
}

@Composable
private fun IndicatorLegendChip(
    label: String,
    color: Color,
    tokens: com.aandios.nous.core.ui.theme.TerminalDesignTokens,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            Modifier
                .width(8.dp)
                .height(2.dp)
                .background(color),
        )
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.Medium,
            fontFamily = TerminalFontFamily,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
