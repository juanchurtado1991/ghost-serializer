@file:Suppress("ReplaceSizeCheckWithIsNotEmpty")

package com.ghost.serialization.parser

import com.ghost.serialization.parser.GhostJsonConstants as C
import com.ghost.serialization.parser.GhostJsonConstants.BYTE_MASK
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_16
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_24
import com.ghost.serialization.parser.GhostJsonConstants.SHIFT_8
import com.ghost.serialization.parser.GhostJsonConstants.UNICODE_HEX_LENGTH
import com.ghost.serialization.parser.GhostJsonConstants.SINGLE_CHAR_SIZE

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
 * @property rawStrings Array of field names as original strings.
 */
class JsonReaderOptions(
    @PublishedApi internal val rawBytes: Array<ByteArray>,
    @PublishedApi internal val shift: Int,
    @PublishedApi internal val multiplier: Int,
    @PublishedApi internal val rawStrings: Array<String>,
    @PublishedApi internal val enableStringDispatch: Boolean = false
) {
    @PublishedApi
    internal val dispatch = IntArray(DISPATCH_TABLE_SIZE) { -1 }

    @PublishedApi
    internal var stringDispatch = if (enableStringDispatch) {
        IntArray(DISPATCH_TABLE_SIZE) { -1 }
    } else {
        EMPTY_DISPATCH_TABLE
    }
        get() {
            val table = field
            if (table === EMPTY_DISPATCH_TABLE) {
                val newTable = IntArray(DISPATCH_TABLE_SIZE) { -1 }
                buildStringDispatchTable(newTable)
                field = newTable
                return newTable
            }
            return table
        }

    @PublishedApi
    internal val hasCollisions: Boolean

    init {
        var detectedCollision = false
        val seen = HashSet<Long>()
        val rawBytesSize = rawBytes.size
        var rawBytesIdx = 0
        while (rawBytesIdx < rawBytesSize) {
            val bytes = rawBytes[rawBytesIdx]
            rawBytesIdx++
            if (bytes.isNotEmpty()) {
                var key = 0L
                if (bytes.size >= SINGLE_CHAR_SIZE) {
                    key = key or (bytes[0].toLong() and C.LONG_BYTE_MASK)
                }
                if (bytes.size >= C.UNICODE_ESCAPE_PREFIX_SIZE) {
                    key = key or ((bytes[1].toLong() and C.LONG_BYTE_MASK) shl SHIFT_8)
                }
                if (bytes.size >= C.UNICODE_ESCAPE_PREFIX_SIZE + 1) {
                    key = key or ((bytes[2].toLong() and C.LONG_BYTE_MASK) shl SHIFT_16)
                }
                if (bytes.size >= UNICODE_HEX_LENGTH) {
                    key = key or ((bytes[3].toLong() and C.LONG_BYTE_MASK) shl SHIFT_24)
                }
                val packed = key or (bytes.size.toLong() shl (SHIFT_24 + SHIFT_8))
                if (!seen.add(packed)) {
                    detectedCollision = true
                    break
                }
            }
        }
        hasCollisions = detectedCollision

        val tableMask = DISPATCH_TABLE_SIZE - 1
        var index = 0
        while (index < rawBytes.size) {
            val bytes = rawBytes[index]
            if (bytes.isNotEmpty()) {
                var key = 0
                if (bytes.size >= SINGLE_CHAR_SIZE) {
                    key = key or (bytes[0].toInt() and BYTE_MASK)
                }
                if (bytes.size >= C.UNICODE_ESCAPE_PREFIX_SIZE) {
                    key = key or ((bytes[1].toInt() and BYTE_MASK) shl SHIFT_8)
                }
                if (bytes.size >= C.UNICODE_ESCAPE_PREFIX_SIZE + 1) {
                    key = key or ((bytes[2].toInt() and BYTE_MASK) shl SHIFT_16)
                }
                if (bytes.size >= UNICODE_HEX_LENGTH) {
                    key = key or ((bytes[3].toInt() and BYTE_MASK) shl SHIFT_24)
                }
                if (hasCollisions && bytes.size >= UNICODE_HEX_LENGTH) {
                    key = key xor (bytes[bytes.size - SINGLE_CHAR_SIZE].toInt() and BYTE_MASK)
                    key = key xor (bytes[bytes.size shr SINGLE_CHAR_SIZE].toInt() and BYTE_MASK)
                }

                val perfectHashKey = ((key * multiplier + bytes.size) shr shift) and tableMask
                if (dispatch[perfectHashKey] == -1) {
                    dispatch[perfectHashKey] = index
                }
            }
            index++
        }

        if (enableStringDispatch) {
            buildStringDispatchTable(stringDispatch)
        }
    }



    private fun buildStringDispatchTable(table: IntArray) {
        val tableMask = DISPATCH_TABLE_SIZE - 1
        var index = 0
        while (index < rawStrings.size) {
            val keyString = rawStrings[index]
            if (keyString.isNotEmpty()) {
                var key = 0
                if (keyString.length >= SINGLE_CHAR_SIZE) {
                    key = key or (keyString[0].code and BYTE_MASK)
                }
                if (keyString.length >= C.UNICODE_ESCAPE_PREFIX_SIZE) {
                    key = key or ((keyString[1].code and BYTE_MASK) shl SHIFT_8)
                }
                if (keyString.length >= C.UNICODE_ESCAPE_PREFIX_SIZE + 1) {
                    key = key or ((keyString[2].code and BYTE_MASK) shl SHIFT_16)
                }
                if (keyString.length >= UNICODE_HEX_LENGTH) {
                    key = key or ((keyString[3].code and BYTE_MASK) shl SHIFT_24)
                }
                if (hasCollisions && keyString.length >= UNICODE_HEX_LENGTH) {
                    key = key xor (keyString[keyString.length - SINGLE_CHAR_SIZE].code and BYTE_MASK)
                    key = key xor (keyString[keyString.length shr SINGLE_CHAR_SIZE].code and BYTE_MASK)
                }

                val perfectHashKey = ((key * multiplier + keyString.length) shr shift) and tableMask
                if (table[perfectHashKey] == -1) {
                    table[perfectHashKey] = index
                }
            }
            index++
        }
    }

    fun findOptionIndex(name: String): Int {
        val table = stringDispatch
        val tableSize = table.size
        if (tableSize == 0 || name.isEmpty()) return -1
        
        val len = name.length
        var key = 0
        if (len >= SINGLE_CHAR_SIZE) {
            key = key or (name[0].code and BYTE_MASK)
        }
        if (len >= C.UNICODE_ESCAPE_PREFIX_SIZE) {
            key = key or ((name[1].code and BYTE_MASK) shl SHIFT_8)
        }
        if (len >= C.UNICODE_ESCAPE_PREFIX_SIZE + 1) {
            key = key or ((name[2].code and BYTE_MASK) shl SHIFT_16)
        }
        if (len >= UNICODE_HEX_LENGTH) {
            key = key or ((name[3].code and BYTE_MASK) shl SHIFT_24)
        }
        if (hasCollisions && len >= UNICODE_HEX_LENGTH) {
            key = key xor (name[len - SINGLE_CHAR_SIZE].code and BYTE_MASK)
            key = key xor (name[len shr SINGLE_CHAR_SIZE].code and BYTE_MASK)
        }
        
        val tableMask = tableSize - 1
        val perfectHashKey = ((key * multiplier + len) shr shift) and tableMask
        val index = table[perfectHashKey]
        if (index != -1 && rawStrings[index] == name) {
            return index
        }
        return -1
    }

    fun getOptionString(index: Int): String {
        return rawStrings[index]
    }

    companion object {

        /**
         * Creates an optimized options configuration for a predefined set of field names.
         *
         * @param names The list of JSON property names to be matched.
         * @return A [JsonReaderOptions] instance configured with default perfect hashing parameters.
         */
        fun of(vararg names: String): JsonReaderOptions = of(0, C.STR_POOL_HASH_MULTIPLIER, *names)

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
            val rawStrings = Array(names.size) { names[it] }

            return JsonReaderOptions(rawBytes, shift, multiplier, rawStrings, enableStringDispatch = true)
        }

        fun of(shift: Int, multiplier: Int, enableStringDispatch: Boolean, vararg names: String): JsonReaderOptions {
            val rawBytes = Array(names.size) { names[it].encodeToByteArray() }
            val rawStrings = Array(names.size) { names[it] }

            return JsonReaderOptions(rawBytes, shift, multiplier, rawStrings, enableStringDispatch)
        }

        private const val DISPATCH_TABLE_SIZE = 1024
        private val EMPTY_DISPATCH_TABLE = IntArray(0)
    }
}
