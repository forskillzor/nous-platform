/*
 * Copyright (C) 2026 Sergey Orlov
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package com.aandios.nous.provider.hyperliquid

/**
 * Minimal deterministic msgpack encoder for Hyperliquid actions.
 * Map insertion order is preserved because the exchange hashes the exact byte stream.
 */
internal object HlMsgPack {
    fun encode(value: Any?): ByteArray {
        val writer = Writer()
        writer.writeValue(value)
        return writer.toByteArray()
    }

    private class Writer {
        private var buffer = ByteArray(128)
        private var size = 0

        fun writeValue(value: Any?) {
            when (value) {
                null -> writeByte(0xC0)
                is Boolean -> writeByte(if (value) 0xC3 else 0xC2)
                is Int -> writeInteger(value.toLong())
                is Long -> writeInteger(value)
                is String -> writeString(value)
                is Map<*, *> -> writeMap(value)
                is List<*> -> writeArray(value)
                else -> error("Unsupported Hyperliquid msgpack value: ${value::class}")
            }
        }

        fun toByteArray(): ByteArray = buffer.copyOf(size)

        private fun writeInteger(value: Long) {
            if (value >= 0) {
                when {
                    value <= 0x7F -> writeByte(value.toInt())
                    value <= 0xFF -> {
                        writeByte(0xCC)
                        writeByte(value.toInt())
                    }
                    value <= 0xFFFF -> {
                        writeByte(0xCD)
                        writeUInt16(value.toInt())
                    }
                    value <= 0xFFFF_FFFFL -> {
                        writeByte(0xCE)
                        writeUInt32(value)
                    }
                    else -> {
                        writeByte(0xCF)
                        writeUInt64(value)
                    }
                }
            } else {
                when {
                    value >= -32 -> writeByte((0xE0 or (value + 32).toInt()) and 0xFF)
                    value >= -128 -> {
                        writeByte(0xD0)
                        writeByte(value.toInt() and 0xFF)
                    }
                    value >= -32768 -> {
                        writeByte(0xD1)
                        writeUInt16(value.toInt() and 0xFFFF)
                    }
                    value >= Int.MIN_VALUE.toLong() -> {
                        writeByte(0xD2)
                        writeUInt32(value.toInt().toLong() and 0xFFFF_FFFFL)
                    }
                    else -> {
                        writeByte(0xD3)
                        writeUInt64(value)
                    }
                }
            }
        }

        private fun writeString(value: String) {
            val bytes = value.encodeToByteArray()
            when {
                bytes.size <= 0x1F -> writeByte(0xA0 or bytes.size)
                bytes.size <= 0xFF -> {
                    writeByte(0xD9)
                    writeByte(bytes.size)
                }
                bytes.size <= 0xFFFF -> {
                    writeByte(0xDA)
                    writeUInt16(bytes.size)
                }
                else -> {
                    writeByte(0xDB)
                    writeUInt32(bytes.size.toLong())
                }
            }
            writeBytes(bytes)
        }

        private fun writeArray(values: List<*>) {
            when {
                values.size <= 0x0F -> writeByte(0x90 or values.size)
                values.size <= 0xFFFF -> {
                    writeByte(0xDC)
                    writeUInt16(values.size)
                }
                else -> {
                    writeByte(0xDD)
                    writeUInt32(values.size.toLong())
                }
            }
            values.forEach(::writeValue)
        }

        private fun writeMap(values: Map<*, *>) {
            when {
                values.size <= 0x0F -> writeByte(0x80 or values.size)
                values.size <= 0xFFFF -> {
                    writeByte(0xDE)
                    writeUInt16(values.size)
                }
                else -> {
                    writeByte(0xDF)
                    writeUInt32(values.size.toLong())
                }
            }
            values.forEach { (key, value) ->
                val name = key as? String ?: error("Hyperliquid msgpack map keys must be strings")
                writeString(name)
                writeValue(value)
            }
        }

        private fun writeUInt16(value: Int) {
            writeByte((value ushr 8) and 0xFF)
            writeByte(value and 0xFF)
        }

        private fun writeUInt32(value: Long) {
            writeByte(((value ushr 24) and 0xFF).toInt())
            writeByte(((value ushr 16) and 0xFF).toInt())
            writeByte(((value ushr 8) and 0xFF).toInt())
            writeByte((value and 0xFF).toInt())
        }

        private fun writeUInt64(value: Long) {
            for (shift in 56 downTo 0 step 8) {
                writeByte(((value ushr shift) and 0xFF).toInt())
            }
        }

        private fun writeByte(value: Int) {
            ensure(1)
            buffer[size++] = value.toByte()
        }

        private fun writeBytes(bytes: ByteArray) {
            ensure(bytes.size)
            bytes.copyInto(buffer, size)
            size += bytes.size
        }

        private fun ensure(extra: Int) {
            if (size + extra <= buffer.size) return
            var capacity = buffer.size
            while (capacity < size + extra) capacity *= 2
            buffer = buffer.copyOf(capacity)
        }
    }
}
