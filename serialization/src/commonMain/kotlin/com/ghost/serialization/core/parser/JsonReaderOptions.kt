package com.ghostserializer.core.parser

/**
 * Dispatch options for optimized JSON field identification.
 * Uses a 4-byte hashing engine to minimize collisions during field lookup.
 */
class JsonReaderOptions(
    val rawBytes: Array<ByteArray>,
    val writerHeaders: Array<okio.ByteString>,
    val writerHeadersWithComma: Array<okio.ByteString>,
    @PublishedApi internal val shift: Int,
    @PublishedApi internal val multiplier: Int
) {
    @PublishedApi internal val dispatch = IntArray(1024) { -1 }

    init {
        for (i in rawBytes.indices) {
            val bytes = rawBytes[i]
            if (bytes.isNotEmpty()) {
                // Multi-byte hashing: Uses up to 4 bytes to identify the dispatch key.
                // This significantly reduces collisions compared to single-byte hashing strategies.
                var key = 0
                if (bytes.size >= 1) key = key or (bytes[0].toInt() and 0xFF)
                if (bytes.size >= 2) key = key or ((bytes[1].toInt() and 0xFF) shl 8)
                if (bytes.size >= 3) key = key or ((bytes[2].toInt() and 0xFF) shl 16)
                if (bytes.size >= 4) key = key or ((bytes[3].toInt() and 0xFF) shl 24)

                val h = ((key * multiplier + bytes.size) shr shift) and 1023
                if (dispatch[h] == -1) {
                    dispatch[h] = i
                }
            }
        }
    }

    companion object {
        /**
         * Creates an optimized options configuration for a predefined set of field names.
         */
        fun of(vararg names: String): JsonReaderOptions = of(0, 31, *names)

        fun of(shift: Int, multiplier: Int, vararg names: String): JsonReaderOptions {
            val rawBytes = Array(names.size) { names[it].encodeToByteArray() }

            val writerHeaders = Array(names.size) {
                okio.ByteString.Companion.run { "\"${names[it]}\":".encodeUtf8() }
            }
            val writerHeadersWithComma = Array(names.size) {
                okio.ByteString.Companion.run { ",\"${names[it]}\":".encodeUtf8() }
            }

            return JsonReaderOptions(
                rawBytes, writerHeaders, writerHeadersWithComma,
                shift, multiplier
            )
        }
    }
}