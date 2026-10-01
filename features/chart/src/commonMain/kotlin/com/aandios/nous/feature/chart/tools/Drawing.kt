/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.chart.tools

import androidx.compose.ui.graphics.Color
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlin.time.Clock

/** Сериализация Compose Color как ARGB Long (для персистента рисунков). */
object ColorSerializer : KSerializer<Color> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Color", PrimitiveKind.LONG)

    override fun serialize(encoder: Encoder, value: Color) {
        encoder.encodeLong(value.value.toLong())
    }

    override fun deserialize(decoder: Decoder): Color = Color(decoder.decodeLong().toULong())
}

/**
 * Drawing objects placed on the chart by the user.
 * Сериализуются для персистента на диск (workspace + panel).
 */
@Serializable
sealed class Drawing {
    abstract val id: String
    abstract val color: Color
    abstract val createdAt: Long

    /**
     * Trend line connecting two points on the chart.
     */
    @Serializable
    data class TrendLine(
        override val id: String,
        val startPrice: Float,
        val startTimeMs: Long,
        val endPrice: Float,
        val endTimeMs: Long,
        @Serializable(with = ColorSerializer::class)
        override val color: Color = Color(0xFFFFEB00),
        override val createdAt: Long = currentTime(),
        val lineWidth: Float = 1.5f,
        val label: String? = null
    ) : Drawing()

    /**
     * Horizontal price level line.
     */
    @Serializable
    data class HorizontalLevel(
        override val id: String,
        val price: Float,
        @Serializable(with = ColorSerializer::class)
        override val color: Color = Color(0xFF2196F3),
        override val createdAt: Long = currentTime(),
        val lineWidth: Float = 1f,
        val label: String? = null,
        val isDashed: Boolean = false
    ) : Drawing()

    /**
     * Rectangle (e.g., support/resistance zone).
     */
    @Serializable
    data class Rectangle(
        override val id: String,
        val topPrice: Float,
        val bottomPrice: Float,
        val startTimeMs: Long,
        val endTimeMs: Long,
        @Serializable(with = ColorSerializer::class)
        override val color: Color = Color(0x442196F3),
        override val createdAt: Long = currentTime(),
        @Serializable(with = ColorSerializer::class)
        val borderColor: Color = Color(0xFF2196F3),
        val lineWidth: Float = 1f
    ) : Drawing()

    /**
     * Vertical time marker (e.g., news event line).
     */
    @Serializable
    data class VerticalLine(
        override val id: String,
        val timeMs: Long,
        @Serializable(with = ColorSerializer::class)
        override val color: Color = Color(0xFFFF5722),
        override val createdAt: Long = currentTime(),
        val lineWidth: Float = 1f,
        val label: String? = null
    ) : Drawing()

    companion object {
        fun currentTime(): Long {
            return Clock.System.now().toEpochMilliseconds()
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
