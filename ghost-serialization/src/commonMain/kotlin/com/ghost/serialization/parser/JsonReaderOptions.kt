package com.ghost.serialization.parser

import com.ghost.serialization.parser.GhostJsonConstants.BYTE_MASK
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_16
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_24
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_8
import com.ghost.serialization.parser.GhostJsonConstants.SPACE_INT
import okio.ByteString.Companion.encodeUtf8

/**
 * Dispatch options for optimized JSON field identification.
 * Uses a 4-byte hashing engine to minimize collisions during field lookup.
 */
class JsonReaderOptions(
    @PublishedApi internal val rawBytes: Array<okio.ByteString>,
    val writerHeaders: Array<okio.ByteString>,
    val writerHeadersWithComma: Array<okio.ByteString>,
    val writerFirstHeaders: Array<okio.ByteString>,
    @PublishedApi internal val rawInts: Array<IntArray>,
    @PublishedApi internal val shift: Int,
    @PublishedApi internal val multiplier: Int
) {
    @PublishedApi
    internal val dispatch = IntArray(DISPATCH_TABLE_SIZE) { -1 }

    init {
        val tableMask = DISPATCH_TABLE_SIZE - 1
        for (i in rawBytes.indices) {
            val bytes = rawBytes[i]
            if (bytes.size > 0) {
                // Multi-byte hashing: Uses up to 4 bytes to identify the dispatch key.
                var key = 0
                if (bytes.size >= 1) key = key or (bytes[0].toInt() and BYTE_MASK)
                if (bytes.size >= 2) key = key or ((bytes[1].toInt() and BYTE_MASK) shl SHIFT_8)
                if (bytes.size >= 3) key = key or ((bytes[2].toInt() and BYTE_MASK) shl SHIFT_16)
                if (bytes.size >= 4) key = key or ((bytes[3].toInt() and BYTE_MASK) shl SHIFT_24)

                val h = ((key * multiplier + bytes.size) shr shift) and tableMask
                if (dispatch[h] == -1) dispatch[h] = i
            }
        }
    }

    companion object {
        /**
         * Creates an optimized options configuration for a predefined set of field names.
         */
        fun of(vararg names: String): JsonReaderOptions = of(0, 31, *names)

        fun of(shift: Int, multiplier: Int, vararg names: String): JsonReaderOptions {
            val rawBytes = Array(names.size) { names[it].encodeUtf8() }

            val writerHeaders = Array(names.size) {
                "\"${escapeJson(names[it])}\":".encodeUtf8()
            }
            val writerHeadersWithComma = Array(names.size) {
                ",\"${escapeJson(names[it])}\":".encodeUtf8()
            }
            val writerFirstHeaders = Array(names.size) {
                "{\"${escapeJson(names[it])}\":".encodeUtf8()
            }

            val rawInts = Array(names.size) { index ->
                val bytes = rawBytes[index]
                IntArray(bytes.size) { bytes[it].toInt() and BYTE_MASK }
            }

            return JsonReaderOptions(
                rawBytes,
                writerHeaders,
                writerHeadersWithComma,
                writerFirstHeaders,
                rawInts,
                shift, multiplier
            )
        }

        private fun escapeJson(s: String): String {
            val sb = StringBuilder()
            for (c in s) {
                when (c) {
                    '\"' -> sb.append("\\\"")
                    '\\' -> sb.append("\\\\")
                    '\b' -> sb.append("\\b")
                    '\u000C' -> sb.append("\\f")
                    '\n' -> sb.append("\\n")
                    '\r' -> sb.append("\\r")
                    '\t' -> sb.append("\\t")
                    else -> {
                        if (c.code < SPACE_INT) {
                            val hex = c.code.toString(16)
                            sb.append("\\u00").append(if (hex.length < 2) "0$hex" else hex)
                        } else {
                            sb.append(c)
                        }
                    }
                }
            }
            return sb.toString()
        }

        private const val DISPATCH_TABLE_SIZE = 1024
    }
}
