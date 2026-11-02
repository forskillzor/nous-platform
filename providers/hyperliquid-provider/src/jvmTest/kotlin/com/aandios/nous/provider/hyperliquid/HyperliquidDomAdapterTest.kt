/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

import com.aandios.nous.provider.hyperliquid.adapter.HyperliquidDomAdapter
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.test.Test
import kotlin.test.assertEquals

class HyperliquidDomAdapterTest {
    private fun bookMessage(levelCount: Int): kotlinx.serialization.json.JsonObject {
        val bids = buildJsonArray {
            repeat(levelCount) { index ->
                add(
                    buildJsonObject {
                        put("px", (100 - index).toString())
                        put("sz", (index + 1).toString())
                    },
                )
            }
        }
        val asks = buildJsonArray {
            repeat(levelCount) { index ->
                add(
                    buildJsonObject {
                        put("px", (101 + index).toString())
                        put("sz", (index + 1).toString())
                    },
                )
            }
        }
        return buildJsonObject {
            put("channel", "l2Book")
            put(
                "data",
                buildJsonObject {
                    put("coin", "BTC")
                    put("time", 123L)
                    put(
                        "levels",
                        buildJsonArray {
                            add(bids)
                            add(asks)
                        },
                    )
                },
            )
        }
    }

    @Test
    fun `caps l2 book windows at twenty levels`() = runTest {
        val streams = FakeHyperliquidStreams()
        val adapter = HyperliquidDomAdapter(streams)
        val next = async { adapter.subscribeToBookWindow("BTC", 200).first() }

        streams.emit("l2Book|BTC", bookMessage(25))

        val window = next.await()
        assertEquals(20, window.bids.size)
        assertEquals(20, window.asks.size)
        assertEquals(100.0, window.bids.first().price)
        assertEquals(101.0, window.asks.first().price)
    }

    @Test
    fun `returns only the requested number of levels`() = runTest {
        val streams = FakeHyperliquidStreams()
        val adapter = HyperliquidDomAdapter(streams)
        val next = async { adapter.subscribeToBookWindow("BTC", 5).first() }

        streams.emit("l2Book|BTC", bookMessage(25))

        assertEquals(5, next.await().bids.size)
    }
}
