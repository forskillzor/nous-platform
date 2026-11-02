/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HyperliquidStreamRoutingTest {
    @Test
    fun `routes market data messages by coin`() {
        assertEquals(
            "candle|BTC|15m",
            hyperliquidStreamKey(
                jsonObject(
                    """{"channel":"candle","data":[{"t":1,"s":"BTC","i":"15m"}]}""",
                ),
            ),
        )
        assertEquals(
            "l2Book|BTC",
            hyperliquidStreamKey(jsonObject("""{"channel":"l2Book","data":{"coin":"BTC"}}""")),
        )
        assertEquals(
            "trades|ETH",
            hyperliquidStreamKey(jsonObject("""{"channel":"trades","data":[{"coin":"ETH"}]}""")),
        )
        assertEquals(
            "bbo|BTC",
            hyperliquidStreamKey(jsonObject("""{"channel":"bbo","data":{"coin":"BTC"}}""")),
        )
    }

    @Test
    fun `routes user and control messages`() {
        assertEquals(
            "userFills|0xabc",
            hyperliquidStreamKey(
                jsonObject("""{"channel":"user","data":{"user":"0xabc","fills":[]}}"""),
            ),
        )
        assertEquals(
            "orderUpdates",
            hyperliquidStreamKey(jsonObject("""{"channel":"orderUpdates","data":[]}""")),
        )
        assertEquals(
            "clearinghouseState|0xabc|xyz",
            hyperliquidStreamKey(
                jsonObject(
                    """{"channel":"clearinghouseState","data":{"user":"0xabc","dex":"xyz","clearinghouseState":{}}}""",
                ),
            ),
        )
        assertEquals(
            "allDexsClearinghouseState|0xabc",
            hyperliquidStreamKey(
                jsonObject("""{"channel":"allDexsClearinghouseState","data":{"user":"0xabc"}}"""),
            ),
        )
        assertEquals(
            "allDexsAssetCtxs",
            hyperliquidStreamKey(jsonObject("""{"channel":"allDexsAssetCtxs","data":{"ctxs":[]}}""")),
        )
        assertEquals(
            "notification",
            hyperliquidStreamKey(jsonObject("""{"channel":"notification","data":{"notification":"hi"}}""")),
        )
        assertNull(hyperliquidStreamKey(jsonObject("""{"channel":"subscriptionResponse","data":{}}""")))
        assertNull(hyperliquidStreamKey(jsonObject("""{"channel":"pong"}""")))
    }
}
