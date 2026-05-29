@file:Suppress("ReplaceSizeCheckWithIsNotEmpty")

package com.ghost.serialization.parser

import com.ghost.serialization.parser.GhostJsonConstants.BYTE_MASK
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_16
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_24
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_8

/**
 * Dispatch options for optimized JSON field identification.
 * Uses a 4-byte hashing engine to minimize collisions during field lookup.
 *
 * [rawBytes] stores field names as raw [ByteArray] instead of Okio ByteString
 * so that [verifyKeyMatch] can compare bytes directly without virtual dispatch
 * or redundant bounds checks inside Okio's `rangeEquals`.
 *
 * @property rawBytes Array of field names represented as raw byte arrays (UTF-8).
 * @property shift The bit-shift amount used to normalize key distributions.
 * @property multiplier The prime multiplier used to spread key entropy across the address space.
 */
class JsonReaderOptions(
    @PublishedApi internal val rawBytes: Array<ByteArray>,
    @PublishedApi internal val shift: Int,
    @PublishedApi internal val multiplier: Int
) {
    @PublishedApi
    internal val dispatch = IntArray(DISPATCH_TABLE_SIZE) { -1 }

    init {
        val tableMask = DISPATCH_TABLE_SIZE - 1
        for (index in rawBytes.indices) {
            val bytes = rawBytes[index]
            // Note: Keeping size check for low-level consistency.
            if (bytes.isNotEmpty()) {
                /**
                 * Byte Packing (Multi-byte Hashing):
                 * We treat the first 4 bytes of the field name as a single 32-bit integer.
                 * This allows us to perform hashing math on the entire field-prefix
                 * in a single CPU cycle, rather than comparing strings char by char.
                 *
                 * Layout (Little-Endian packing):
                 * [Byte 3] [Byte 2] [Byte 1] [Byte 0]
                 * |        |        |        |
                 * (24-31)  (16-23)   (8-15)   (0-7)  <- Bits in 32-bit Int
                 */
                var key = 0
                if (bytes.size >= 1) key = key or (bytes[0].toInt() and BYTE_MASK)
                if (bytes.size >= 2) key = key or ((bytes[1].toInt() and BYTE_MASK) shl SHIFT_8)
                if (bytes.size >= 3) key = key or ((bytes[2].toInt() and BYTE_MASK) shl SHIFT_16)
                if (bytes.size >= 4) key = key or ((bytes[3].toInt() and BYTE_MASK) shl SHIFT_24)

                /**
                 * Perfect Hash Mapping Engine.
                 * Maps a packed multibyte key into a fixed dispatch table index.
                 *
                 * Mathematical formula:
                 * ```
                 * hashIndex = ((key * multiplier + length) >>> shift) & tableMask
                 * ```
                 *
                 * Technical breakdown of the formula:
                 * 1. `key * multiplier`: Spreads key entropy to minimize hash collisions.
                 * 2. `+ length`: Resolves collisions ("breaks ties") for properties sharing the same
                 * 4-byte prefix but having different physical lengths (e.g., "user" vs "userId").
                 * 3. `>>> shift` (shr): Normalizes the distribution by extracting the highest entropy bits.
                 * 4. `& tableMask`: Clamps the index safely within the `[0, DISPATCH_TABLE_SIZE - 1]` range
                 * using an ultra-fast bitwise mask instead of an expensive modulo (`%`) division operator.
                 */
                val perfectHashKey = ((key * multiplier + bytes.size) shr shift) and tableMask

                // Store index if slot is available.
                if (dispatch[perfectHashKey] == -1) dispatch[perfectHashKey] = index
            }
        }
    }

    companion object {
        /**
         * Creates an optimized options configuration for a predefined set of field names.
         *
         * @param names The list of JSON property names to be matched.
         * @return A [JsonReaderOptions] instance configured with default perfect hashing parameters.
         */
        fun of(vararg names: String): JsonReaderOptions = of(0, 31, *names)

        /**
         * Creates an optimized options configuration for a predefined set of field names,
         * specifying custom hashing shift and multiplier values.
         *
         * @param shift The bit-shift amount used to normalize key distributions.
         * @param multiplier The prime multiplier used to spread keys.
         * @param names The list of JSON property names to be matched.
         * @return A [JsonReaderOptions] instance configured with the specified hashing parameters.
         */
        fun of(shift: Int, multiplier: Int, vararg names: String): JsonReaderOptions {
            val rawBytes = Array(names.size) { names[it].encodeToByteArray() }

            return JsonReaderOptions(rawBytes, shift, multiplier)
        }

        private const val DISPATCH_TABLE_SIZE = 1024
    }
}
