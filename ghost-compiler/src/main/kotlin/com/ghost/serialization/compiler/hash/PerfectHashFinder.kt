@file:Suppress("ReplaceSizeCheckWithIsNotEmpty")

package com.ghost.serialization.compiler.hash

import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C
import com.ghost.serialization.compiler.internal.GhostPerfectHashConstants as PH

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
                shift = C.VAL_ZERO,
                multiplier = PH.HASH_MULTIPLIER_START,
                tableSize = PH.PERFECT_HASH_EMPTY_TABLE_SIZE,
                extendedKeyHash = false,
            )
        }
        findPerfectHashInternal(
            names = names,
            useExtendedKeyHash = false
        )?.let { (shift, multiplier, tableSize) ->
            return PerfectHashConfig(
                shift = shift,
                multiplier = multiplier,
                tableSize = tableSize,
                extendedKeyHash = false
            )
        }
        findPerfectHashInternal(
            names = names,
            useExtendedKeyHash = true
        )?.let { (shift, multiplier, tableSize) ->
            return PerfectHashConfig(
                shift = shift,
                multiplier = multiplier,
                tableSize = tableSize,
                extendedKeyHash = true
            )
        }
        throw IllegalStateException(
            PH.STR_ERR_PERFECT_HASH_COLLISION_1 + names.joinToString() + PH.STR_ERR_PERFECT_HASH_COLLISION_2
        )
    }

    private fun computeDispatchKey(bytes: ByteArray, hasCollisions: Boolean): Int {
        var key = 0
        if (bytes.size >= CC.VAL_ONE) {
            key = key or (bytes[C.VAL_ZERO].toInt() and PH.BYTE_MASK)
        }
        if (bytes.size >= PH.VAL_TWO) {
            key = key or ((bytes[CC.VAL_ONE].toInt() and PH.BYTE_MASK) shl PH.BIT_SHIFT_8)
        }
        if (bytes.size >= PH.VAL_THREE) {
            key = key or ((bytes[PH.VAL_TWO].toInt() and PH.BYTE_MASK) shl PH.BIT_SHIFT_16)
        }
        if (bytes.size >= PH.VAL_FOUR) {
            key = key or ((bytes[PH.VAL_THREE].toInt() and PH.BYTE_MASK) shl PH.BIT_SHIFT_24)
        }
        if (hasCollisions) {
            var ci = PH.VAL_FOUR
            while (ci < bytes.size) {
                key = key * PH.COLLISION_HASH_MULTIPLIER + (bytes[ci].toInt() and PH.BYTE_MASK)
                ci++
            }
        }
        return key
    }

    private fun detectPrefixLengthCollisions(rawBytes: List<ByteArray>): Boolean {
        val seen = HashSet<Long>()
        for (bytes in rawBytes) {
            if (bytes.isNotEmpty()) {
                var mask = 0L
                if (bytes.size >= CC.VAL_ONE) mask =
                    mask or (bytes[C.VAL_ZERO].toLong() and PH.LONG_BYTE_MASK)
                if (bytes.size >= PH.VAL_TWO) mask =
                    mask or ((bytes[CC.VAL_ONE].toLong() and PH.LONG_BYTE_MASK) shl PH.BIT_SHIFT_8)
                if (bytes.size >= PH.VAL_THREE) mask =
                    mask or ((bytes[PH.VAL_TWO].toLong() and PH.LONG_BYTE_MASK) shl PH.BIT_SHIFT_16)
                if (bytes.size >= PH.VAL_FOUR) mask =
                    mask or ((bytes[PH.VAL_THREE].toLong() and PH.LONG_BYTE_MASK) shl PH.BIT_SHIFT_24)
                val packed = mask or (bytes.size.toLong() shl PH.BIT_SHIFT_32)
                if (!seen.add(packed)) {
                    return true
                }
            }
        }
        return false
    }

    private fun findPerfectHashInternal(
        names: List<String>,
        useExtendedKeyHash: Boolean
    ): Triple<Int, Int, Int>? {
        val rawBytes = names.map { it.encodeToByteArray() }
        val hasCollisions = if (useExtendedKeyHash) {
            true
        } else {
            detectPrefixLengthCollisions(rawBytes = rawBytes)
        }

        val tableSizes = PH.PERFECT_HASH_TABLE_SIZES
        for (tableSize in tableSizes) {
            val tableMask = tableSize - CC.VAL_ONE
            for (multiplier in PH.HASH_MULTIPLIER_START..PH.HASH_MULTIPLIER_LIMIT step PH.HASH_MULTIPLIER_STEP) {
                for (shift in C.VAL_ZERO..PH.HASH_SHIFT_LIMIT) {
                    val dispatch = IntArray(tableSize) { -1 }
                    var collision = false
                    for (index in rawBytes.indices) {
                        val bytes = rawBytes[index]
                        if (bytes.isNotEmpty()) {
                            val key = computeDispatchKey(bytes = bytes, hasCollisions = hasCollisions)
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
}
