/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.research.indicator

import com.aandios.nous.api.market.model.Candle

/**
 * Index of the candle whose bucket contains [timestamp]: the last candle with
 * `timestamp <= t`. Returns null before the first candle, and the last index
 * for timestamps at or after it.
 */
fun List<Candle>.indexOfBucket(timestamp: Long, timeframeMillis: Long): Int? {
    if (isEmpty() || timeframeMillis <= 0) return null
    var low = 0
    var high = lastIndex
    var result = -1
    while (low <= high) {
        val mid = (low + high) ushr 1
        if (this[mid].timestamp <= timestamp) {
            result = mid
            low = mid + 1
        } else {
            high = mid - 1
        }
    }
    return result.takeIf { it >= 0 }
}
