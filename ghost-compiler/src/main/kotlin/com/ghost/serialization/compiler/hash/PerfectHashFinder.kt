@file:Suppress("ReplaceSizeCheckWithIsNotEmpty")

package com.ghost.serialization.compiler.hash

import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C

internal object PerfectHashFinder {

    /**
     * Finds a collision-free hash multiplier/shift pair mapping [names] to unique index slots
     * in a dispatch table, used by the generated reader's `selectNameAndConsume`. The first four
     * bytes of each name are packed into a 32-bit key; names longer than that fall back to an
     * extended (polynomial) hash if the prefix alone collides.
     *
     * @return Optimal hash parameters and whether extended key hashing is required at runtime.
     */
    fun findPerfectHash(names: List<String>): PerfectHashConfig {
        if (names.isEmpty()) {
            return PerfectHashConfig(
                C.VAL_ZERO,
                C.HASH_MULTIPLIER_START,
                C.PERFECT_HASH_EMPTY_TABLE_SIZE,
                extendedKeyHash = false,
            )
        }
        findPerfectHashInternal(
            names,
            useExtendedKeyHash = false
        )?.let { (shift, multiplier, tableSize) ->
            return PerfectHashConfig(shift, multiplier, tableSize, extendedKeyHash = false)
        }
        findPerfectHashInternal(
            names,
            useExtendedKeyHash = true
        )?.let { (shift, multiplier, tableSize) ->
            return PerfectHashConfig(shift, multiplier, tableSize, extendedKeyHash = true)
        }
        throw IllegalStateException(
            C.STR_ERR_PERFECT_HASH_COLLISION_1 + names.joinToString() + C.STR_ERR_PERFECT_HASH_COLLISION_2
        )
    }

    private fun findPerfectHashInternal(
        names: List<String>,
        useExtendedKeyHash: Boolean
    ): Triple<Int, Int, Int>? {
        val rawBytes = names.map { it.encodeToByteArray() }
        val hasCollisions = if (useExtendedKeyHash) {
            true
        } else {
            detectPrefixLengthCollisions(rawBytes)
        }

        val tableSizes = C.PERFECT_HASH_TABLE_SIZES
        for (tableSize in tableSizes) {
            val tableMask = tableSize - C.VAL_ONE
            for (multiplier in C.HASH_MULTIPLIER_START..C.HASH_MULTIPLIER_LIMIT step C.HASH_MULTIPLIER_STEP) {
                for (shift in C.VAL_ZERO..C.HASH_SHIFT_LIMIT) {
                    val dispatch = IntArray(tableSize) { -1 }
                    var collision = false
                    for (index in rawBytes.indices) {
                        val bytes = rawBytes[index]
                        if (bytes.isNotEmpty()) {
                            val key = computeDispatchKey(bytes, hasCollisions)
                            val hash = ((key * multiplier + bytes.size) shr shift) and tableMask
                            if (dispatch[hash] == -1) {
                                dispatch[hash] = index
                            } else {
                                collision = true
                                break
                            }
                        }
                    }
                    if (!collision) {
                        return Triple(shift, multiplier, tableSize)
                    }
                }
            }
        }
        return null
    }

    private fun detectPrefixLengthCollisions(rawBytes: List<ByteArray>): Boolean {
        val seen = HashSet<Long>()
        for (bytes in rawBytes) {
            if (bytes.isNotEmpty()) {
                var mask = 0L
                if (bytes.size >= C.VAL_ONE) mask =
                    mask or (bytes[C.VAL_ZERO].toLong() and C.LONG_BYTE_MASK)
                if (bytes.size >= C.VAL_TWO) mask =
                    mask or ((bytes[C.VAL_ONE].toLong() and C.LONG_BYTE_MASK) shl C.BIT_SHIFT_8)
                if (bytes.size >= C.VAL_THREE) mask =
                    mask or ((bytes[C.VAL_TWO].toLong() and C.LONG_BYTE_MASK) shl C.BIT_SHIFT_16)
                if (bytes.size >= C.VAL_FOUR) mask =
                    mask or ((bytes[C.VAL_THREE].toLong() and C.LONG_BYTE_MASK) shl C.BIT_SHIFT_24)
                val packed = mask or (bytes.size.toLong() shl C.BIT_SHIFT_32)
                if (!seen.add(packed)) {
                    return true
                }
            }
        }
        return false
    }

    private fun computeDispatchKey(bytes: ByteArray, hasCollisions: Boolean): Int {
        var key = 0
        if (bytes.size >= C.VAL_ONE) {
            key = key or (bytes[C.VAL_ZERO].toInt() and C.BYTE_MASK)
        }
        if (bytes.size >= C.VAL_TWO) {
            key = key or ((bytes[C.VAL_ONE].toInt() and C.BYTE_MASK) shl C.BIT_SHIFT_8)
        }
        if (bytes.size >= C.VAL_THREE) {
            key = key or ((bytes[C.VAL_TWO].toInt() and C.BYTE_MASK) shl C.BIT_SHIFT_16)
        }
        if (bytes.size >= C.VAL_FOUR) {
            key = key or ((bytes[C.VAL_THREE].toInt() and C.BYTE_MASK) shl C.BIT_SHIFT_24)
        }
        if (hasCollisions) {
            var ci = C.VAL_FOUR
            while (ci < bytes.size) {
                key = key * C.COLLISION_HASH_MULTIPLIER + (bytes[ci].toInt() and C.BYTE_MASK)
                ci++
            }
        }
        return key
    }
}
