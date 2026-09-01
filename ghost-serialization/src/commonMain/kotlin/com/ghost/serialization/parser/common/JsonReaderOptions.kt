@file:Suppress("ReplaceSizeCheckWithIsNotEmpty")

package com.ghost.serialization.parser.common

import com.ghost.serialization.parser.strings.packChars4
import com.ghost.serialization.parser.common.GhostJsonConstants as C


/**
 * Dispatch options for optimized JSON field identification; uses a 4-byte hashing engine
 * to minimize collisions during field lookup.
 *
 * [rawBytes] stores field names as raw [ByteArray] (not Okio ByteString) so [verifyKeyMatch]
 * compares bytes directly, no virtual dispatch or Okio `rangeEquals` bounds checks.
 * [rawChars] mirrors [rawStrings] as [CharArray] for the same reason on the String channel.
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
    /** Field names as [CharArray], built once from [rawStrings]; used by the String-channel key matcher. */
    @PublishedApi
    internal val rawChars: Array<CharArray> = Array(rawStrings.size) { i ->
        rawStrings[i].toCharArray()
    }

    /**
     * [rawBytes] entries of at most [C.LONG_BYTES] bytes, zero-padded up to exactly
     * [C.LONG_BYTES] (`ByteArray.copyOf` zero-fills on growth) — lets the predicted-field fast
     * path compare a short key with a single masked [com.ghost.serialization.parser.bytes.ghostReadLong8]
     * read instead of a byte-by-byte loop. Entries longer than [C.LONG_BYTES] map to
     * [EMPTY_PADDED_KEY] and stay on the existing loop+tail path.
     */
    @PublishedApi
    internal val predictedKeyPadded: Array<ByteArray> = Array(rawBytes.size) { i ->
        val bytes = rawBytes[i]
        if (bytes.size in 1..C.LONG_BYTES) bytes.copyOf(C.LONG_BYTES) else EMPTY_PADDED_KEY
    }

    /**
     * Two packed-Long words per candidate (see [com.ghost.serialization.parser.strings.packChars4]),
     * covering predicted-key names up to [C.MAX_CHAR_FASTPATH_LEN] chars for the String-channel
     * fast path in [com.ghost.serialization.parser.strings.internalSelect]. Both entries stay `0L`
     * for candidates outside that range — the `candidateLength` guard at the call site keeps those
     * on the existing loop+tail path, so the zero value is never read.
     */
    @PublishedApi
    internal val predictedCharWord0: LongArray = LongArray(rawChars.size)

    @PublishedApi
    internal val predictedCharWord1: LongArray = LongArray(rawChars.size)

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
                if (bytes.size >= C.SINGLE_CHAR_SIZE) {
                    key = key or (bytes[0].toLong() and C.LONG_BYTE_MASK)
                }
                if (bytes.size >= C.UNICODE_ESCAPE_PREFIX_SIZE) {
                    key = key or ((bytes[1].toLong() and C.LONG_BYTE_MASK) shl C.SHIFT_8)
                }
                if (bytes.size >= C.UNICODE_ESCAPE_PREFIX_SIZE + 1) {
                    key = key or ((bytes[2].toLong() and C.LONG_BYTE_MASK) shl C.SHIFT_16)
                }
                if (bytes.size >= C.UNICODE_HEX_LENGTH) {
                    key = key or ((bytes[3].toLong() and C.LONG_BYTE_MASK) shl C.SHIFT_24)
                }
                val packed = key or (bytes.size.toLong() shl (C.SHIFT_24 + C.SHIFT_8))
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
                if (bytes.size >= C.SINGLE_CHAR_SIZE) {
                    key = key or (bytes[0].toInt() and C.BYTE_MASK)
                }
                if (bytes.size >= C.UNICODE_ESCAPE_PREFIX_SIZE) {
                    key = key or ((bytes[1].toInt() and C.BYTE_MASK) shl C.SHIFT_8)
                }
                if (bytes.size >= C.UNICODE_ESCAPE_PREFIX_SIZE + 1) {
                    key = key or ((bytes[2].toInt() and C.BYTE_MASK) shl C.SHIFT_16)
                }
                if (bytes.size >= C.UNICODE_HEX_LENGTH) {
                    key = key or ((bytes[3].toInt() and C.BYTE_MASK) shl C.SHIFT_24)
                }
                if (hasCollisions) {
                    var ci = C.UNICODE_HEX_LENGTH
                    while (ci < bytes.size) {
                        key =
                            key * C.COLLISION_HASH_MULTIPLIER + (bytes[ci].toInt() and C.BYTE_MASK); ci++
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

        for (i in rawChars.indices) {
            val candidate = rawChars[i]
            if (candidate.size in 1..C.MAX_CHAR_FASTPATH_LEN) {
                val padded = candidate.copyOf(C.MAX_CHAR_FASTPATH_LEN)
                predictedCharWord0[i] = packChars4(padded, 0)
                if (candidate.size > C.LONG_CHARS) {
                    predictedCharWord1[i] = packChars4(padded, C.LONG_CHARS)
                }
            }
        }
    }

    private fun buildStringDispatchTable(table: IntArray) {
        val tableMask = tableSize - 1
        for (index in rawStrings.indices) {
            val keyString = rawStrings[index]
            if (keyString.isNotEmpty()) {
                var key = 0
                if (keyString.length >= C.SINGLE_CHAR_SIZE) {
                    key = key or (keyString[0].code and C.BYTE_MASK)
                }
                if (keyString.length >= C.UNICODE_ESCAPE_PREFIX_SIZE) {
                    key = key or ((keyString[1].code and C.BYTE_MASK) shl C.SHIFT_8)
                }
                if (keyString.length >= C.UNICODE_ESCAPE_PREFIX_SIZE + 1) {
                    key = key or ((keyString[2].code and C.BYTE_MASK) shl C.SHIFT_16)
                }
                if (keyString.length >= C.UNICODE_HEX_LENGTH) {
                    key = key or ((keyString[3].code and C.BYTE_MASK) shl C.SHIFT_24)
                }
                if (hasCollisions) {
                    var ci = C.UNICODE_HEX_LENGTH
                    while (ci < keyString.length) {
                        key =
                            key * C.COLLISION_HASH_MULTIPLIER + (keyString[ci].code and C.BYTE_MASK); ci++
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
        if (len >= C.SINGLE_CHAR_SIZE) {
            key = key or (name[0].code and C.BYTE_MASK)
        }
        if (len >= C.UNICODE_ESCAPE_PREFIX_SIZE) {
            key = key or ((name[1].code and C.BYTE_MASK) shl C.SHIFT_8)
        }
        if (len >= C.UNICODE_ESCAPE_PREFIX_SIZE + 1) {
            key = key or ((name[2].code and C.BYTE_MASK) shl C.SHIFT_16)
        }
        if (len >= C.UNICODE_HEX_LENGTH) {
            key = key or ((name[3].code and C.BYTE_MASK) shl C.SHIFT_24)
        }
        if (hasCollisions && len >= C.UNICODE_HEX_LENGTH) {
            key = key xor (name[len - C.SINGLE_CHAR_SIZE].code and C.BYTE_MASK)
            key = key xor (name[len shr C.SINGLE_CHAR_SIZE].code and C.BYTE_MASK)
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
        // buildStringDispatchTable, and each computeKeyHash. See C.COLLISION_HASH_MULTIPLIER.
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
        private val EMPTY_PADDED_KEY = ByteArray(0)
    }
}
