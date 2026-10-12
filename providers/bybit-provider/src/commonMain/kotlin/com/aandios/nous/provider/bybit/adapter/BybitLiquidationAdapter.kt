/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.bybit.adapter

import com.aandios.nous.api.market.adapters.LiquidationAdapter
import com.aandios.nous.api.market.model.liquidation.LiquidationOrder
import com.aandios.nous.provider.bybit.BybitStreamHub
import com.aandios.nous.provider.bybit.asObject
import com.aandios.nous.provider.bybit.asObjectList
import com.aandios.nous.provider.bybit.bybitLiquidation
import com.aandios.nous.provider.bybit.string
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json

class BybitLiquidationAdapter internal constructor(
    private val streams: BybitStreamHub,
) : LiquidationAdapter {
    private val json = Json { ignoreUnknownKeys = true }

    override fun subscribeToLiquidations(symbol: String): Flow<LiquidationOrder> {
        val topic = "allLiquidation.$symbol"
        return streams.subscribe(topic).mapNotNull { text ->
            val root = json.parseToJsonElement(text).asObject() ?: return@mapNotNull null
            if (root.string("topic") != topic) return@mapNotNull null
            root["data"]?.asObjectList()?.firstNotNullOfOrNull(::bybitLiquidation)
        }
    }

    override suspend fun getHistoricalLiquidations(
        symbol: String,
        startTime: Long?,
        endTime: Long?,
        limit: Int,
    ): List<LiquidationOrder> {
        throw UnsupportedOperationException(
            "Bybit V5 does not provide a public historical liquidation endpoint; use the live liquidation stream.",
        )
    }
}
