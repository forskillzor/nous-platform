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
 * Уровень ОДНОСТОРОННИЙ: либо bidSteps, либо askSteps (другое — null).
 * Правило: апдейт одной стороны затирает другую на той же цене —
 * меньше записей в памяти и не бывает «bid и ask на одной цене».
 */
@Immutable
data class DomLevel(
    /** Цена как число тиков (tickSize) — целое, точное, стабильный ключ */
    val priceTicks: Long,
    /** Объём bid как число степов (stepSize); null = нет bid на этом уровне */
    val bidSteps: Long? = null,
    /** Объём ask как число степов (stepSize); null = нет ask на этом уровне */
    val askSteps: Long? = null,
)
