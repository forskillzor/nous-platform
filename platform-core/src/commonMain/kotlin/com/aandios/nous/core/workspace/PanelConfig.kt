/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.workspace

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Конфигурация одной панели внутри workspace.
 */
@Serializable
data class PanelConfig(
    val id: String,
    val type: PanelType,
    val providerRef: String,
    val symbol: String,
    val state: PanelState = PanelState.empty()
)

@Serializable
enum class PanelType { CHART, DOM, TRADES }

@Serializable
sealed class PanelState {

    @Serializable @SerialName("chart")
    data class Chart(
        val timeframe: String = "1m",
        val chartMode: String = "CANDLESTICK",
        val zoomLevel: Float = 1f
    ) : PanelState()

    @Serializable @SerialName("dom")
    data class Dom(
        val depth: Int = 20,
        val aggregation: String = "1x",
        val collapsed: Boolean = false
    ) : PanelState()

    @Serializable @SerialName("trades")
    data class Trades(
        val minSize: String? = null,
        val highlightLarge: Boolean = true,
        val sizeFilter: String? = null,
        val customPresets: List<Double> = emptyList()
    ) : PanelState()

    companion object {
        fun empty() = Chart()
    }
}
