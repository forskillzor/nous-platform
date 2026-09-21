/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.localstorage

import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.api.market.model.FootprintLevel
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.sql.DriverManager
import kotlin.io.path.absolutePathString
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LocalStorageTest {

    private fun tempDbPath(): String {
        val dir = Files.createTempDirectory("nous-storage-test")
        return dir.resolve("storage.db").absolutePathString()
    }

    private fun candle(timestamp: Long, close: Float) = Candle(
        open = close - 1f,
        high = close + 1f,
        close = close,
        low = close - 2f,
        timestamp = timestamp,
        volume = 10f,
    )

    private fun footprint(startTime: Long, price: String) = FootprintCandle(
        exchange = "Binance",
        symbol = "BTCUSDT",
        timeframe = "1m",
        startTime = startTime,
        endTime = startTime + 60_000L,
        totalTicks = 3L,
        minPrice = price,
        maxPrice = price,
        levels = listOf(FootprintLevel(price = price, bidVolume = "1.5", askVolume = "2.5", bidCount = 2, askCount = 3)),
    )

    @Test
    fun `candles save and load roundtrip`() = runTest {
        val storage = LocalStorage(tempDbPath())
        val candles = listOf(candle(1_000L, 100f), candle(61_000L, 101f), candle(121_000L, 102f))

        storage.saveCandles("Binance", "BTCUSDT", "1m", candles)
        val loaded = storage.getCandles("Binance", "BTCUSDT", "1m", limit = 500)

        assertEquals(candles, loaded)
        storage.close()
    }

    @Test
    fun `candles are isolated by exchange symbol and timeframe`() = runTest {
        val storage = LocalStorage(tempDbPath())

        storage.saveCandles("Binance", "BTCUSDT", "1m", listOf(candle(1_000L, 100f)))
        storage.saveCandles("Binance", "BTCUSDT", "5m", listOf(candle(1_000L, 200f)))
        storage.saveCandles("MEXC", "BTCUSDT", "1m", listOf(candle(1_000L, 300f)))

        assertEquals(100f, storage.getCandles("Binance", "BTCUSDT", "1m").single().close)
        assertEquals(200f, storage.getCandles("Binance", "BTCUSDT", "5m").single().close)
        assertEquals(300f, storage.getCandles("MEXC", "BTCUSDT", "1m").single().close)
        storage.close()
    }

    @Test
    fun `getCandles returns most recent limit in ascending order`() = runTest {
        val storage = LocalStorage(tempDbPath())
        val candles = (0 until 10).map { candle(it * 60_000L, it.toFloat()) }

        storage.saveCandles("Binance", "BTCUSDT", "1m", candles)
        val loaded = storage.getCandles("Binance", "BTCUSDT", "1m", limit = 3)

        assertEquals(listOf(7f, 8f, 9f), loaded.map { it.close })
        storage.close()
    }

    @Test
    fun `footprint save and load roundtrip`() = runTest {
        val storage = LocalStorage(tempDbPath())
        val candles = listOf(footprint(1_000L, "100.5"), footprint(61_000L, "101.5"))

        storage.saveFootprintCandles("Binance", "BTCUSDT", candles)
        val loaded = storage.getFootprintCandles("Binance", "BTCUSDT", limit = 100)

        assertEquals(candles, loaded)
        storage.close()
    }

    @Test
    fun `clear removes only matching rows`() = runTest {
        val storage = LocalStorage(tempDbPath())
        storage.saveCandles("Binance", "BTCUSDT", "1m", listOf(candle(1_000L, 100f)))
        storage.saveCandles("MEXC", "BTCUSDT", "1m", listOf(candle(1_000L, 300f)))

        storage.clearCandles(exchange = "Binance", symbol = "BTCUSDT", timeframe = "1m")

        assertTrue(storage.getCandles("Binance", "BTCUSDT", "1m").isEmpty())
        assertEquals(1, storage.getCandles("MEXC", "BTCUSDT", "1m").size)
        storage.close()
    }

    @Test
    fun `detailed stats include exchange`() = runTest {
        val storage = LocalStorage(tempDbPath())
        storage.saveCandles("Binance", "BTCUSDT", "1m", listOf(candle(1_000L, 100f)))
        storage.saveFootprintCandles("MEXC", "ETHUSDT", listOf(footprint(1_000L, "10.5")))

        val (stats, _) = storage.getDetailedStats()

        assertEquals(2, stats.size)
        assertTrue(stats.any { it.key == "Candles Binance BTCUSDT 1m" && it.exchange == "Binance" })
        assertTrue(stats.any { it.key == "Footprint MEXC ETHUSDT" && it.exchange == "MEXC" })
        storage.close()
    }

    @Test
    fun `migrates legacy schema adding exchange column`() = runTest {
        val dbPath = tempDbPath()
        // Создаём БД старой схемы (без exchange) вручную
        DriverManager.getConnection("jdbc:sqlite:$dbPath").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.execute("""
                    CREATE TABLE candles_cache (
                        symbol TEXT NOT NULL,
                        timeframe TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        open REAL, high REAL, low REAL, close REAL, volume REAL,
                        PRIMARY KEY (symbol, timeframe, timestamp)
                    )
                """)
                stmt.execute("""
                    CREATE TABLE footprint_cache (
                        symbol TEXT NOT NULL,
                        start_time INTEGER NOT NULL,
                        end_time INTEGER NOT NULL,
                        json_data TEXT NOT NULL,
                        PRIMARY KEY (symbol, start_time)
                    )
                """)
                stmt.execute("INSERT INTO candles_cache (symbol, timeframe, timestamp, open, high, low, close, volume) VALUES ('BTCUSDT', '1m', 1000, 1.0, 2.0, 0.5, 1.5, 10.0)")
                stmt.execute("""INSERT INTO footprint_cache (symbol, start_time, end_time, json_data) VALUES ('BTCUSDT', 1000, 61000, '{"symbol":"BTCUSDT","startTime":1000,"endTime":61000,"levels":[]}')""")
            }
        }

        val storage = LocalStorage(dbPath)
        val candles = storage.getCandles("Binance", "BTCUSDT", "1m")
        val footprint = storage.getFootprintCandles("Binance", "BTCUSDT")

        assertEquals(1, candles.size)
        assertEquals(1.5f, candles.single().close)
        assertEquals(1, footprint.size)
        assertEquals(1000L, footprint.single().startTime)
        storage.close()
    }
}
