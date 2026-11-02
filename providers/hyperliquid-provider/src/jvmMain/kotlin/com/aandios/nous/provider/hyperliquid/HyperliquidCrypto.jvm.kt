/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import org.web3j.crypto.ECKeyPair
import org.web3j.crypto.Hash
import org.web3j.crypto.Keys
import org.web3j.crypto.Sign
import java.math.BigInteger

internal actual fun hlKeccak256(bytes: ByteArray): ByteArray = Hash.sha3(bytes)

internal actual fun hlSignDigest(digest: ByteArray, privateKeyHex: String): HlSignature {
    val privateKey = BigInteger(1, hlHexToBytes(privateKeyHex))
    val keyPair = ECKeyPair.create(privateKey)
    val signature = Sign.signMessage(digest, keyPair, false)
    val rawV = signature.v.firstOrNull()?.toInt()?.and(0xFF) ?: 0
    val v = if (rawV < 27) rawV + 27 else rawV
    return HlSignature(
        r = "0x" + BigInteger(1, signature.r).toString(16),
        s = "0x" + BigInteger(1, signature.s).toString(16),
        v = v,
    )
}

internal actual fun hlAddressFromPrivateKey(privateKeyHex: String): String {
    val privateKey = BigInteger(1, hlHexToBytes(privateKeyHex))
    val address = Keys.getAddress(ECKeyPair.create(privateKey))
    return "0x${address.lowercase()}"
}
