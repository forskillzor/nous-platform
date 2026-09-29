/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.localstorage

import com.aandios.nous.api.market.model.Candle
import com.aandios.nous.api.market.model.FootprintCandle
import com.aandios.nous.core.domain.cache.CandleCacheStore
import com.aandios.nous.core.domain.cache.FootprintCacheStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import com.aandios.nous.core.storage.StateStore

class LocalStorage(val dbPath: String = DEFAULT_PATH) : StateStore, CandleCacheStore, FootprintCacheStore {

    private val dbFile = File(dbPath)
    private var connection: Connection? = null
    private val json = Json { ignoreUnknownKeys = true }

    private suspend fun getConnection(): Connection {
        val conn = connection
        if (conn != null && !conn.isClosed) return conn
        return withContext(Dispatchers.IO) {
            dbFile.parentFile?.mkdirs()
            val c = DriverManager.getConnection("jdbc:sqlite:$dbPath")
            c.createStatement().use { stmt ->
                stmt.execute("PRAGMA journal_mode=WAL")
                stmt.execute("PRAGMA synchronous=NORMAL")
                stmt.execute("PRAGMA foreign_keys=ON")
            }
            ensureTables(c)
            migrateSchema(c)
            connection = c
            c
        }
    }

    private fun ensureTables(conn: Connection) {
        conn.createStatement().use { stmt ->
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS settings (
                    key TEXT PRIMARY KEY,
                    value TEXT NOT NULL
                )
            """)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS candles_cache (
                    exchange TEXT NOT NULL,
                    symbol TEXT NOT NULL,
                    timeframe TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    open REAL, high REAL, low REAL, close REAL, volume REAL,
                    PRIMARY KEY (exchange, symbol, timeframe, timestamp)
                )
            """)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS footprint_cache (
                    exchange TEXT NOT NULL,
                    symbol TEXT NOT NULL,
                    start_time INTEGER NOT NULL,
                    end_time INTEGER NOT NULL,
                    json_data TEXT NOT NULL,
                    PRIMARY KEY (exchange, symbol, start_time)
                )
            """)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS cache_meta (
                    key TEXT PRIMARY KEY,
                    symbol TEXT NOT NULL,
                    first_ts INTEGER NOT NULL,
                    last_ts INTEGER NOT NULL,
                    count INTEGER NOT NULL
                )
            """)
        }
    }

    /**
     * Миграция старых БД (без колонки exchange): пересоздаём таблицы с exchange
     * в первичном ключе, существующие данные помечаем как Binance.
     */
    private fun migrateSchema(conn: Connection) {
        if (!hasColumn(conn, "candles_cache", "exchange")) {
            conn.createStatement().use { stmt ->
                stmt.execute("ALTER TABLE candles_cache RENAME TO candles_cache_legacy")
                stmt.execute("""
                    CREATE TABLE candles_cache (
                        exchange TEXT NOT NULL,
                        symbol TEXT NOT NULL,
                        timeframe TEXT NOT NULL,
                        timestamp INTEGER NOT NULL,
                        open REAL, high REAL, low REAL, close REAL, volume REAL,
                        PRIMARY KEY (exchange, symbol, timeframe, timestamp)
                    )
                """)
                stmt.execute("""
                    INSERT INTO candles_cache (exchange, symbol, timeframe, timestamp, open, high, low, close, volume)
                    SELECT 'Binance', symbol, timeframe, timestamp, open, high, low, close, volume FROM candles_cache_legacy
                """)
                stmt.execute("DROP TABLE candles_cache_legacy")
            }
        }
        if (!hasColumn(conn, "footprint_cache", "exchange")) {
            conn.createStatement().use { stmt ->
                stmt.execute("ALTER TABLE footprint_cache RENAME TO footprint_cache_legacy")
                stmt.execute("""
                    CREATE TABLE footprint_cache (
                        exchange TEXT NOT NULL,
                        symbol TEXT NOT NULL,
                        start_time INTEGER NOT NULL,
                        end_time INTEGER NOT NULL,
                        json_data TEXT NOT NULL,
                        PRIMARY KEY (exchange, symbol, start_time)
                    )
                """)
                stmt.execute("""
                    INSERT INTO footprint_cache (exchange, symbol, start_time, end_time, json_data)
                    SELECT 'Binance', symbol, start_time, end_time, json_data FROM footprint_cache_legacy
                """)
                stmt.execute("DROP TABLE footprint_cache_legacy")
            }
        }
    }

    private fun hasColumn(conn: Connection, table: String, column: String): Boolean {
        conn.createStatement().use { stmt ->
            stmt.executeQuery("PRAGMA table_info($table)").use { rs ->
                while (rs.next()) {
                    if (rs.getString("name") == column) return true
                }
            }
        }
        return false
    }

    // ============ State Save/Load ============

    suspend fun saveChartState(symbol: String, timeframe: String, mode: String) {
        putString("chart_symbol", symbol)
        putString("chart_timeframe", timeframe)
        putString("chart_mode", mode)
    }

    data class ChartState(val symbol: String, val timeframe: String, val mode: String)
    suspend fun loadChartState(): ChartState? {
        val sym = getString("chart_symbol") ?: return null
        val tf = getString("chart_timeframe") ?: return null
        val mode = getString("chart_mode") ?: return null
        return ChartState(sym, tf, mode)
    }

    suspend fun saveDomOptions(json: String) { putString("dom_options", json) }
    suspend fun loadDomOptions(): String? = getString("dom_options")

    suspend fun saveTradesOptions(json: String) { putString("trades_options", json) }
    suspend fun loadTradesOptions(): String? = getString("trades_options")

    override suspend fun putString(key: String, value: String) {
        val conn = getConnection()
        conn.prepareStatement("INSERT OR REPLACE INTO settings (key, value) VALUES (?, ?)").use {
            it.setString(1, key); it.setString(2, value); it.execute()
        }
    }

    override suspend fun getString(key: String): String? {
        val conn = getConnection()
        return conn.prepareStatement("SELECT value FROM settings WHERE key = ?").use {
            it.setString(1, key)
            val rs = it.executeQuery()
            if (rs.next()) rs.getString("value") else null
        }
    }

    // ============ Candles ============

    override suspend fun saveCandles(exchange: String, symbol: String, timeframe: String, candles: List<Candle>) {
        if (candles.isEmpty()) return
        val conn = getConnection()
        conn.prepareStatement("INSERT OR REPLACE INTO candles_cache (exchange, symbol, timeframe, timestamp, open, high, low, close, volume) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)").use { stmt ->
            candles.forEach { c ->
                stmt.setString(1, exchange); stmt.setString(2, symbol); stmt.setString(3, timeframe)
                stmt.setLong(4, c.timestamp)
                stmt.setDouble(5, c.open.toDouble()); stmt.setDouble(6, c.high.toDouble())
                stmt.setDouble(7, c.low.toDouble()); stmt.setDouble(8, c.close.toDouble())
                stmt.setDouble(9, c.volume.toDouble())
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
        updateCandlesMeta(exchange, symbol, timeframe)
    }

    private suspend fun updateCandlesMeta(exchange: String, symbol: String, timeframe: String) {
        val conn = getConnection()
        val metaKey = "candles_${exchange}_${symbol}_$timeframe"
        conn.prepareStatement("SELECT MIN(timestamp), MAX(timestamp), COUNT(*) FROM candles_cache WHERE exchange = ? AND symbol = ? AND timeframe = ?").use { stmt ->
            stmt.setString(1, exchange); stmt.setString(2, symbol); stmt.setString(3, timeframe)
            val rs = stmt.executeQuery()
            if (rs.next() && rs.getLong(1) > 0) {
                conn.prepareStatement("INSERT OR REPLACE INTO cache_meta (key, symbol, first_ts, last_ts, count) VALUES (?, ?, ?, ?, ?)").use { u ->
                    u.setString(1, metaKey); u.setString(2, symbol)
                    u.setLong(3, rs.getLong(1)); u.setLong(4, rs.getLong(2))
                    u.setInt(5, rs.getInt(3))
                    u.execute()
                }
            }
        }
    }

    override suspend fun getCandles(exchange: String, symbol: String, timeframe: String, limit: Int): List<Candle> {
        val conn = getConnection()
        return conn.prepareStatement("SELECT timestamp, open, high, low, close, volume FROM candles_cache WHERE exchange = ? AND symbol = ? AND timeframe = ? ORDER BY timestamp DESC LIMIT ?").use {
            it.setString(1, exchange); it.setString(2, symbol); it.setString(3, timeframe); it.setInt(4, limit)
            val rs = it.executeQuery()
            val result = mutableListOf<Candle>()
            while (rs.next()) {
                result.add(
                    Candle(
                        open = rs.getDouble("open").toFloat(),
                        high = rs.getDouble("high").toFloat(),
                        close = rs.getDouble("close").toFloat(),
                        low = rs.getDouble("low").toFloat(),
                        timestamp = rs.getLong("timestamp"),
                        volume = rs.getDouble("volume").toFloat(),
                    )
                )
            }
            result.reversed()
        }
    }

    suspend fun clearCandles(symbol: String? = null, timeframe: String? = null, olderThan: Long? = null, exchange: String? = null) {
        val conn = getConnection()
        val conditions = mutableListOf<String>()
        val stringParams = mutableListOf<Pair<Int, String>>()
        var index = 1
        exchange?.let { conditions += "exchange = ?"; stringParams += index++ to it }
        symbol?.let { conditions += "symbol = ?"; stringParams += index++ to it }
        timeframe?.let { conditions += "timeframe = ?"; stringParams += index++ to it }
        val olderThanIndex = if (olderThan != null) { conditions += "timestamp < ?"; index } else -1
        val where = if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND ")
        conn.prepareStatement("DELETE FROM candles_cache$where").use { st ->
            stringParams.forEach { (i, v) -> st.setString(i, v) }
            if (olderThanIndex > 0) st.setLong(olderThanIndex, olderThan!!)
            st.execute()
        }
    }

    // ============ Footprint ============

    override suspend fun saveFootprintCandles(exchange: String, symbol: String, candles: List<FootprintCandle>) {
        if (candles.isEmpty()) return
        val conn = getConnection()
        conn.prepareStatement("INSERT OR REPLACE INTO footprint_cache (exchange, symbol, start_time, end_time, json_data) VALUES (?, ?, ?, ?, ?)").use { stmt ->
            candles.forEach { c ->
                stmt.setString(1, exchange); stmt.setString(2, symbol)
                stmt.setLong(3, c.startTime); stmt.setLong(4, c.endTime)
                stmt.setString(5, json.encodeToString(c))
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
        updateFootprintMeta(exchange, symbol)
    }

    private suspend fun updateFootprintMeta(exchange: String, symbol: String) {
        val conn = getConnection()
        val metaKey = "footprint_${exchange}_$symbol"
        conn.prepareStatement("SELECT MIN(start_time), MAX(end_time), COUNT(*) FROM footprint_cache WHERE exchange = ? AND symbol = ?").use { stmt ->
            stmt.setString(1, exchange); stmt.setString(2, symbol)
            val rs = stmt.executeQuery()
            if (rs.next() && rs.getLong(1) > 0) {
                conn.prepareStatement("INSERT OR REPLACE INTO cache_meta (key, symbol, first_ts, last_ts, count) VALUES (?, ?, ?, ?, ?)").use { u ->
                    u.setString(1, metaKey); u.setString(2, symbol)
                    u.setLong(3, rs.getLong(1)); u.setLong(4, rs.getLong(2))
                    u.setInt(5, rs.getInt(3))
                    u.execute()
                }
            }
        }
    }

    override suspend fun getFootprintCandles(exchange: String, symbol: String, limit: Int): List<FootprintCandle> {
        val conn = getConnection()
        return conn.prepareStatement("SELECT json_data FROM footprint_cache WHERE exchange = ? AND symbol = ? ORDER BY start_time DESC LIMIT ?").use {
            it.setString(1, exchange); it.setString(2, symbol); it.setInt(3, limit)
            val rs = it.executeQuery()
            val result = mutableListOf<FootprintCandle>()
            while (rs.next()) {
                try {
                    result.add(json.decodeFromString<FootprintCandle>(rs.getString("json_data")))
                } catch (_: Exception) {
                    // пропускаем повреждённые записи
                }
            }
            result.reversed()
        }
    }

    suspend fun clearFootprint(symbol: String? = null, olderThan: Long? = null, exchange: String? = null) {
        val conn = getConnection()
        val conditions = mutableListOf<String>()
        val stringParams = mutableListOf<Pair<Int, String>>()
        var index = 1
        exchange?.let { conditions += "exchange = ?"; stringParams += index++ to it }
        symbol?.let { conditions += "symbol = ?"; stringParams += index++ to it }
        val olderThanIndex = if (olderThan != null) { conditions += "start_time < ?"; index } else -1
        val where = if (conditions.isEmpty()) "" else " WHERE " + conditions.joinToString(" AND ")
        conn.prepareStatement("DELETE FROM footprint_cache$where").use { st ->
            stringParams.forEach { (i, v) -> st.setString(i, v) }
            if (olderThanIndex > 0) st.setLong(olderThanIndex, olderThan!!)
            st.execute()
        }
    }

    // ============ Stats ============

    data class CacheStats(
        val key: String,
        val symbol: String,
        val count: Long,
        val firstTs: Long,
        val lastTs: Long,
        val sizeBytes: Long,
        val durationMs: Long = 0L,
        val exchange: String = "",
    )

    suspend fun getDetailedStats(): Pair<List<CacheStats>, Long> {
        val conn = getConnection()
        val stats = mutableListOf<CacheStats>()

        // Candles per exchange+symbol+timeframe
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT exchange, symbol, timeframe, COUNT(*) as cnt, MIN(timestamp) as first_ts, MAX(timestamp) as last_ts FROM candles_cache GROUP BY exchange, symbol, timeframe ORDER BY cnt DESC")
            while (rs.next()) {
                val exchange = rs.getString("exchange")
                val sym = rs.getString("symbol")
                val tf = rs.getString("timeframe")
                val cnt = rs.getLong("cnt")
                val rowSize = (8 + 5 * 8 + 8).toLong()
                stats.add(
                    CacheStats(
                        key = "Candles $exchange $sym $tf",
                        symbol = sym,
                        count = cnt,
                        firstTs = rs.getLong("first_ts"),
                        lastTs = rs.getLong("last_ts"),
                        sizeBytes = cnt * rowSize,
                        durationMs = rs.getLong("last_ts") - rs.getLong("first_ts"),
                        exchange = exchange,
                    )
                )
            }
        }

        // Footprint per exchange+symbol
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT exchange, symbol, COUNT(*) as cnt, MIN(start_time) as first_ts, MAX(end_time) as last_ts, AVG(LENGTH(json_data)) as avg_len FROM footprint_cache GROUP BY exchange, symbol ORDER BY cnt DESC")
            while (rs.next()) {
                val exchange = rs.getString("exchange")
                val sym = rs.getString("symbol")
                val cnt = rs.getLong("cnt")
                val avgLen = rs.getDouble("avg_len")
                val size = if (cnt > 0) (cnt * avgLen).toLong() else 0L
                stats.add(
                    CacheStats(
                        key = "Footprint $exchange $sym",
                        symbol = sym,
                        count = cnt,
                        firstTs = rs.getLong("first_ts"),
                        lastTs = rs.getLong("last_ts"),
                        sizeBytes = size,
                        durationMs = rs.getLong("last_ts") - rs.getLong("first_ts"),
                        exchange = exchange,
                    )
                )
            }
        }

        return stats to dbFile.length()
    }

    // ============ Clear All ============

    suspend fun clearSettings() { getConnection().createStatement().execute("DELETE FROM settings") }
    suspend fun clearAll() {
        getConnection().createStatement().use { stmt ->
            stmt.execute("DELETE FROM settings")
            stmt.execute("DELETE FROM candles_cache")
            stmt.execute("DELETE FROM footprint_cache")
            stmt.execute("DELETE FROM cache_meta")
        }
    }

    fun close() { connection?.close(); connection = null }

    companion object {
        val DEFAULT_PATH = "${System.getProperty("user.home")}/.nous/storage.db"
    }
}
