/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import kotlinx.serialization.json.JsonObject

/**
 * Maps a Hyperliquid websocket message to the subscription key used by the hub.
 * Keys are shared by all collectors of the same feed.
 */
internal fun hyperliquidStreamKey(root: JsonObject, channel: String = root.string("channel").orEmpty()): String? =
    when (channel) {
        "candle" -> {
            val first = root["data"]?.firstObject()
            val coin = first?.string("s") ?: return null
            val interval = first.string("i") ?: return null
            "candle|$coin|$interval"
        }
        "l2Book", "bbo", "activeAssetCtx" -> {
            val coin = root["data"]?.asObject()?.string("coin") ?: return null
            "$channel|$coin"
        }
        "trades" -> {
            val coin = root["data"]?.firstObject()?.string("coin") ?: return null
            "trades|$coin"
        }
        "user" -> {
            val user = root["data"]?.asObject()?.string("user") ?: return null
            "userFills|$user"
        }
        "orderUpdates" -> "orderUpdates"
        "clearinghouseState" -> {
            val data = root["data"]?.asObject() ?: return null
            val user = data.string("user") ?: return null
            "clearinghouseState|$user|${data.string("dex").orEmpty()}"
        }
        "allDexsClearinghouseState" -> {
            val user = root["data"]?.asObject()?.string("user") ?: return null
            "allDexsClearinghouseState|$user"
        }
        "allDexsAssetCtxs" -> "allDexsAssetCtxs"
        "notification" -> "notification"
        else -> null
    }

private fun kotlinx.serialization.json.JsonElement.firstObject(): JsonObject? =
    asArray()?.firstOrNull()?.asObject() ?: asObject()
