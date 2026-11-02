/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

internal fun JsonObject.string(name: String): String? =
    this[name]?.jsonPrimitive?.contentOrNull

internal fun JsonObject.double(name: String): Double? = string(name)?.toDoubleOrNull()

internal fun JsonObject.long(name: String): Long? = string(name)?.toLongOrNull()

internal fun JsonObject.int(name: String): Int? = string(name)?.toIntOrNull()

internal fun JsonObject.bool(name: String): Boolean? {
    val primitive = this[name]?.jsonPrimitive ?: return null
    return primitive.booleanOrNull ?: primitive.contentOrNull?.toBooleanStrictOrNull()
}

internal fun JsonElement.asObject(): JsonObject? = this as? JsonObject

internal fun JsonElement.asArray(): JsonArray? = this as? JsonArray

internal fun JsonElement.asObjectList(): List<JsonObject> =
    asArray()?.mapNotNull { it as? JsonObject }.orEmpty()

internal fun JsonObject.requireObject(name: String): JsonObject =
    this[name]?.asObject() ?: error("Hyperliquid payload is missing object '$name'")

internal fun JsonObject.requireArray(name: String): JsonArray =
    this[name]?.asArray() ?: error("Hyperliquid payload is missing array '$name'")

internal fun JsonPrimitive.asString(): String? = contentOrNull
