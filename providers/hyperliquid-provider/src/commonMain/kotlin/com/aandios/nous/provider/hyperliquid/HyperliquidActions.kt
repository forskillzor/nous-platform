/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

internal object HyperliquidActions {
    fun order(
        asset: Int,
        isBuy: Boolean,
        price: String,
        size: String,
        reduceOnly: Boolean,
        limitTif: String? = null,
        trigger: TriggerType? = null,
        cloid: String? = null,
    ): HlAction {
        require(limitTif != null || trigger != null) { "Order needs a limit or trigger type" }
        val type = when {
            limitTif != null -> linkedMapOf<String, Any?>(
                "limit" to linkedMapOf<String, Any?>("tif" to limitTif),
            )
            trigger != null -> linkedMapOf<String, Any?>(
                "trigger" to linkedMapOf<String, Any?>(
                    "isMarket" to trigger.isMarket,
                    "triggerPx" to trigger.triggerPrice,
                    "tpsl" to trigger.tpsl,
                ),
            )
            else -> error("unreachable")
        }
        val order = linkedMapOf<String, Any?>(
            "a" to asset,
            "b" to isBuy,
            "p" to price,
            "s" to size,
            "r" to reduceOnly,
            "t" to type,
        )
        cloid?.let { order["c"] = it }
        return order
    }

    fun orderAction(
        orders: List<HlAction>,
        grouping: String = "na",
    ): HlAction = linkedMapOf(
        "type" to "order",
        "orders" to orders,
        "grouping" to grouping,
    )

    fun cancelAction(cancels: List<HlAction>): HlAction = linkedMapOf(
        "type" to "cancel",
        "cancels" to cancels,
    )

    fun cancel(asset: Int, oid: Long): HlAction = linkedMapOf(
        "a" to asset,
        "o" to oid,
    )

    fun updateLeverage(asset: Int, isCross: Boolean, leverage: Int): HlAction = linkedMapOf(
        "type" to "updateLeverage",
        "asset" to asset,
        "isCross" to isCross,
        "leverage" to leverage,
    )

    fun updateIsolatedMargin(asset: Int, isBuy: Boolean, notional: Long): HlAction = linkedMapOf(
        "type" to "updateIsolatedMargin",
        "asset" to asset,
        "isBuy" to isBuy,
        "ntli" to notional,
    )

    data class TriggerType(
        val isMarket: Boolean,
        val triggerPrice: String,
        val tpsl: String,
    )
}
