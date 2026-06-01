@file:Suppress("ReplaceSizeCheckWithIsNotEmpty")

package com.ghost.serialization.compiler

import com.ghost.serialization.compiler.GhostEmitterConstants as C

internal object PerfectHashFinder {

    /**
     * Utility to find a collision-free hash multiplier and shift configuration
     * mapping a set of field names to unique index slots inside a 1024-entry table.
     *
     * ### How the Reader uses these parameters (Runtime Walkthrough):
     *
     * Suppose we have a field named **"age"** (3 bytes: 'a'=97, 'g'=103, 'e'=101)
     * and the optimizer chose `multiplier = 31` and `shift = 5`.
     *
     * | Step | Action | Description | Resulting Value |
     * | :--- | :--- | :--- | :--- |
     * | **1. Bytes** | Capture | Read the first 4 bytes of "age" | `[97, 103, 101]` |
     * | **2. Packing** | Create `key` | Place bytes into 4 slots (32-bit container) | `0x00656761` (hex) |
     * | **3. Hash** | Apply formula | Calculate: `((key * mult + size) >> shift) & 1023` | `((6645601 * 31 + 3) >> 5) & 1023` |
     * | **4. Lookup** | Index | Access the dispatch table | `dispatch(hash)` -> Returns index |
     *
     * #### Deep dive into "Packing" (Step 2):
     * Imagine a 32-bit Integer as 4 empty boxes. We place each byte into a box,
     * shifting them to the left so they don't overlap:
     *
     * ```text
     * Slot 4 (24-31) | Slot 3 (16-23) | Slot 2 (8-15) | Slot 1 (0-7)
     * --------------------------------------------------------------
     * Empty      |     'e' (101)  |   'g' (103)   |  'a' (97)
     * ```
     * *Each shift (<< 8, << 16, etc.) just pushes the byte into its assigned slot.*
     *
     * @param names The list of unique field names to index.
     * @return A [Pair] containing the optimal shift and multiplier.
     */
    fun findPerfectHash(names: List<String>): Pair<Int, Int> {
        if (names.isEmpty()) {
            return 0 to C.HASH_MULTIPLIER_START
        }
        val rawBytes = names.map {
            it.encodeToByteArray()
        }

        // Mirror the runtime JsonReaderOptions collision detection logic
        var hasCollisions = false
        val seen = HashSet<Long>()
        for (bytes in rawBytes) {
            if (bytes.isNotEmpty()) {
                var k = 0L
                if (bytes.size >= C.VAL_ONE) k = k or (bytes[C.VAL_ZERO].toLong() and C.LONG_BYTE_MASK)
                if (bytes.size >= C.VAL_TWO) k = k or ((bytes[C.VAL_ONE].toLong() and C.LONG_BYTE_MASK) shl C.BIT_SHIFT_8)
                if (bytes.size >= C.VAL_THREE) k = k or ((bytes[C.VAL_TWO].toLong() and C.LONG_BYTE_MASK) shl C.BIT_SHIFT_16)
                if (bytes.size >= C.VAL_FOUR) k = k or ((bytes[C.VAL_THREE].toLong() and C.LONG_BYTE_MASK) shl C.BIT_SHIFT_24)
                val packed = k or (bytes.size.toLong() shl C.BIT_SHIFT_32)
                if (!seen.add(packed)) {
                    hasCollisions = true
                    break
                }
            }
        }

        // Brute force search for a collision-free multiplier and shift for 4-byte hashing
        for (multiplier in C.HASH_MULTIPLIER_START..C.HASH_MULTIPLIER_LIMIT step C.HASH_MULTIPLIER_STEP) {
            for (shift in 0..C.HASH_SHIFT_LIMIT) {
                val dispatch = IntArray(C.HASH_TABLE_SIZE) { -1 }
                var collision = false
                for (index in rawBytes.indices) {
                    val bytes = rawBytes[index]
                    if (bytes.isNotEmpty()) {
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
                        if (hasCollisions && bytes.size >= C.VAL_FOUR) {
                            key = key xor (bytes[bytes.size - C.VAL_ONE].toInt() and C.BYTE_MASK)
                            key = key xor (bytes[bytes.size shr C.VAL_ONE].toInt() and C.BYTE_MASK)
                        }

                        val hash = ((key * multiplier + bytes.size) shr shift) and C.HASH_MASK
                        if (dispatch[hash] == -1) {
                            dispatch[hash] = index
                        } else {
                            collision = true
                            break
                        }
                    }
                }
                if (!collision) {
                    return shift to multiplier
                }
            }
        }
        return 0 to C.HASH_MULTIPLIER_START // Fallback
    }
}
