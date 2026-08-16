/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.dom.ui

import com.aandios.nous.core.ui.format.SymbolFormatter

private val symFmt = SymbolFormatter.DEFAULT

fun formatDomPrice(price: Double): String = symFmt.formatPrice(price)
