@file:Suppress("ReplaceSizeCheckWithIsNotEmpty")

package com.ghost.serialization.parser.common

import com.ghost.serialization.parser.common.GhostJsonConstants.BYTE_MASK
import com.ghost.serialization.parser.common.GhostJsonConstants.COLLISION_HASH_MULTIPLIER
import com.ghost.serialization.parser.common.GhostJsonConstants.SHIFT_16
import com.ghost.serialization.parser.common.GhostJsonConstants.SHIFT_24
import com.ghost.serialization.parser.common.GhostJsonConstants.SHIFT_8
import com.ghost.serialization.parser.common.GhostJsonConstants.SINGLE_CHAR_SIZE
import com.ghost.serialization.parser.common.GhostJsonConstants.UNICODE_HEX_LENGTH
import com.ghost.serialization.parser.common.GhostJsonConstants as C


/**
 * Dispatch options for optimized JSON field identification.
 * Uses a 4-byte hashing engine to minimize collisions during field lookup.
 *
 * [rawBytes] stores field names as raw [ByteArray] instead of Okio ByteString
 * so that [verifyKeyMatch] can compare bytes directly without virtual dispatch
 * or redundant bounds checks inside Okio's `rangeEquals`.
 *
 * [rawChars] stores the same names as [CharArray] for the String-channel
 * [verifyKeyMatch], avoiding per-character [String.get] / coder checks on the
 * hot key-match path.
 *
 * @property rawBytes Array of field names represented as raw byte arrays (UTF-8).
 * @property shift The bit-shift amount used to normalize key distributions.
 * @property multiplier The prime multiplier used to spread key entropy across the address space.
 * @property rawStrings Array of field names as original strings.
 */
class JsonReaderOptions(
    val rawBytes: Array<ByteArray>,
    @PublishedApi internal val shift: Int,
    @PublishedApi internal val multiplier: Int,
    @PublishedApi internal val tableSize: Int,
    val rawStrings: Array<String>,
    @PublishedApi internal val enableStringDispatch: Boolean = false,
    @PublishedApi internal val extendedKeyHash: Boolean? = null
) {
    /**
     * Field names as [CharArray], built once from [rawStrings].
     * Used exclusively by the String-channel key matcher.
     */
    @PublishedApi
    internal val rawChars: Array<CharArray> = Array(rawStrings.size) { i ->
        rawStrings[i].toCharArray()
    }

    @PublishedApi
    internal val dispatch = IntArray(tableSize) { -1 }

    @PublishedApi
    internal var stringDispatch = if (enableStringDispatch) {
        IntArray(tableSize) { -1 }
    } else {
        EMPTY_DISPATCH_TABLE
    }
        get() {
            val table = field
            if (table === EMPTY_DISPATCH_TABLE) {
                val newTable = IntArray(tableSize) { -1 }
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
        for (bytes in rawBytes) {
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
        hasCollisions = if (extendedKeyHash == true) {
            true
        } else {
            detectedCollision
        }

        val tableMask = tableSize - 1
        for (index in rawBytes.indices) {
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
                if (hasCollisions) {
                    var ci = UNICODE_HEX_LENGTH
                    while (ci < bytes.size) {
                        key =
                            key * COLLISION_HASH_MULTIPLIER + (bytes[ci].toInt() and BYTE_MASK); ci++
                    }
                }

                val perfectHashKey = ((key * multiplier + bytes.size) shr shift) and tableMask
                if (dispatch[perfectHashKey] == -1) {
                    dispatch[perfectHashKey] = index
                }
            }
        }

        if (enableStringDispatch) {
            buildStringDispatchTable(stringDispatch)
        }
    }

    private fun buildStringDispatchTable(table: IntArray) {
        val tableMask = tableSize - 1
        for (index in rawStrings.indices) {
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
                if (hasCollisions) {
                    var ci = UNICODE_HEX_LENGTH
                    while (ci < keyString.length) {
                        key =
                            key * COLLISION_HASH_MULTIPLIER + (keyString[ci].code and BYTE_MASK); ci++
                    }
                }

                val perfectHashKey = ((key * multiplier + keyString.length) shr shift) and tableMask
                if (table[perfectHashKey] == -1) {
                    table[perfectHashKey] = index
                }
            }
        }
    }

    /** Linear lookup helper for YAML enum-style matching on plain string keys. */
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

    fun getOptionString(index: Int): String = rawStrings[index]

    companion object {
        // Collision disambiguation uses polynomial accumulation inlined directly in init,
        // buildStringDispatchTable, and each computeKeyHash. See COLLISION_HASH_MULTIPLIER.
        // All five sites must stay identical; PerfectHashFinder (compiler-side) is the sixth.

        fun of(vararg names: String): JsonReaderOptions = of(
            C.DEFAULT_DISPATCH_SHIFT,
            C.DEFAULT_DISPATCH_MULTIPLIER,
            C.DEFAULT_DISPATCH_TABLE_SIZE,
            *names
        )

        fun of(shift: Int, multiplier: Int, vararg names: String): JsonReaderOptions {
            return of(shift, multiplier, C.DEFAULT_DISPATCH_TABLE_SIZE, *names)
        }

        fun of(
            shift: Int,
            multiplier: Int,
            tableSize: Int,
            vararg names: String
        ): JsonReaderOptions {
            val rawBytes = Array(names.size) { names[it].encodeToByteArray() }
            val rawStrings = Array(names.size) { names[it] }
            return JsonReaderOptions(
                rawBytes,
                shift,
                multiplier,
                tableSize,
                rawStrings,
                enableStringDispatch = true
            )
        }

        fun of(
            shift: Int,
            multiplier: Int,
            enableStringDispatch: Boolean,
            vararg names: String
        ): JsonReaderOptions {
            return of(
                shift,
                multiplier,
                C.DEFAULT_DISPATCH_TABLE_SIZE,
                enableStringDispatch,
                *names
            )
        }

        fun of(
            shift: Int,
            multiplier: Int,
            tableSize: Int,
            enableStringDispatch: Boolean,
            vararg names: String
        ): JsonReaderOptions {
            val rawBytes = Array(names.size) { names[it].encodeToByteArray() }
            val rawStrings = Array(names.size) { names[it] }
            return JsonReaderOptions(
                rawBytes,
                shift,
                multiplier,
                tableSize,
                rawStrings,
                enableStringDispatch = enableStringDispatch
            )
        }

        fun of(
            shift: Int,
            multiplier: Int,
            tableSize: Int,
            enableStringDispatch: Boolean,
            extendedKeyHash: Boolean,
            vararg names: String
        ): JsonReaderOptions {
            val rawBytes = Array(names.size) { names[it].encodeToByteArray() }
            val rawStrings = Array(names.size) { names[it] }
            return JsonReaderOptions(
                rawBytes,
                shift,
                multiplier,
                tableSize,
                rawStrings,
                enableStringDispatch = enableStringDispatch,
                extendedKeyHash = extendedKeyHash
            )
        }

        private val EMPTY_DISPATCH_TABLE = IntArray(0)
    }
}
