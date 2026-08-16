/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Рекурсивное дерево сплитов — описывает расположение панелей в workspace.
 * Без ограничения вложенности: можно 12 DOM в гриде или сложные H/V комбинации.
 */
@Serializable
sealed class LayoutNode {

    @Serializable
    @SerialName("split")
    data class Split(
        val direction: Direction,
        var ratio: Float = 0.5f,
        val children: List<LayoutNode>
    ) : LayoutNode()

    @Serializable
    @SerialName("leaf")
    data class Leaf(
        val panelId: String
    ) : LayoutNode()

    @Serializable
    enum class Direction { HORIZONTAL, VERTICAL }
}
