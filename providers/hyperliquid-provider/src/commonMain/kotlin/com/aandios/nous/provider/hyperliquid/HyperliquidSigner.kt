/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import com.aandios.nous.api.market.ProviderConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal interface HyperliquidActionSigner {
    val canSign: Boolean
    suspend fun accountAddress(): String?
    suspend fun signPayload(action: HlAction, vaultAddress: String? = null): JsonObject
}

internal class HyperliquidL1Signer(
    private val privateKeyHex: String?,
    private val configuredAccountAddress: String?,
    private val configuredVaultAddress: String?,
    private val isMainnet: Boolean,
    private val clock: () -> Long = { hyperliquidCurrentTimeMillis() },
) : HyperliquidActionSigner {
    private val mutex = Mutex()
    private var lastNonce = 0L

    override val canSign: Boolean = !privateKeyHex.isNullOrBlank()

    private val derivedAddress: String? by lazy {
        privateKeyHex?.takeIf { it.isNotBlank() }
            ?.let { runCatching { hlAddressFromPrivateKey(it) }.getOrNull() }
            ?.let(::hyperliquidNormalizeAddress)
    }

    override suspend fun accountAddress(): String? =
        configuredAccountAddress?.takeIf { it.isNotBlank() }?.let(::hyperliquidNormalizeAddress)
            ?: derivedAddress

    override suspend fun signPayload(action: HlAction, vaultAddress: String?): JsonObject = mutex.withLock {
        check(canSign) { "Hyperliquid private key is not configured" }
        val nonce = maxOf(clock(), lastNonce + 1L)
        lastNonce = nonce
        val effectiveVault = vaultAddress?.takeIf { it.isNotBlank() } ?: configuredVaultAddress
        val signature = hyperliquidSignL1Action(
            action = action,
            nonce = nonce,
            vaultAddress = effectiveVault,
            isMainnet = isMainnet,
            privateKeyHex = privateKeyHex!!,
        )
        buildJsonObject {
            put("action", hyperliquidActionToJson(action))
            put("nonce", nonce)
            put(
                "signature",
                buildJsonObject {
                    put("r", signature.r)
                    put("s", signature.s)
                    put("v", signature.v)
                },
            )
            effectiveVault?.takeIf { it.isNotBlank() }?.let {
                put("vaultAddress", hyperliquidNormalizeAddress(it))
            }
        }
    }
}

internal data class HyperliquidCredentials(
    val accountAddress: String?,
    val privateKeyHex: String?,
    val vaultAddress: String?,
)

internal fun resolveHyperliquidCredentials(config: ProviderConfig): HyperliquidCredentials {
    val envAccount = hyperliquidEnvironmentVariable("HYPERLIQUID_ACCOUNT_ADDRESS")
        ?: hyperliquidEnvironmentVariable("HYPERLIQUID_API_KEY")
    val envKey = hyperliquidEnvironmentVariable("HYPERLIQUID_PRIVATE_KEY")
        ?: hyperliquidEnvironmentVariable("HYPERLIQUID_SECRET_KEY")

    var account = config.customSettings["accountAddress"]?.trim()?.takeIf { it.isNotEmpty() }
        ?: envAccount?.trim()?.takeIf { it.isNotEmpty() }
    var privateKey = config.secretKey?.trim()?.takeIf { it.isNotEmpty() }
        ?: envKey?.trim()?.takeIf { it.isNotEmpty() }

    val apiKey = config.apiKey?.trim()?.takeIf { it.isNotEmpty() }
    if (apiKey != null) {
        if (looksLikePrivateKey(apiKey)) {
            if (privateKey == null) privateKey = apiKey
        } else if (account == null) {
            account = apiKey
        }
    }
    if (account != null && looksLikePrivateKey(account)) {
        if (privateKey == null) privateKey = account
        account = null
    }

    val vaultAddress = config.customSettings["vaultAddress"]?.trim()?.takeIf { it.isNotEmpty() }
    return HyperliquidCredentials(
        accountAddress = account?.let(::hyperliquidNormalizeAddress),
        privateKeyHex = privateKey?.let(::hyperliquidNormalizePrivateKey),
        vaultAddress = vaultAddress?.let(::hyperliquidNormalizeAddress),
    )
}

private fun looksLikePrivateKey(value: String): Boolean {
    val clean = value.trim().removePrefix("0x").removePrefix("0X")
    return clean.length == 64 && clean.all(::isHexDigit)
}

private fun isHexDigit(char: Char): Boolean =
    char in '0'..'9' || char in 'a'..'f' || char in 'A'..'F'

internal data class HyperliquidAccount(
    val user: String?,
    val vaultAddress: String?,
)

internal class HyperliquidAccountResolver(
    private val info: HyperliquidInfoApi,
    private val credentials: HyperliquidCredentials,
    private val signer: HyperliquidActionSigner,
) {
    private val mutex = Mutex()
    private var cached: HyperliquidAccount? = null

    suspend fun resolve(): HyperliquidAccount = mutex.withLock {
        cached?.let { return@withLock it }
        val initialUser = signer.accountAddress() ?: credentials.accountAddress
        val resolved = if (initialUser == null) {
            HyperliquidAccount(user = null, vaultAddress = credentials.vaultAddress)
        } else {
            val roleResponse = runCatching {
                info.info(
                    buildJsonObject {
                        put("type", "userRole")
                        put("user", initialUser)
                    },
                )
            }.getOrNull()?.asObject()
            val role = roleResponse?.string("role")
            val user = when (role) {
                "agent" -> roleResponse?.get("data")?.asObject()?.string("user") ?: initialUser
                else -> initialUser
            }.let(::hyperliquidNormalizeAddress)
            val vaultAddress = when {
                credentials.vaultAddress != null -> credentials.vaultAddress
                role == "subAccount" || role == "vault" -> user
                else -> null
            }
            HyperliquidAccount(user = user, vaultAddress = vaultAddress)
        }
        cached = resolved
        resolved
    }
}
