/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Vectors from the official hyperliquid-python-sdk signing tests.
 */
class HyperliquidSigningTest {
    private val privateKey = "0x0123456789012345678901234567890123456789012345678901234567890123"
    private val vault = "0x1719884eb866cb12b2287399b15f7db5e7d775ea"

    @Test
    fun `action hash matches sdk vector`() {
        val action = HyperliquidActions.orderAction(
            listOf(
                HyperliquidActions.order(
                    asset = 4,
                    isBuy = true,
                    price = "1670.1",
                    size = "0.0147",
                    reduceOnly = false,
                    limitTif = "Ioc",
                ),
            ),
        )
        val hash = hyperliquidActionHash(action, 1_677_777_606_040L, vaultAddress = null)
        assertEquals(
            "0x0fcbeda5ae3c4950a548021552a4fea2226858c4453571bf3f24ba017eac2908",
            hash.toHex(),
        )
    }

    @Test
    fun `dummy action matches sdk vectors`() {
        val action = linkedMapOf<String, Any?>(
            "type" to "dummy",
            "num" to 100_000_000_000L,
        )

        assertSignature(
            action = action,
            nonce = 0L,
            vaultAddress = null,
            isMainnet = true,
            r = "0x53749d5b30552aeb2fca34b530185976545bb22d0b3ce6f62e31be961a59298",
            s = "0x755c40ba9bf05223521753995abb2f73ab3229be8ec921f350cb447e384d8ed8",
            v = 27,
        )
        assertSignature(
            action = action,
            nonce = 0L,
            vaultAddress = null,
            isMainnet = false,
            r = "0x542af61ef1f429707e3c76c5293c80d01f74ef853e34b76efffcb57e574f9510",
            s = "0x17b8b32f086e8cdede991f1e2c529f5dd5297cbe8128500e00cbaf766204a613",
            v = 28,
        )
    }

    @Test
    fun `limit order matches sdk vectors`() {
        val action = HyperliquidActions.orderAction(
            listOf(
                HyperliquidActions.order(
                    asset = 1,
                    isBuy = true,
                    price = "100",
                    size = "100",
                    reduceOnly = false,
                    limitTif = "Gtc",
                ),
            ),
        )

        assertSignature(
            action = action,
            nonce = 0L,
            vaultAddress = null,
            isMainnet = true,
            r = "0xd65369825a9df5d80099e513cce430311d7d26ddf477f5b3a33d2806b100d78e",
            s = "0x2b54116ff64054968aa237c20ca9ff68000f977c93289157748a3162b6ea940e",
            v = 28,
        )
        assertSignature(
            action = action,
            nonce = 0L,
            vaultAddress = null,
            isMainnet = false,
            r = "0x82b2ba28e76b3d761093aaded1b1cdad4960b3af30212b343fb2e6cdfa4e3d54",
            s = "0x6b53878fc99d26047f4d7e8c90eb98955a109f44209163f52d8dc4278cbbd9f5",
            v = 27,
        )
    }

    @Test
    fun `limit order with cloid matches sdk vectors`() {
        val action = HyperliquidActions.orderAction(
            listOf(
                HyperliquidActions.order(
                    asset = 1,
                    isBuy = true,
                    price = "100",
                    size = "100",
                    reduceOnly = false,
                    limitTif = "Gtc",
                    cloid = "0x00000000000000000000000000000001",
                ),
            ),
        )

        assertSignature(
            action = action,
            nonce = 0L,
            vaultAddress = null,
            isMainnet = true,
            r = "0x41ae18e8239a56cacbc5dad94d45d0b747e5da11ad564077fcac71277a946e3",
            s = "0x3c61f667e747404fe7eea8f90ab0e76cc12ce60270438b2058324681a00116da",
            v = 27,
        )
        assertSignature(
            action = action,
            nonce = 0L,
            vaultAddress = null,
            isMainnet = false,
            r = "0xeba0664bed2676fc4e5a743bf89e5c7501aa6d870bdb9446e122c9466c5cd16d",
            s = "0x7f3e74825c9114bc59086f1eebea2928c190fdfbfde144827cb02b85bbe90988",
            v = 28,
        )
    }

    @Test
    fun `trigger order matches sdk vectors`() {
        val action = HyperliquidActions.orderAction(
            listOf(
                HyperliquidActions.order(
                    asset = 1,
                    isBuy = true,
                    price = "100",
                    size = "100",
                    reduceOnly = false,
                    trigger = HyperliquidActions.TriggerType(
                        isMarket = true,
                        triggerPrice = "103",
                        tpsl = "sl",
                    ),
                ),
            ),
        )

        assertSignature(
            action = action,
            nonce = 0L,
            vaultAddress = null,
            isMainnet = true,
            r = "0x98343f2b5ae8e26bb2587daad3863bc70d8792b09af1841b6fdd530a2065a3f9",
            s = "0x6b5bb6bb0633b710aa22b721dd9dee6d083646a5f8e581a20b545be6c1feb405",
            v = 27,
        )
        assertSignature(
            action = action,
            nonce = 0L,
            vaultAddress = null,
            isMainnet = false,
            r = "0x971c554d917c44e0e1b6cc45d8f9404f32172a9d3b3566262347d0302896a2e4",
            s = "0x206257b104788f80450f8e786c329daa589aa0b32ba96948201ae556d5637eac",
            v = 28,
        )
    }

    @Test
    fun `vault action matches sdk vectors`() {
        val action = linkedMapOf<String, Any?>(
            "type" to "dummy",
            "num" to 100_000_000_000L,
        )

        assertSignature(
            action = action,
            nonce = 0L,
            vaultAddress = vault,
            isMainnet = true,
            r = "0x3c548db75e479f8012acf3000ca3a6b05606bc2ec0c29c50c515066a326239",
            s = "0x4d402be7396ce74fbba3795769cda45aec00dc3125a984f2a9f23177b190da2c",
            v = 28,
        )
        assertSignature(
            action = action,
            nonce = 0L,
            vaultAddress = vault,
            isMainnet = false,
            r = "0xe281d2fb5c6e25ca01601f878e4d69c965bb598b88fac58e475dd1f5e56c362b",
            s = "0x7ddad27e9a238d045c035bc606349d075d5c5cd00a6cd1da23ab5c39d4ef0f60",
            v = 27,
        )
    }

    private fun assertSignature(
        action: HlAction,
        nonce: Long,
        vaultAddress: String?,
        isMainnet: Boolean,
        r: String,
        s: String,
        v: Int,
    ) {
        val signature = hyperliquidSignL1Action(action, nonce, vaultAddress, isMainnet, privateKey)
        assertEquals(r, signature.r)
        assertEquals(s, signature.s)
        assertEquals(v, signature.v)
    }

    private fun ByteArray.toHex(): String =
        "0x" + joinToString("") { byte -> (byte.toInt() and 0xFF).toString(16).padStart(2, '0') }
}
