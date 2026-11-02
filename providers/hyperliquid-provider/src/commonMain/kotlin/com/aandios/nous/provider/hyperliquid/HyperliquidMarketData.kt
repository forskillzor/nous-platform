/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import com.aandios.nous.api.market.ProviderConfig
import com.aandios.nous.api.market.model.MarketTickerStats
import com.aandios.nous.api.market.model.SymbolInfo
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal data class HyperliquidAsset(
    val coin: String,
    val assetId: Int,
    val dex: String,
    val dexIndex: Int,
    val indexInMeta: Int,
    val szDecimals: Int,
    val maxLeverage: Int,
    val onlyIsolated: Boolean,
    val marginMode: String?,
    val isDelisted: Boolean,
    val collateral: String,
) {
    val requiresIsolatedMargin: Boolean
        get() = onlyIsolated || marginMode == "noCross" || marginMode == "strictIsolated"

    val baseAsset: String get() = coin.substringAfter(':')
}

internal data class HyperliquidMarketContext(
    val markPrice: Double?,
    val prevDayPrice: Double?,
    val dayNotionalVolume: Double?,
)

internal data class HyperliquidMarketSnapshot(
    val assets: List<HyperliquidAsset>,
    val byCoin: Map<String, HyperliquidAsset>,
    val dexNames: List<String>,
    val assetsByDex: Map<String, List<HyperliquidAsset>>,
    val dexCollateral: Map<String, String>,
    /** Contexts embedded in the legacy `[meta, ctxs]` shape of allPerpMetas. */
    val embeddedContexts: Map<String, List<JsonObject>> = emptyMap(),
)

internal class HyperliquidMarketData(
    private val info: HyperliquidInfoApi,
    config: ProviderConfig,
    private val clock: () -> Long = { hyperliquidCurrentTimeMillis() },
) {
    private val mutex = Mutex()
    private val spotMutex = Mutex()
    private val contextMutex = Mutex()
    private var cached: HyperliquidMarketSnapshot? = null
    private var cachedAt = 0L
    private var cachedSpotTokens: Map<Int, String>? = null
    private var cachedSpotAt = 0L
    private var contextByCoin: Map<String, HyperliquidMarketContext> = emptyMap()
    private var contextRowsByDex: Map<String, List<JsonObject>> = emptyMap()
    private var contextAt: Map<String, Long> = emptyMap()
    private val allowedDexes: Set<String>? = parseDexFilter(config)

    suspend fun snapshot(force: Boolean = false): HyperliquidMarketSnapshot = mutex.withLock {
        val now = clock()
        if (!force) {
            cached?.takeIf { now - cachedAt < META_TTL_MS }?.let { return@withLock it }
        }
        try {
            val fresh = fetchSnapshot()
            cached = fresh
            cachedAt = now
            if (fresh.embeddedContexts.isNotEmpty()) {
                contextMutex.withLock {
                    val rows = contextRowsByDex.toMutableMap()
                    val at = contextAt.toMutableMap()
                    fresh.embeddedContexts.forEach { (dex, ctxs) ->
                        rows[dex] = ctxs
                        at[dex] = now
                    }
                    contextRowsByDex = rows
                    contextAt = at
                    rebuildContextsLocked(fresh)
                }
            }
            fresh
        } catch (e: Exception) {
            cached ?: throw e
        }
    }

    suspend fun assetFor(symbol: String): HyperliquidAsset? {
        val snapshot = snapshot()
        return canonicalCoin(symbol, snapshot)?.let { snapshot.byCoin[it.lowercase()] }
    }

    /** Best-effort synchronous resolution from the last cached snapshot (used by WS subscriptions). */
    fun knownCoin(symbol: String): String? = cached?.let { canonicalCoin(symbol, it) }

    suspend fun canonicalCoinOrNull(symbol: String): String? = canonicalCoin(symbol, snapshot())

    /** Single-symbol lookup used by DOM/Trades/chart restores; context is fetched before tickSize is derived. */
    suspend fun symbolInfo(symbol: String): SymbolInfo? {
        val snapshot = snapshot()
        val asset = canonicalCoin(symbol, snapshot)?.let { snapshot.byCoin[it.lowercase()] } ?: return null
        contextsForDexes(snapshot, listOf(asset.dex), CATALOG_CONTEXT_TTL_MS)
        return symbolInfoFor(asset)
    }

    suspend fun symbols(): List<SymbolInfo> {
        val snapshot = snapshot()
        contextsForDexes(snapshot, snapshot.dexNames, CATALOG_CONTEXT_TTL_MS)
        return snapshot.assets
            .asSequence()
            .filter { !it.isDelisted }
            .filter { allowedDexes == null || it.dex in allowedDexes }
            .map(::symbolInfoFor)
            .sortedBy { it.symbol }
            .toList()
    }

    suspend fun marketOverview(): List<MarketTickerStats> {
        val snapshot = snapshot()
        contextsForDexes(snapshot, snapshot.dexNames, CATALOG_CONTEXT_TTL_MS)
        return snapshot.assets
            .asSequence()
            .filter { !it.isDelisted }
            .filter { allowedDexes == null || it.dex in allowedDexes }
            .mapNotNull(::statsFor)
            .sortedBy { it.symbol }
            .toList()
    }

    suspend fun markPrice(coin: String): Double? {
        val snapshot = snapshot()
        val asset = canonicalCoin(coin, snapshot)?.let { snapshot.byCoin[it.lowercase()] } ?: return null
        contextsForDexes(snapshot, listOf(asset.dex), MARK_CONTEXT_TTL_MS)
        return contextByCoin[asset.coin.lowercase()]?.markPrice
    }

    suspend fun markPrices(): Map<String, Double> = markPrices(snapshot().dexNames)

    suspend fun markPrices(dexes: Collection<String>): Map<String, Double> {
        val snapshot = snapshot()
        val requested = dexes.toSet()
        contextsForDexes(snapshot, requested, MARK_CONTEXT_TTL_MS)
        return buildMap {
            snapshot.assets.forEach { asset ->
                if (asset.dex !in requested) return@forEach
                contextByCoin[asset.coin.lowercase()]?.markPrice?.let { put(asset.coin, it) }
            }
        }
    }

    fun symbolInfoFor(asset: HyperliquidAsset): SymbolInfo {
        val mark = contextByCoin[asset.coin.lowercase()]?.markPrice ?: 0.0
        val step = hyperliquidStepSize(asset.szDecimals)
        return SymbolInfo(
            symbol = asset.coin,
            tickSize = hyperliquidTickSize(mark, asset.szDecimals),
            stepSize = step,
            minQty = step,
            minNotional = HYPERLIQUID_MIN_NOTIONAL,
            status = if (asset.isDelisted) "DELISTED" else "TRADING",
            baseAsset = asset.baseAsset,
            quoteAsset = asset.collateral,
            contractType = "PERPETUAL",
            marginAsset = asset.collateral,
            contractSize = 1.0,
        )
    }

    fun statsFor(asset: HyperliquidAsset): MarketTickerStats? {
        val context = contextByCoin[asset.coin.lowercase()] ?: return null
        val last = context.markPrice ?: return null
        val previous = context.prevDayPrice
        val change = if (previous != null && previous > 0.0) {
            (last - previous) / previous * 100.0
        } else {
            0.0
        }
        return MarketTickerStats(
            symbol = asset.coin,
            lastPrice = last,
            changePercent24h = change,
            quoteVolume24h = context.dayNotionalVolume ?: 0.0,
        )
    }

    /**
     * Hyperliquid currently returns allPerpMetas as a flat array of meta objects,
     * while older deployments/docs return `[meta, ctxs]` pairs. Support both.
     */
    private suspend fun fetchSnapshot(): HyperliquidMarketSnapshot = coroutineScope {
        val perpDexsDeferred = async {
            info.info(buildJsonObject { put("type", "perpDexs") })
        }
        val allPerpMetasDeferred = async {
            info.info(buildJsonObject { put("type", "allPerpMetas") })
        }
        val spotDeferred = async {
            runCatching { spotTokens() }.getOrDefault(fallbackCollateralTokens())
        }

        val dexNames = parsePerpDexs(perpDexsDeferred.await())
        val spotTokens = spotDeferred.await()
        val metas = allPerpMetasDeferred.await().asArray()
            ?: error("Hyperliquid allPerpMetas response is not an array")
        val assets = ArrayList<HyperliquidAsset>()
        val embeddedContexts = LinkedHashMap<String, List<JsonObject>>()
        metas.forEachIndexed { dexIndex, element ->
            val pair = element.asArray()
            val meta = element.asObject() ?: pair?.getOrNull(0)?.asObject() ?: return@forEachIndexed
            val embeddedCtxs = pair?.getOrNull(1)?.asArray()
                ?.mapNotNull { it.asObject() }
                .orEmpty()
            val dexName = dexNames.getOrNull(dexIndex).orEmpty()
            if (embeddedCtxs.isNotEmpty()) embeddedContexts[dexName] = embeddedCtxs
            val collateralToken = meta.int("collateralToken") ?: 0
            val collateral = spotTokens[collateralToken] ?: fallbackCollateralName(collateralToken)
            val universe = meta["universe"]?.asArray() ?: return@forEachIndexed
            universe.forEachIndexed { indexInMeta, assetElement ->
                val asset = assetElement.asObject() ?: return@forEachIndexed
                val coin = asset.string("name") ?: return@forEachIndexed
                assets += HyperliquidAsset(
                    coin = coin,
                    assetId = hyperliquidAssetId(dexIndex, indexInMeta),
                    dex = dexName,
                    dexIndex = dexIndex,
                    indexInMeta = indexInMeta,
                    szDecimals = asset.int("szDecimals") ?: 0,
                    maxLeverage = asset.int("maxLeverage") ?: 1,
                    onlyIsolated = asset.bool("onlyIsolated") == true,
                    marginMode = asset.string("marginMode"),
                    isDelisted = asset.bool("isDelisted") == true,
                    collateral = collateral,
                )
            }
        }
        val assetsByDex = assets.groupBy { it.dex }
        HyperliquidMarketSnapshot(
            assets = assets,
            byCoin = assets.associateBy { it.coin.lowercase() },
            dexNames = dexNames,
            assetsByDex = assetsByDex,
            dexCollateral = assetsByDex.mapValues { (_, dexAssets) ->
                dexAssets.firstOrNull()?.collateral ?: "USDC"
            },
            embeddedContexts = embeddedContexts,
        )
    }

    private suspend fun contextsForDexes(
        snapshot: HyperliquidMarketSnapshot,
        dexes: Collection<String>,
        ttlMs: Long,
    ): Map<String, List<JsonObject>> = contextMutex.withLock {
        val now = clock()
        val requested = dexes.toSet()
        val rows = contextRowsByDex.toMutableMap()
        val at = contextAt.toMutableMap()
        requested.forEach { dex ->
            if (now - (at[dex] ?: 0L) < ttlMs && rows.containsKey(dex)) return@forEach
            val body = buildJsonObject {
                put("type", "metaAndAssetCtxs")
                if (dex.isNotEmpty()) put("dex", dex)
            }
            val response = runCatching { info.info(body) }.getOrNull() ?: return@forEach
            val pair = response.asArray()?.getOrNull(1)?.asArray()
                ?: return@forEach
            rows[dex] = pair.mapNotNull { it.asObject() }
            at[dex] = clock()
        }
        contextRowsByDex = rows
        contextAt = at
        rebuildContextsLocked(snapshot)
        requested.mapNotNull { dex -> rows[dex]?.let { dex to it } }.toMap()
    }

    private fun rebuildContextsLocked(snapshot: HyperliquidMarketSnapshot) {
        val contexts = HashMap<String, HyperliquidMarketContext>()
        contextRowsByDex.forEach { (dex, rows) ->
            val assets = snapshot.assetsByDex[dex].orEmpty()
            rows.forEachIndexed { index, row ->
                val asset = assets.getOrNull(index) ?: return@forEachIndexed
                contexts[asset.coin.lowercase()] = HyperliquidMarketContext(
                    markPrice = row.markPrice(),
                    prevDayPrice = row.double("prevDayPx"),
                    dayNotionalVolume = row.double("dayNtlVlm"),
                )
            }
        }
        contextByCoin = contexts
    }

    private suspend fun spotTokens(): Map<Int, String> {
        val now = clock()
        cachedSpotTokens?.takeIf { now - cachedSpotAt < SPOT_TTL_MS }?.let { return it }
        return spotMutex.withLock {
            cachedSpotTokens?.takeIf { clock() - cachedSpotAt < SPOT_TTL_MS }?.let { return@withLock it }
            val response = info.info(buildJsonObject { put("type", "spotMeta") })
            val tokens = LinkedHashMap<Int, String>()
            fallbackCollateralTokens().forEach { (id, name) -> tokens[id] = name }
            response.asObject()?.get("tokens")?.let { it.asObjectList() }?.forEach { token ->
                val index = token.int("index") ?: return@forEach
                val name = token.string("name") ?: return@forEach
                tokens[index] = name
            }
            cachedSpotTokens = tokens
            cachedSpotAt = clock()
            tokens
        }
    }

    private fun parsePerpDexs(element: kotlinx.serialization.json.JsonElement): List<String> {
        val array = element.asArray() ?: return emptyList()
        return array.map { item ->
            if (item is JsonObject) item.string("name").orEmpty() else ""
        }
    }

    private fun parseDexFilter(config: ProviderConfig): Set<String>? {
        val raw = (config.customSettings["dexes"] ?: hyperliquidEnvironmentVariable("HYPERLIQUID_DEXES"))
            ?.trim()
            .orEmpty()
        if (raw.isEmpty() || raw.equals("all", ignoreCase = true) || raw == "*") return null
        return raw.split(',', ';')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toMutableSet()
            .apply { add("") }
    }

    private fun canonicalCoin(symbol: String, snapshot: HyperliquidMarketSnapshot): String? {
        val normalized = symbol.trim()
        if (normalized.isEmpty()) return null
        snapshot.byCoin[normalized.lowercase()]?.let { return it.coin }
        val upper = normalized.uppercase()
        val suffixes = listOf("USDT", "USDC", "USD")
        suffixes.forEach { suffix ->
            if (upper.endsWith(suffix) && upper.length > suffix.length) {
                val base = upper.dropLast(suffix.length)
                snapshot.byCoin[base.lowercase()]?.let { return it.coin }
            }
        }
        return null
    }

    private fun JsonObject.markPrice(): Double? =
        double("markPx") ?: double("midPx") ?: double("oraclePx")

    private companion object {
        const val META_TTL_MS = 10 * 60_000L
        const val SPOT_TTL_MS = 10 * 60_000L
        const val CATALOG_CONTEXT_TTL_MS = 30_000L
        const val MARK_CONTEXT_TTL_MS = 10_000L

        fun fallbackCollateralTokens(): Map<Int, String> = mapOf(
            0 to "USDC",
            235 to "USDE",
            268 to "USDT0",
            360 to "USDH",
        )

        fun fallbackCollateralName(token: Int): String =
            fallbackCollateralTokens()[token] ?: "TOKEN_$token"
    }
}
