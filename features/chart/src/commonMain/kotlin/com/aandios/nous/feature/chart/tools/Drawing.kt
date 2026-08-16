/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.tools

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/**
 * Drawing objects placed on the chart by the user.
 * Serialization-ready for workspace persistence.
 */
sealed class Drawing {
    abstract val id: String
    abstract val color: Color
    abstract val createdAt: Long

    /**
     * Trend line connecting two points on the chart.
     */
    data class TrendLine(
        override val id: String,
        val startPrice: Float,
        val startTimeMs: Long,
        val endPrice: Float,
        val endTimeMs: Long,
        override val color: Color = Color(0xFFFFEB00),
        override val createdAt: Long = currentTime(),
        val lineWidth: Float = 1.5f,
        val label: String? = null
    ) : Drawing()

    /**
     * Horizontal price level line.
     */
    data class HorizontalLevel(
        override val id: String,
        val price: Float,
        override val color: Color = Color(0xFF2196F3),
        override val createdAt: Long = currentTime(),
        val lineWidth: Float = 1f,
        val label: String? = null,
        val isDashed: Boolean = false
    ) : Drawing()

    /**
     * Rectangle (e.g., support/resistance zone).
     */
    data class Rectangle(
        override val id: String,
        val topPrice: Float,
        val bottomPrice: Float,
        val startTimeMs: Long,
        val endTimeMs: Long,
        override val color: Color = Color(0x442196F3),
        override val createdAt: Long = currentTime(),
        val borderColor: Color = Color(0xFF2196F3),
        val lineWidth: Float = 1f
    ) : Drawing()

    /**
     * Vertical time marker (e.g., news event line).
     */
    data class VerticalLine(
        override val id: String,
        val timeMs: Long,
        override val color: Color = Color(0xFFFF5722),
        override val createdAt: Long = currentTime(),
        val lineWidth: Float = 1f,
        val label: String? = null
    ) : Drawing()

    companion object {
        fun currentTime(): Long {
            return kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
        }
    }
}

/**
 * Active drawing tool selected by user.
 */
enum class DrawingToolType {
    NONE,       // Cursor / default — no drawing
    TREND_LINE, // Click to place start, click to place end
    HORIZONTAL, // Click at a price level
    RECTANGLE,  // Drag from top-left to bottom-right
    VERTICAL,   // Click at a time position
    RULER       // Measure distance (price + time delta)
}
