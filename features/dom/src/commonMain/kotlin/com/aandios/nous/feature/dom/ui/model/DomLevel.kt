/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui.model

import androidx.compose.runtime.Immutable

/**
 * Уровень стакана в целочисленных координатах (fixed-point / «матчинг-движок»).
 * Цена и объём хранятся как число минимальных шагов биржи:
 *   цена  = priceTicks * tickSize
 *   объём = steps    * stepSize
 *
 * @see plan: plans/dom-aprove-performance-plan.md §7
 */
@Immutable
data class DomLevel(
    /** Цена как число тиков (tickSize) — целое, точное, стабильный ключ */
    val priceTicks: Long,
    /** Объём bid как число степов (stepSize); 0 = нет bid на этом уровне */
    val bidSteps: Long,
    /** Объём ask как число степов (stepSize); 0 = нет ask на этом уровне */
    val askSteps: Long,
)
