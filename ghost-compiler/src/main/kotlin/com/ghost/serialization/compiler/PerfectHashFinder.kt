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

        // Brute force search for a collision-free multiplier and shift for 4-byte hashing
        for (multiplier in C.HASH_MULTIPLIER_START..C.HASH_MULTIPLIER_LIMIT step C.HASH_MULTIPLIER_STEP) {
            for (shift in 0..C.HASH_SHIFT_LIMIT) {
                val dispatch = IntArray(C.HASH_TABLE_SIZE) { -1 }
                var collision = false
                for (index in rawBytes.indices) {
                    val bytes = rawBytes[index]
                    if (bytes.isNotEmpty()) {
                        var key = 0
                        if (bytes.size >= 1) {
                            key = key or (bytes[0].toInt() and C.BYTE_MASK)
                        }
                        if (bytes.size >= 2) {
                            key = key or ((bytes[1].toInt() and C.BYTE_MASK) shl C.BIT_SHIFT_8)
                        }
                        if (bytes.size >= 3) {
                            key = key or ((bytes[2].toInt() and C.BYTE_MASK) shl C.BIT_SHIFT_16)
                        }
                        if (bytes.size >= 4) {
                            key = key or ((bytes[3].toInt() and C.BYTE_MASK) shl C.BIT_SHIFT_24)
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
