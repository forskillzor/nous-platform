/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous_platform.utils

import com.aandios.nous.core.ui.format.SymbolFormatter
import java.text.SimpleDateFormat
import java.util.Date

fun formatPrice(price: Float, formatter: SymbolFormatter = SymbolFormatter.DEFAULT): String {
    return formatter.formatPrice(price)
}

fun formatTime(timestamp: Long): String {
    val date = Date(timestamp)
    val formatter = SimpleDateFormat("HH:mm")
    return formatter.format(date)
}
