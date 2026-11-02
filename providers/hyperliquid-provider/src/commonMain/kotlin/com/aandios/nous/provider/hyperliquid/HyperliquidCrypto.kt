/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

internal typealias HlAction = LinkedHashMap<String, Any?>

internal data class HlSignature(
    val r: String,
    val s: String,
    val v: Int,
)

/**
 * Hyperliquid L1 action hash: msgpack(action) + nonce + vault flag.
 * `expiresAfter` is intentionally omitted (null).
 */
internal fun hyperliquidActionHash(
    action: HlAction,
    nonce: Long,
    vaultAddress: String?,
): ByteArray {
    val payload = HlMsgPack.encode(action)
    val nonceBytes = ByteArray(8) { index ->
        ((nonce ushr ((7 - index) * 8)) and 0xFF).toByte()
    }
    val vaultBytes = if (vaultAddress.isNullOrBlank()) {
        byteArrayOf(0x00)
    } else {
        byteArrayOf(0x01) + hlHexToBytes(vaultAddress)
    }
    return hlKeccak256(payload + nonceBytes + vaultBytes)
}

internal fun hyperliquidPhantomAgentDigest(actionHash: ByteArray, isMainnet: Boolean): ByteArray {
    val source = if (isMainnet) "a" else "b"
    val domainSeparator = hlKeccak256(
        hlKeccak256("EIP712Domain(string name,string version,uint256 chainId,address verifyingContract)".encodeToByteArray()) +
            hlKeccak256("Exchange".encodeToByteArray()) +
            hlKeccak256("1".encodeToByteArray()) +
            hlUint256(1337L) +
            ByteArray(32),
    )
    val sourceBytes = source.encodeToByteArray()
    // EIP-712 hashes dynamic fields as keccak256(contents), not ABI offset/length encoding.
    val structHash = hlKeccak256(
        hlKeccak256("Agent(string source,bytes32 connectionId)".encodeToByteArray()) +
            hlKeccak256(sourceBytes) +
            actionHash,
    )
    return hlKeccak256(byteArrayOf(0x19, 0x01) + domainSeparator + structHash)
}

internal fun hyperliquidSignL1Action(
    action: HlAction,
    nonce: Long,
    vaultAddress: String?,
    isMainnet: Boolean,
    privateKeyHex: String,
): HlSignature {
    val hash = hyperliquidActionHash(action, nonce, vaultAddress)
    val digest = hyperliquidPhantomAgentDigest(hash, isMainnet)
    return hlSignDigest(digest, privateKeyHex)
}

internal fun hyperliquidActionToJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is String -> JsonPrimitive(value)
    is Boolean -> JsonPrimitive(value)
    is Int -> JsonPrimitive(value)
    is Long -> JsonPrimitive(value)
    is Map<*, *> -> buildJsonObject {
        value.forEach { (key, entry) ->
            put(key as? String ?: error("Action map keys must be strings"), hyperliquidActionToJson(entry))
        }
    }
    is List<*> -> buildJsonArray {
        value.forEach { add(hyperliquidActionToJson(it)) }
    }
    else -> error("Unsupported action value: ${value::class}")
}

internal fun hyperliquidNormalizeAddress(address: String): String {
    val clean = address.trim().removePrefix("0x").removePrefix("0X").lowercase()
    return if (clean.isEmpty()) "" else "0x$clean"
}

internal fun hyperliquidNormalizePrivateKey(key: String): String {
    val clean = key.trim().removePrefix("0x").removePrefix("0X").lowercase()
    return "0x$clean"
}

internal fun hlHexToBytes(hex: String): ByteArray {
    val clean = hex.trim().removePrefix("0x").removePrefix("0X")
    require(clean.length % 2 == 0) { "Hex string must have an even length" }
    return ByteArray(clean.length / 2) { index ->
        clean.substring(index * 2, index * 2 + 2).toInt(16).toByte()
    }
}

internal fun hlUint256(value: Long): ByteArray {
    val result = ByteArray(32)
    var remaining = value
    for (index in 31 downTo 0) {
        result[index] = (remaining and 0xFF).toByte()
        remaining = remaining ushr 8
    }
    return result
}

internal expect fun hlKeccak256(bytes: ByteArray): ByteArray

internal expect fun hlSignDigest(digest: ByteArray, privateKeyHex: String): HlSignature

internal expect fun hlAddressFromPrivateKey(privateKeyHex: String): String
