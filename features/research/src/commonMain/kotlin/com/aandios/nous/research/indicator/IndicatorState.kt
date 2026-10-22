/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

import kotlinx.serialization.Serializable

/** Editable numeric parameter of an indicator definition. */
@Serializable
data class IndicatorParamSpec(
    val key: String,
    val label: String,
    val defaultValue: Double,
    val minValue: Double,
    val maxValue: Double,
    val step: Double = 1.0,
    val integer: Boolean = true,
)

/** Static metadata for one indicator type. */
@Serializable
data class IndicatorDefinition(
    val type: IndicatorType,
    val label: String,
    val placement: IndicatorPlacement,
    val parameters: List<IndicatorParamSpec> = emptyList(),
    val canOverlayInPane: Boolean = placement == IndicatorPlacement.PANE,
)

/**
 * Configured indicator instance. [params] is keyed by [IndicatorParamSpec.key].
 * [lineColors] maps an output line id (see [IndicatorDefinitions.colorTargets])
 * to a user-selected ARGB value; missing entries keep the theme default.
 */
@Serializable
data class IndicatorInstance(
    val id: String,
    val type: IndicatorType,
    val params: Map<String, Double> = emptyMap(),
    val lineColors: Map<String, Long> = emptyMap(),
)

/** How multiple bottom panes are presented. */
@Serializable
enum class IndicatorPaneLayout {
    TABS,
    STACKED,
}

/** One bottom-pane tab. Multiple indicators in [indicators] are overlaid. */
@Serializable
data class IndicatorPaneState(
    val id: String,
    val indicators: List<IndicatorInstance> = emptyList(),
    /** Plot height in dp; the pane header/tabs strip is reserved separately. */
    val heightDp: Float = 80f,
)

const val DEFAULT_INDICATOR_PANE_HEIGHT_DP = 80f

/** Per-chart indicator configuration persisted under the chart panel prefix. */
@Serializable
data class IndicatorState(
    val overlays: List<IndicatorInstance> = emptyList(),
    val panes: List<IndicatorPaneState> = emptyList(),
    val activePaneId: String? = null,
    val paneLayout: IndicatorPaneLayout = IndicatorPaneLayout.TABS,
    /**
     * Shared plot height in dp while [paneLayout] is TABS. Null in legacy state;
     * it then follows the active pane until the first tab resize/switch.
     */
    val tabsHeightDp: Float? = null,
)

/** TABS uses one shared height; STACKED keeps a height per pane. */
fun IndicatorState.effectivePaneHeightDp(paneId: String): Float =
    if (paneLayout == IndicatorPaneLayout.TABS) {
        effectiveTabsHeightDp()
    } else {
        panes.firstOrNull { it.id == paneId }?.heightDp ?: DEFAULT_INDICATOR_PANE_HEIGHT_DP
    }

fun IndicatorState.effectiveTabsHeightDp(): Float =
    tabsHeightDp
        ?: panes.firstOrNull { it.id == activePaneId }?.heightDp
        ?: panes.firstOrNull()?.heightDp
        ?: DEFAULT_INDICATOR_PANE_HEIGHT_DP
