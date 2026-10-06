/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.mexc.adapter

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.adapters.TradingAdapter
import com.aandios.nous.api.market.model.Balance
import com.aandios.nous.api.market.model.orderbook.OrderSide
import com.aandios.nous.api.market.model.orderbook.OrderType
import com.aandios.nous.api.market.model.trading.FeeRates
import com.aandios.nous.api.market.model.trading.Order
import com.aandios.nous.api.market.model.trading.OrderRequest
import com.aandios.nous.api.market.model.trading.OrderResponse
import com.aandios.nous.api.market.model.trading.Position
import com.aandios.nous.api.market.model.trading.TradeFill
import com.aandios.nous.provider.mexc.MexcRestGate
import com.aandios.nous.provider.mexc.MexcStreamHub
import com.aandios.nous.provider.mexc.MexcTradingClient
import com.aandios.nous.provider.mexc.currentTimeMillis
import com.aandios.nous.provider.mexc.formatDecimal
import com.aandios.nous.provider.mexc.toMexcSymbol
import com.aandios.nous.provider.mexc.model.MexcAsset
import com.aandios.nous.provider.mexc.model.MexcCancelResult
import com.aandios.nous.provider.mexc.model.MexcHistoryOrder
import com.aandios.nous.provider.mexc.model.MexcOrderDeal
import com.aandios.nous.provider.mexc.model.MexcPosition
import io.ktor.client.HttpClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

/**
 * Торговый адаптер MEXC Futures (Contract API v1, private endpoints).
 *
 * Ключи — из ProviderConfig или переменных окружения MEXC_API_KEY/MEXC_SECRET_KEY.
 * Без ключей вызовы возвращают понятную ошибку (не бросают).
 *
 * Семантика сторон MEXC: 1 open long, 2 close short, 3 open short, 4 close long.
 * Типы: 1 limit, 2 post-only, 3 IOC, 4 FOK, 5 market.
 *
 * Единицы: платформа работает в базовом активе (SOL), MEXC — в контрактах.
 * Адаптер конвертирует qty в/из контрактов через contractSize символа
 * (справочник `/api/v1/contract/detail`, кэш) — ордера, позиции и сделки
 * наружу отдаются в базовом активе.
 */
class MexcTradingAdapter(
    private val client: HttpClient,
    @Suppress("unused") private val config: ProviderConfig,
    private val restGate: MexcRestGate,
    private val streamHub: MexcStreamHub,
) : TradingAdapter {

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private val tradingClient = MexcTradingClient(client, config)
    private val symbolInfoAdapter = MexcSymbolInfoAdapter(client, config, restGate)

    private fun noKeys(): OrderResponse = OrderResponse(
        orderId = "",
        success = false,
        message = "MEXC API keys not set (MEXC_API_KEY / MEXC_SECRET_KEY)",
    )

    // ── Единицы: базовый актив (платформа) ↔ контракты (MEXC) ──

    private data class ContractSpec(
        val contractSize: Double,
        /** Inverse (COIN-M): qty платформы — уже контракты, ответы не масштабируем. */
        val inverse: Boolean,
        /** Шаг объёма в контрактах. */
        val volUnit: Double,
    )

    private val specCache = mutableMapOf<String, ContractSpec>()

    /** Параметры единиц символа (contractSize/шаг/инверсия) — с кэшем. */
    private suspend fun contractSpec(symbol: String): ContractSpec {
        specCache[symbol]?.let { return it }
        val info = runCatching { symbolInfoAdapter.getSymbolInfo(symbol) }.getOrNull()
        val cs = info?.contractSize?.takeIf { it > 0.0 } ?: 1.0
        val inverse = info?.isInverse == true
        val volUnit = if (inverse) {
            // stepSize в SymbolInfo — контракты
            info?.stepSize?.takeIf { it > 0.0 } ?: 1.0
        } else {
            // stepSize в SymbolInfo — базовый актив (volUnit * cs)
            info?.stepSize?.takeIf { it > 0.0 }?.div(cs)?.takeIf { it > 0.0 } ?: 1.0
        }
        val spec = ContractSpec(cs, inverse, volUnit)
        specCache[symbol] = spec
        return spec
    }

    /**
     * qty платформы → контракты (half-up к шагу контракта):
     * linear — qty в базовом активе, делим на contractSize; inverse — уже контракты.
     */
    private fun toContracts(quantity: Double, spec: ContractSpec): Double {
        val raw = if (spec.inverse) {
            quantity / spec.volUnit
        } else {
            quantity / spec.contractSize / spec.volUnit
        }
        val units = kotlin.math.floor(raw + 0.5)
        return units * spec.volUnit
    }

    private fun Order.scaled(spec: ContractSpec): Order =
        if (spec.inverse) this else copy(
            quantity = quantity * spec.contractSize,
            filledQuantity = filledQuantity * spec.contractSize,
        )

    private fun Position.scaled(spec: ContractSpec): Position =
        if (spec.inverse) this else copy(quantity = quantity * spec.contractSize)

    private fun TradeFill.scaled(spec: ContractSpec): TradeFill =
        if (spec.inverse) this else copy(quantity = quantity * spec.contractSize)

    // ── Ордера ──

    override suspend fun placeOrder(request: OrderRequest): OrderResponse {
        if (!tradingClient.hasCredentials) return noKeys()

        // qty платформы (базовый актив) → контракты MEXC
        val spec = contractSpec(request.symbol)
        val vol = toContracts(request.quantity, spec)
        if (vol <= 0.0) {
            return OrderResponse(
                orderId = "",
                success = false,
                message = "Quantity is below the contract minimum for ${request.symbol}",
            )
        }

        val side = when {
            request.reduceOnly && request.side == OrderSide.BUY -> 2   // close short
            request.reduceOnly && request.side == OrderSide.SELL -> 4  // close long
            request.side == OrderSide.BUY -> 1                         // open long
            else -> 3                                                 // open short
        }
        val type = when (request.orderType) {
            OrderType.LIMIT -> 1
            OrderType.POST_ONLY -> 2
            OrderType.IOC -> 3
            OrderType.FOK -> 4
            OrderType.MARKET -> 5
        }
        val body = buildJsonObject {
            put("symbol", toMexcSymbol(request.symbol))
            if (type != 5) put("price", formatDecimal(request.price))
            put("vol", formatDecimal(vol))
            put("side", side)
            put("type", type)
            put("openType", request.marginMode ?: 2) // cross по умолчанию
            request.leverage?.let { put("leverage", it) }
            request.positionMode?.let { put("positionMode", it) }
            request.positionId?.let { put("positionId", it) }
            request.clientOrderId?.let { put("externalOid", it) }
            if (request.reduceOnly) put("reduceOnly", true)
            request.stopLossPrice?.let { put("stopLossPrice", formatDecimal(it)) }
            request.takeProfitPrice?.let { put("takeProfitPrice", formatDecimal(it)) }
        }

        return restGate.execute(key = "order:submit:${request.symbol}", weight = 20) {
            val response = tradingClient.signedPost("api/v1/private/order/submit", body.toString())
            val obj = response.jsonObject
            val success = obj["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
            val orderId = obj["data"]?.jsonPrimitive?.longOrNull?.toString().orEmpty()
            OrderResponse(
                orderId = orderId,
                price = request.price,
                success = success && orderId.isNotEmpty(),
                message = if (success && orderId.isNotEmpty()) null
                else (obj["message"]?.jsonPrimitive?.content ?: "Order submit failed"),
            )
        }
    }

    override suspend fun cancelOrder(orderId: String): Boolean {
        if (!tradingClient.hasCredentials) return false
        return restGate.execute(key = "order:cancel:$orderId", weight = 20) {
            val response = tradingClient.signedPost("api/v1/private/order/cancel", "[$orderId]")
            val results = json.decodeFromJsonElement<List<MexcCancelResult>>(response.jsonObject["data"] ?: JsonArray(emptyList()))
            results.any { it.errorCode == 0 }
        }
    }

    override suspend fun cancelAllOrders(symbol: String?): Boolean {
        if (!tradingClient.hasCredentials) return false
        val body = if (symbol != null) {
            buildJsonObject { put("symbol", toMexcSymbol(symbol)) }.toString()
        } else "{}"
        return restGate.execute(key = "order:cancelAll:${symbol.orEmpty()}", weight = 20) {
            val response = tradingClient.signedPost("api/v1/private/order/cancel_all", body)
            response.jsonObject["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        }
    }

    override suspend fun closePosition(
        symbol: String,
        positionId: Long?,
        quantity: Double?,
    ): OrderResponse? {
        if (!tradingClient.hasCredentials) return noKeys()
        val position = getPositions().firstOrNull {
            it.symbol == symbol.uppercase() && (positionId == null || it.positionId == positionId)
        } ?: return OrderResponse("", success = false, message = "Position not found for $symbol")
        return placeOrder(
            OrderRequest(
                symbol = symbol,
                side = if (position.side == com.aandios.nous.api.market.model.trading.TradeSide.BUY) {
                    OrderSide.SELL
                } else {
                    OrderSide.BUY
                },
                orderType = OrderType.MARKET,
                quantity = quantity ?: position.quantity,
                reduceOnly = true,
                positionId = position.positionId,
                marginMode = position.marginMode,
            )
        )
    }

    // ── Счёт/позиции/ордера/история ──

    override suspend fun getBalances(): List<Balance> {
        if (!tradingClient.hasCredentials) return emptyList()
        return restGate.execute(key = "account:assets", weight = 20) {
            val response = tradingClient.signedGet("api/v1/private/account/assets")
            json.decodeFromJsonElement<List<MexcAsset>>(response.jsonObject["data"] ?: JsonArray(emptyList()))
                .map { it.toBalance() }
        }
    }

    override suspend fun getPositions(): List<Position> {
        if (!tradingClient.hasCredentials) return emptyList()
        return restGate.execute(key = "position:open", weight = 20) {
            val response = tradingClient.signedGet("api/v1/private/position/open_positions")
            json.decodeFromJsonElement<List<MexcPosition>>(response.jsonObject["data"] ?: JsonArray(emptyList()))
                .filter { it.state == 1 } // только удерживаемые
                .map { position ->
                    val spec = contractSpec(position.symbol)
                    position.toPosition().scaled(spec)
                }
        }
    }

    override suspend fun getOpenOrders(symbol: String?): List<Order> {
        if (!tradingClient.hasCredentials) return emptyList()
        val path = if (symbol != null) {
            "api/v1/private/order/list/open_orders/${toMexcSymbol(symbol)}"
        } else {
            "api/v1/private/order/list/open_orders"
        }
        return restGate.execute(key = "orders:open:${symbol.orEmpty()}", weight = 20) {
            val response = tradingClient.signedGet(path, mapOf("page_size" to "100"))
            json.decodeFromJsonElement<List<com.aandios.nous.provider.mexc.model.MexcOrder>>(
                response.jsonObject["data"] ?: JsonArray(emptyList())
            ).map { order ->
                val spec = contractSpec(order.symbol)
                order.toOrder().scaled(spec)
            }
        }
    }

    override suspend fun getTradeHistory(symbol: String?, limit: Int): List<TradeFill> {
        if (!tradingClient.hasCredentials) return emptyList()
        // order_deals требует symbol; без него берём history_orders (symbol необязателен)
        if (symbol == null) {
            return restGate.execute(key = "orders:history", weight = 20) {
                val response = tradingClient.signedGet(
                    "api/v1/private/order/list/history_orders",
                    mapOf(
                        "page_num" to "1",
                        "page_size" to limit.coerceIn(1, 100).toString(),
                    ),
                )
                json.decodeFromJsonElement<List<MexcHistoryOrder>>(
                    response.jsonObject["data"] ?: JsonArray(emptyList())
                ).filter { it.dealVol > 0.0 } // только исполненные (полностью/частично)
                    .map { order ->
                        val spec = contractSpec(order.symbol)
                        order.toTradeFill().scaled(spec)
                    }
            }
        }
        val params = buildMap {
            put("symbol", toMexcSymbol(symbol))
            put("page_num", "1")
            put("page_size", limit.coerceIn(1, 100).toString())
        }
        return restGate.execute(key = "orders:deals:${symbol.orEmpty()}", weight = 20) {
            val response = tradingClient.signedGet("api/v1/private/order/list/order_deals", params)
            json.decodeFromJsonElement<List<MexcOrderDeal>>(response.jsonObject["data"] ?: JsonArray(emptyList()))
                .map { deal ->
                    val spec = contractSpec(deal.symbol)
                    deal.toTradeFill().scaled(spec)
                }
        }
    }

    /**
     * Реализованный PnL за текущий торговый день MEXC (UTC+8):
     * Σ(profit − takerFee − makerFee) по history_orders, постранично.
     */
    override suspend fun getDailyRealizedPnl(): Double? {
        if (!tradingClient.hasCredentials) return null
        val startOfDay = startOfMexcTradingDay()
        val endTime = currentTimeMillis()
        return restGate.execute(key = "orders:dailyPnl", weight = 20) {
            var total = 0.0
            var page = 1
            while (page <= 10) {
                val response = tradingClient.signedGet(
                    "api/v1/private/order/list/history_orders",
                    mapOf(
                        "start_time" to startOfDay.toString(),
                        "end_time" to endTime.toString(),
                        "page_num" to page.toString(),
                        "page_size" to "100",
                    ),
                )
                val orders = json.decodeFromJsonElement<List<MexcHistoryOrder>>(
                    response.jsonObject["data"] ?: JsonArray(emptyList())
                )
                orders.forEach { total += it.netProfit }
                if (orders.size < 100) break
                page++
            }
            total
        }
    }

    /** Начало торгового дня MEXC (UTC+8) в epoch-ms. */
    private fun startOfMexcTradingDay(): Long {
        val shiftMs = 8L * 60 * 60 * 1000
        val dayMs = 24L * 60 * 60 * 1000
        val shifted = currentTimeMillis() + shiftMs
        return (shifted / dayMs) * dayMs - shiftMs
    }

    // ── Плечо / режимы / маржа ──

    override suspend fun setLeverage(symbol: String, leverage: Int, positionId: Long?): Boolean {
        if (!tradingClient.hasCredentials) return false
        val targetId = positionId ?: getPositions().firstOrNull {
            it.symbol == symbol.uppercase()
        }?.positionId

        val body = buildJsonObject {
            if (targetId != null) {
                put("positionId", targetId)
                put("leverage", leverage)
            } else {
                // Позиции нет — нужен symbol + тип; задаём для будущей long-позиции изолированно
                put("symbol", toMexcSymbol(symbol))
                put("leverage", leverage)
                put("openType", 1)
                put("positionType", 1)
            }
        }
        return restGate.execute(key = "position:leverage:$symbol", weight = 20) {
            val response = tradingClient.signedPost("api/v1/private/position/change_leverage", body.toString())
            response.jsonObject["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        }
    }

    override suspend fun getLeverage(symbol: String): Int? {
        if (!tradingClient.hasCredentials) return null
        return restGate.execute(key = "position:leverage:get:$symbol", weight = 20) {
            val response = tradingClient.signedGet(
                "api/v1/private/position/leverage",
                mapOf("symbol" to toMexcSymbol(symbol)),
            )
            val arr = response.jsonObject["data"] as? JsonArray ?: return@execute null
            arr.firstOrNull()?.jsonObject?.get("leverage")?.jsonPrimitive?.content?.toIntOrNull()
        }
    }

    override suspend fun getPositionMode(): Int? {
        if (!tradingClient.hasCredentials) return null
        return restGate.execute(key = "position:mode", weight = 20) {
            val response = tradingClient.signedGet("api/v1/private/position/position_mode")
            response.jsonObject["data"]?.jsonPrimitive?.content?.toIntOrNull()
        }
    }

    override suspend fun setPositionMode(mode: Int): Boolean {
        if (!tradingClient.hasCredentials) return false
        val body = buildJsonObject { put("positionMode", mode) }.toString()
        return restGate.execute(key = "position:mode:set", weight = 20) {
            val response = tradingClient.signedPost("api/v1/private/position/change_position_mode", body)
            response.jsonObject["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        }
    }

    override suspend fun adjustMargin(positionId: Long, amount: Double, add: Boolean): Boolean {
        if (!tradingClient.hasCredentials) return false
        val body = buildJsonObject {
            put("positionId", positionId)
            put("amount", formatDecimal(amount))
            put("type", if (add) "ADD" else "SUB")
        }
        return restGate.execute(key = "position:margin:$positionId", weight = 20) {
            val response = tradingClient.signedPost("api/v1/private/position/change_margin", body.toString())
            response.jsonObject["success"]?.jsonPrimitive?.content?.toBooleanStrictOrNull() ?: false
        }
    }

    // ── Комиссии ──

    override suspend fun getFeeRates(symbol: String): FeeRates? {
        if (!tradingClient.hasCredentials) return null
        return restGate.execute(key = "account:fees:$symbol", weight = 20) {
            val response = tradingClient.signedGet(
                "api/v1/private/account/tiered_fee_rate/v2",
                mapOf("symbol" to toMexcSymbol(symbol)),
            )
            val data = response.jsonObject["data"]?.jsonObject ?: return@execute null
            fun read(name: String): Double? = data[name]?.jsonPrimitive?.content?.toDoubleOrNull()
            val maker = read("realMakerFee") ?: read("originalMakerFee") ?: return@execute null
            val taker = read("realTakerFee") ?: read("originalTakerFee") ?: return@execute null
            FeeRates(maker = maker, taker = taker)
        }
    }

    // ── Живые обновления (приватный WS) ──

    override fun subscribeToPositions(): Flow<Position>? {
        if (!tradingClient.hasCredentials) return null
        return streamHub.subscribePersonal("personal:position").mapNotNull { text ->
            val data = (json.parseToJsonElement(text) as? JsonObject)?.get("data") as? JsonObject ?: return@mapNotNull null
            val position = json.decodeFromJsonElement<MexcPosition>(data).toPosition()
            position.scaled(contractSpec(position.symbol))
        }
    }

    override fun subscribeToOrders(): Flow<Order>? {
        if (!tradingClient.hasCredentials) return null
        return streamHub.subscribePersonal("personal:order").mapNotNull { text ->
            val data = (json.parseToJsonElement(text) as? JsonObject)?.get("data") as? JsonObject ?: return@mapNotNull null
            val order = json.decodeFromJsonElement<com.aandios.nous.provider.mexc.model.MexcOrder>(data).toOrder()
            order.scaled(contractSpec(order.symbol))
        }
    }

    override fun subscribeToBalances(): Flow<Balance>? {
        if (!tradingClient.hasCredentials) return null
        return streamHub.subscribePersonal("personal:asset").mapNotNull { text ->
            val data = (json.parseToJsonElement(text) as? JsonObject)?.get("data") as? JsonObject ?: return@mapNotNull null
            json.decodeFromJsonElement<MexcAsset>(data).toBalance()
        }
    }
}
