/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.feature.localstorage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import com.aandios.nous.core.storage.StateStore

class LocalStorage(val dbPath: String = DEFAULT_PATH) : StateStore {

    private val dbFile = File(dbPath)
    private var connection: Connection? = null

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
                    symbol TEXT NOT NULL,
                    timeframe TEXT NOT NULL,
                    timestamp INTEGER NOT NULL,
                    open REAL, high REAL, low REAL, close REAL, volume REAL,
                    PRIMARY KEY (symbol, timeframe, timestamp)
                )
            """)
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS footprint_cache (
                    symbol TEXT NOT NULL,
                    start_time INTEGER NOT NULL,
                    end_time INTEGER NOT NULL,
                    json_data TEXT NOT NULL,
                    PRIMARY KEY (symbol, start_time)
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

    suspend fun saveCandles(symbol: String, timeframe: String, candles: List<CandleCache>) {
        if (candles.isEmpty()) return
        val conn = getConnection()
        conn.prepareStatement("INSERT OR REPLACE INTO candles_cache (symbol, timeframe, timestamp, open, high, low, close, volume) VALUES (?, ?, ?, ?, ?, ?, ?, ?)").use { stmt ->
            candles.forEach { c ->
                stmt.setString(1, symbol); stmt.setString(2, timeframe)
                stmt.setLong(3, c.timestamp)
                stmt.setDouble(4, c.open); stmt.setDouble(5, c.high)
                stmt.setDouble(6, c.low); stmt.setDouble(7, c.close)
                stmt.setDouble(8, c.volume)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
        // Update meta
        val metaKey = "candles_${symbol}_$timeframe"
        conn.prepareStatement("SELECT MIN(timestamp), MAX(timestamp), COUNT(*) FROM candles_cache WHERE symbol = ? AND timeframe = ?").use { stmt ->
            stmt.setString(1, symbol); stmt.setString(2, timeframe)
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

    suspend fun getCandles(symbol: String, timeframe: String, limit: Int = 500): List<CandleCache> {
        val conn = getConnection()
        return conn.prepareStatement("SELECT * FROM candles_cache WHERE symbol = ? AND timeframe = ? ORDER BY timestamp ASC LIMIT ?").use {
            it.setString(1, symbol); it.setString(2, timeframe); it.setInt(3, limit)
            val rs = it.executeQuery()
            val result = mutableListOf<CandleCache>()
            while (rs.next()) {
                result.add(CandleCache(rs.getLong("timestamp"), rs.getDouble("open"), rs.getDouble("high"), rs.getDouble("low"), rs.getDouble("close"), rs.getDouble("volume")))
            }
            result
        }
    }

    suspend fun getCandlesCount(symbol: String, timeframe: String): Long {
        val conn = getConnection()
        return conn.prepareStatement("SELECT COUNT(*) FROM candles_cache WHERE symbol = ? AND timeframe = ?").use {
            it.setString(1, symbol); it.setString(2, timeframe)
            val rs = it.executeQuery()
            if (rs.next()) rs.getLong(1) else 0
        }
    }

    suspend fun clearCandles(symbol: String? = null, timeframe: String? = null, olderThan: Long? = null) {
        val conn = getConnection()
        val sb = StringBuilder("DELETE FROM candles_cache WHERE 1=1")
        symbol?.let { sb.append(" AND symbol = '$it'") }
        timeframe?.let { sb.append(" AND timeframe = '$it'") }
        olderThan?.let { sb.append(" AND timestamp < $it") }
        conn.createStatement().execute(sb.toString())
        // Remove meta entries
        conn.createStatement().execute("DELETE FROM cache_meta WHERE key LIKE 'candles_%' AND (SELECT COUNT(*) FROM candles_cache c WHERE c.symbol = cache_meta.symbol) = 0")
    }

    // ============ Footprint ============

    suspend fun saveFootprintCandles(symbol: String, candles: List<FootprintCache>) {
        if (candles.isEmpty()) return
        val conn = getConnection()
        conn.prepareStatement("INSERT OR REPLACE INTO footprint_cache (symbol, start_time, end_time, json_data) VALUES (?, ?, ?, ?)").use { stmt ->
            candles.forEach { c ->
                stmt.setString(1, symbol); stmt.setLong(2, c.startTime)
                stmt.setLong(3, c.endTime); stmt.setString(4, c.jsonData)
                stmt.addBatch()
            }
            stmt.executeBatch()
        }
        val metaKey = "footprint_$symbol"
        conn.prepareStatement("SELECT MIN(start_time), MAX(end_time), COUNT(*) FROM footprint_cache WHERE symbol = ?").use { stmt ->
            stmt.setString(1, symbol)
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

    suspend fun getFootprintCandles(symbol: String, limit: Int = 60): List<FootprintCache> {
        val conn = getConnection()
        return conn.prepareStatement("SELECT * FROM footprint_cache WHERE symbol = ? ORDER BY start_time DESC LIMIT ?").use {
            it.setString(1, symbol); it.setInt(2, limit)
            val rs = it.executeQuery()
            val result = mutableListOf<FootprintCache>()
            while (rs.next()) {
                result.add(FootprintCache(rs.getLong("start_time"), rs.getLong("end_time"), rs.getString("json_data")))
            }
            result.reversed()
        }
    }

    suspend fun clearFootprint(symbol: String? = null, olderThan: Long? = null) {
        val conn = getConnection()
        val sb = StringBuilder("DELETE FROM footprint_cache WHERE 1=1")
        symbol?.let { sb.append(" AND symbol = '$it'") }
        olderThan?.let { sb.append(" AND start_time < $it") }
        conn.createStatement().execute(sb.toString())
        conn.createStatement().execute("DELETE FROM cache_meta WHERE key LIKE 'footprint_%' AND (SELECT COUNT(*) FROM footprint_cache f WHERE f.symbol = cache_meta.symbol) = 0")
    }

    // ============ Stats ============

    data class CacheStats(
        val key: String,
        val symbol: String,
        val count: Long,
        val firstTs: Long,
        val lastTs: Long,
        val sizeBytes: Long,
        val durationMs: Long = 0L
    )

    suspend fun getDetailedStats(): Pair<List<CacheStats>, Long> {
        val conn = getConnection()
        val stats = mutableListOf<CacheStats>()

        // Candles per symbol+timeframe
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT symbol, timeframe, COUNT(*) as cnt, MIN(timestamp) as first_ts, MAX(timestamp) as last_ts FROM candles_cache GROUP BY symbol, timeframe ORDER BY cnt DESC")
            while (rs.next()) {
                val sym = rs.getString("symbol")
                val tf = rs.getString("timeframe")
                val cnt = rs.getLong("cnt")
                val rowSize = (8 + 5 * 8 + 8).toLong()
                stats.add(CacheStats("Candles $sym $tf", sym, cnt, rs.getLong("first_ts"), rs.getLong("last_ts"), cnt * rowSize, rs.getLong("last_ts") - rs.getLong("first_ts")))
            }
        }

        // Footprint per symbol
        conn.createStatement().use { stmt ->
            val rs = stmt.executeQuery("SELECT symbol, COUNT(*) as cnt, MIN(start_time) as first_ts, MAX(end_time) as last_ts, AVG(LENGTH(json_data)) as avg_len FROM footprint_cache GROUP BY symbol ORDER BY cnt DESC")
            while (rs.next()) {
                val sym = rs.getString("symbol")
                val cnt = rs.getLong("cnt")
                val avgLen = rs.getDouble("avg_len")
                val size = if (cnt > 0) (cnt * avgLen).toLong() else 0L
                stats.add(CacheStats("Footprint $sym", sym, cnt, rs.getLong("first_ts"), rs.getLong("last_ts"), size, rs.getLong("last_ts") - rs.getLong("first_ts")))
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

    data class CandleCache(val timestamp: Long, val open: Double, val high: Double, val low: Double, val close: Double, val volume: Double)
    data class FootprintCache(val startTime: Long, val endTime: Long, val jsonData: String)

    companion object {
        val DEFAULT_PATH = "${System.getProperty("user.home")}/.nous/storage.db"
    }
}
