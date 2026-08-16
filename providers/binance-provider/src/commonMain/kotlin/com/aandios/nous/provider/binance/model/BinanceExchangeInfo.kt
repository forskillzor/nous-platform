/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.binance.model

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import kotlinx.serialization.json.JsonElement

/**
 * Ответ на запрос exchangeInfo от Binance.
 */
@Serializable
data class BinanceExchangeInfoResponse(
    @SerialName("timezone") val timezone: String,
    @SerialName("serverTime") val serverTime: Long,
    @SerialName("rateLimits") val rateLimits: List<BinanceRateLimit>,
    @SerialName("exchangeFilters") val exchangeFilters: List<JsonElement>,
    @SerialName("symbols") val symbols: List<BinanceSymbolInfo>,
)

@Serializable
data class BinanceRateLimit(
    @SerialName("rateLimitType") val rateLimitType: String,
    @SerialName("interval") val interval: String,
    @SerialName("intervalNum") val intervalNum: Int,
    @SerialName("limit") val limit: Int,
)

@Serializable
data class BinanceSymbolInfo(
    @SerialName("symbol") val symbol: String,
    @SerialName("pair") val pair: String? = null,
    @SerialName("contractType") val contractType: String? = null,
    @SerialName("deliveryDate") val deliveryDate: Long? = null,
    @SerialName("onboardDate") val onboardDate: Long? = null,
    @SerialName("status") val status: String,
    @SerialName("maintMarginPercent") val maintMarginPercent: String? = null,
    @SerialName("requiredMarginPercent") val requiredMarginPercent: String? = null,
    @SerialName("baseAsset") val baseAsset: String,
    @SerialName("quoteAsset") val quoteAsset: String,
    @SerialName("marginAsset") val marginAsset: String? = null,
    @SerialName("pricePrecision") val pricePrecision: Int? = null,
    @SerialName("quantityPrecision") val quantityPrecision: Int? = null,
    @SerialName("baseAssetPrecision") val baseAssetPrecision: Int,
    @SerialName("quotePrecision") val quotePrecision: Int,
    @SerialName("quoteAssetPrecision") val quoteAssetPrecision: Int? = null,
    @SerialName("baseCommissionPrecision") val baseCommissionPrecision: Int? = null,
    @SerialName("quoteCommissionPrecision") val quoteCommissionPrecision: Int? = null,
    @SerialName("underlyingType") val underlyingType: String? = null,
    @SerialName("underlyingSubType") val underlyingSubType: List<String>? = null,
    @SerialName("triggerProtect") val triggerProtect: String? = null,
    @SerialName("liquidationFee") val liquidationFee: String? = null,
    @SerialName("marketTakeBound") val marketTakeBound: String? = null,
    @SerialName("maxMoveOrderLimit") val maxMoveOrderLimit: Int? = null,
    @SerialName("filters") val filters: List<BinanceSymbolFilter>,
    @SerialName("orderTypes") val orderTypes: List<String>,
    @SerialName("timeInForce") val timeInForce: List<String>? = null,
    @SerialName("permissionSets") val permissionSets: List<String>? = null,
    // Старые поля для обратной совместимости
    @SerialName("icebergAllowed") val icebergAllowed: Boolean? = null,
    @SerialName("ocoAllowed") val ocoAllowed: Boolean? = null,
    @SerialName("quoteOrderQtyMarketAllowed") val quoteOrderQtyMarketAllowed: Boolean? = null,
    @SerialName("allowTrailingStop") val allowTrailingStop: Boolean? = null,
    @SerialName("cancelReplaceAllowed") val cancelReplaceAllowed: Boolean? = null,
    @SerialName("isSpotTradingAllowed") val isSpotTradingAllowed: Boolean? = null,
    @SerialName("isMarginTradingAllowed") val isMarginTradingAllowed: Boolean? = null,
    @SerialName("permissions") val permissions: List<String>? = null,
    @SerialName("defaultSelfTradePreventionMode") val defaultSelfTradePreventionMode: String? = null,
    @SerialName("allowedSelfTradePreventionModes") val allowedSelfTradePreventionModes: List<String>? = null,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("filterType")
sealed class BinanceSymbolFilter {
    abstract val filterType: String
}

@Serializable
@SerialName("PRICE_FILTER")
data class BinancePriceFilter(
    @SerialName("filterType") override val filterType: String,
    @SerialName("minPrice") val minPrice: String,
    @SerialName("maxPrice") val maxPrice: String,
    @SerialName("tickSize") val tickSize: String,
) : BinanceSymbolFilter()

@Serializable
@SerialName("LOT_SIZE")
data class BinanceLotSizeFilter(
    @SerialName("filterType") override val filterType: String,
    @SerialName("minQty") val minQty: String,
    @SerialName("maxQty") val maxQty: String,
    @SerialName("stepSize") val stepSize: String,
) : BinanceSymbolFilter()

@Serializable
@SerialName("MARKET_LOT_SIZE")
data class BinanceMarketLotSizeFilter(
    @SerialName("filterType") override val filterType: String,
    @SerialName("minQty") val minQty: String,
    @SerialName("maxQty") val maxQty: String,
    @SerialName("stepSize") val stepSize: String,
) : BinanceSymbolFilter()

@Serializable
@SerialName("MIN_NOTIONAL")
data class BinanceMinNotionalFilter(
    @SerialName("filterType") override val filterType: String,
    @SerialName("minNotional") val minNotional: String? = null,
    @SerialName("notional") val notional: String? = null,
    @SerialName("applyToMarket") val applyToMarket: Boolean? = null,
    @SerialName("avgPriceMins") val avgPriceMins: Int? = null,
) : BinanceSymbolFilter()

@Serializable
@SerialName("MAX_NUM_ORDERS")
data class BinanceMaxNumOrdersFilter(
    @SerialName("filterType") override val filterType: String,
    @SerialName("limit") val limit: Int,
) : BinanceSymbolFilter()

@Serializable
@SerialName("PERCENT_PRICE")
data class BinancePercentPriceFilter(
    @SerialName("filterType") override val filterType: String,
    @SerialName("multiplierUp") val multiplierUp: String,
    @SerialName("multiplierDown") val multiplierDown: String,
    @SerialName("multiplierDecimal") val multiplierDecimal: String,
) : BinanceSymbolFilter()

@Serializable
@SerialName("POSITION_RISK_CONTROL")
data class BinancePositionRiskControlFilter(
    @SerialName("filterType") override val filterType: String,
    @SerialName("positionControlSide") val positionControlSide: String,
) : BinanceSymbolFilter()

// Unknown filter для обработки неизвестных типов фильтров
@Serializable
@SerialName("UNKNOWN")
data class BinanceUnknownFilter(
    @SerialName("filterType") override val filterType: String,
) : BinanceSymbolFilter()