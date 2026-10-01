@file:Suppress("ReplaceSizeCheckWithIsNotEmpty")

package com.ghost.serialization.parser.common.json

import com.ghost.serialization.parser.strings.packChars4
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

/**
 * Dispatch options for optimized JSON field identification; uses a 4-byte hashing engine
 * ([computeKeyHashCore], shared with the compiled reader channels) to minimize collisions
 * during field lookup.
 *
 * [rawBytes] stores field names as raw [ByteArray] (not Okio ByteString) for direct byte
 * comparison, no virtual dispatch or Okio `rangeEquals` bounds checks. [rawChars] mirrors
 * [rawStrings] as [CharArray] for the same reason on the String channel.
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
     * [rawBytes] entries of at most [SCN.LONG_BYTES] bytes, zero-padded up to exactly
     * [SCN.LONG_BYTES] (`ByteArray.copyOf` zero-fills on growth) — lets the predicted-field fast
     * path compare a short key with a single masked [com.ghost.serialization.parser.bytes.ghostReadLong8]
     * read instead of a byte-by-byte loop. Entries longer than [SCN.LONG_BYTES] map to
     * [EMPTY_PADDED_KEY] and stay on the existing loop+tail path.
     */
    @PublishedApi
    internal val predictedKeyPadded: Array<ByteArray> = Array(rawBytes.size) { i ->
        val bytes = rawBytes[i]
        if (bytes.size in 1..SCN.LONG_BYTES) bytes.copyOf(SCN.LONG_BYTES) else EMPTY_PADDED_KEY
    }

    /**
     * Two packed-Long words per candidate (see [packChars4]),
     * covering predicted-key names up to [SCN.MAX_CHAR_FASTPATH_LEN] chars for the String-channel
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
                buildStringDispatchTable(table = newTable)
                field = newTable
                return newTable
            }
            return table
        }

    @PublishedApi
    internal val hasCollisions: Boolean

    init {
        // Detects whether any two candidates share the same 4-byte-prefix key AND length —
        // packs both into one collision-free Long (key unsigned in the low 32 bits, length in
        // the high 32 bits) so a HashSet<Long> can spot a duplicate without a Pair allocation.
        val seen = HashSet<Long>()
        var detectedCollision = false
        for (bytes in rawBytes) {
            if (bytes.isNotEmpty()) {
                val key = computeKeyHashCore(start = 0, length = bytes.size, hasCollisions = false) {
                    bytes[it].toInt() and TOK.BYTE_MASK
                }
                val packed = key.toUInt().toLong() or (bytes.size.toLong() shl SCN.SHIFT_32)
                if (!seen.add(packed)) {
                    detectedCollision = true
                    break
                }
            }
        }
        hasCollisions = extendedKeyHash == true || detectedCollision

        val tableMask = tableSize - 1
        for (index in rawBytes.indices) {
            val bytes = rawBytes[index]
            if (bytes.isNotEmpty()) {
                val key = computeKeyHashCore(start = 0, length = bytes.size, hasCollisions = hasCollisions) {
                    bytes[it].toInt() and TOK.BYTE_MASK
                }
                val perfectHashKey = ((key * multiplier + bytes.size) shr shift) and tableMask
                if (dispatch[perfectHashKey] == -1) {
                    dispatch[perfectHashKey] = index
                }
            }
        }

        if (enableStringDispatch) {
            buildStringDispatchTable(table = stringDispatch)
        }

        for (i in rawChars.indices) {
            val candidate = rawChars[i]
            if (candidate.size in 1..SCN.MAX_CHAR_FASTPATH_LEN) {
                val padded = candidate.copyOf(SCN.MAX_CHAR_FASTPATH_LEN)
                predictedCharWord0[i] = packChars4(chars = padded, index = 0)
                if (candidate.size > SCN.LONG_CHARS) {
                    predictedCharWord1[i] = packChars4(chars = padded, index = SCN.LONG_CHARS)
                }
            }
        }
    }

    /** Linear lookup helper for YAML enum-style matching on plain string keys. */
    fun findOptionIndex(name: String): Int {
        val table = stringDispatch
        val tableSize = table.size
        if (tableSize == 0 || name.isEmpty()) return -1

        val key = computeKeyHashCore(start = 0, length = name.length, hasCollisions = hasCollisions) {
            name[it].code and TOK.BYTE_MASK
        }
        val tableMask = tableSize - 1
        val perfectHashKey = ((key * multiplier + name.length) shr shift) and tableMask
        val index = table[perfectHashKey]
        if (index != -1 && rawStrings[index] == name) {
            return index
        }
        return -1
    }

    private fun buildStringDispatchTable(table: IntArray) {
        val tableMask = tableSize - 1
        for (index in rawStrings.indices) {
            val keyString = rawStrings[index]
            if (keyString.isNotEmpty()) {
                val key = computeKeyHashCore(start = 0, length = keyString.length, hasCollisions = hasCollisions) {
                    keyString[it].code and TOK.BYTE_MASK
                }
                val perfectHashKey = ((key * multiplier + keyString.length) shr shift) and tableMask
                if (table[perfectHashKey] == -1) {
                    table[perfectHashKey] = index
                }
            }
        }
    }

    companion object {
        // Collision disambiguation is centralized in computeKeyHashCore (GhostJsonKeySelectHelpers.kt)
        // — used here, by the compiled reader channels (bytes/streaming/string), and mirrored by
        // PerfectHashFinder (compiler-side). Calling the same function removes the old requirement
        // to hand-keep several inlined copies byte-for-byte identical.

        private val EMPTY_DISPATCH_TABLE = IntArray(0)
        private val EMPTY_PADDED_KEY = ByteArray(0)

        /**
         * Convenience factories over [JsonReaderOptions]'s constructor; each fixes one more
         * trailing default before the `names` vararg, matching the distinct positional call
         * shapes used by generated code (codegen always passes `shift, multiplier, tableSize,
         * enableStringDispatch[, extendedKeyHash]` positionally) and by hand-written call sites.
         * Only the fullest overload builds the instance; the rest just fill in a default and
         * delegate.
         */
        fun of(vararg names: String): JsonReaderOptions = of(
            SCN.DEFAULT_DISPATCH_SHIFT,
            SCN.DEFAULT_DISPATCH_MULTIPLIER,
            SCN.DEFAULT_DISPATCH_TABLE_SIZE,
            *names
        )

        fun of(shift: Int, multiplier: Int, vararg names: String): JsonReaderOptions = of(
            shift,
            multiplier,
            SCN.DEFAULT_DISPATCH_TABLE_SIZE,
            *names
        )

        fun of(shift: Int, multiplier: Int, tableSize: Int, vararg names: String): JsonReaderOptions = of(
            shift,
            multiplier,
            tableSize,
            enableStringDispatch = true,
            *names
        )

        fun of(
            shift: Int,
            multiplier: Int,
            enableStringDispatch: Boolean,
            vararg names: String
        ): JsonReaderOptions = of(
            shift,
            multiplier,
            SCN.DEFAULT_DISPATCH_TABLE_SIZE,
            enableStringDispatch,
            *names
        )

        fun of(
            shift: Int,
            multiplier: Int,
            tableSize: Int,
            enableStringDispatch: Boolean,
            vararg names: String
        ): JsonReaderOptions = buildOptions(
            shift = shift,
            multiplier = multiplier,
            tableSize = tableSize,
            enableStringDispatch = enableStringDispatch,
            extendedKeyHash = null,
            names = names
        )

        fun of(
            shift: Int,
            multiplier: Int,
            tableSize: Int,
            enableStringDispatch: Boolean,
            extendedKeyHash: Boolean,
            vararg names: String
        ): JsonReaderOptions = buildOptions(
            shift = shift,
            multiplier = multiplier,
            tableSize = tableSize,
            enableStringDispatch = enableStringDispatch,
            extendedKeyHash = extendedKeyHash,
            names = names
        )

        private fun buildOptions(
            shift: Int,
            multiplier: Int,
            tableSize: Int,
            enableStringDispatch: Boolean,
            extendedKeyHash: Boolean?,
            names: Array<out String>
        ): JsonReaderOptions {
            val rawBytes = Array(names.size) { names[it].encodeToByteArray() }
            val rawStrings = Array(names.size) { names[it] }
            return JsonReaderOptions(
                rawBytes = rawBytes,
                shift = shift,
                multiplier = multiplier,
                tableSize = tableSize,
                rawStrings = rawStrings,
                enableStringDispatch = enableStringDispatch,
                extendedKeyHash = extendedKeyHash
            )
        }
    }
}
