/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.double(name: String): Double? = string(name)?.toDoubleOrNull()

internal fun JsonObject.long(name: String): Long? = string(name)?.toLongOrNull()

internal fun JsonElement.asObject(): JsonObject? = this as? JsonObject

internal fun JsonElement.asArray(): JsonArray? = this as? JsonArray
