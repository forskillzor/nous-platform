/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.core.storage

interface StateStore {
    suspend fun getString(key: String): String?
    suspend fun putString(key: String, value: String)
}
