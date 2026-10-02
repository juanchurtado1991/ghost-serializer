package com.ghost.serialization.compiler.internal

/** Perfect-hash table sizing and hashing constants (PerfectHashFinder). */
internal object GhostPerfectHashConstants {

    const val VAL_TWO = 2
    const val VAL_THREE = 3
    const val VAL_FOUR = 4
    const val BIT_SHIFT_32 = 32
    const val LONG_BYTE_MASK = 0xFFL
    const val HASH_MULTIPLIER_START = 31
    const val HASH_MULTIPLIER_LIMIT = 15000
    const val HASH_MULTIPLIER_STEP = 2
    /**  Polynomial multiplier for collision disambiguation. Must match COLLISION_HASH_MULTIPLIER in
     * GhostJsonScanConstants. */
    const val COLLISION_HASH_MULTIPLIER = 31

    const val HASH_SHIFT_LIMIT = 16
    /** Table size used when the field-name set is empty (no dispatch needed). */
    const val PERFECT_HASH_EMPTY_TABLE_SIZE = 128

    const val PERFECT_HASH_TABLE_SIZE_256 = 256
    const val PERFECT_HASH_TABLE_SIZE_512 = 512
    const val PERFECT_HASH_TABLE_SIZE_1024 = 1024
    const val PERFECT_HASH_TABLE_SIZE_2048 = 2048
    const val PERFECT_HASH_TABLE_SIZE_4096 = 4096
    const val PERFECT_HASH_TABLE_SIZE_8192 = 8192
    /** Candidate dispatch table sizes tried by `PerfectHashFinder`, ascending. */
    val PERFECT_HASH_TABLE_SIZES = intArrayOf(
        PERFECT_HASH_EMPTY_TABLE_SIZE,
        PERFECT_HASH_TABLE_SIZE_256,
        PERFECT_HASH_TABLE_SIZE_512,
        PERFECT_HASH_TABLE_SIZE_1024,
        PERFECT_HASH_TABLE_SIZE_2048,
        PERFECT_HASH_TABLE_SIZE_4096,
        PERFECT_HASH_TABLE_SIZE_8192,
    )

    const val BYTE_MASK = 0xFF
    const val BIT_SHIFT_8 = 8
    const val BIT_SHIFT_16 = 16
    const val BIT_SHIFT_24 = 24
    const val STR_ERR_PERFECT_HASH_COLLISION_1 =
        "Could not find a collision-free perfect hash configuration for fields: "

    const val STR_ERR_PERFECT_HASH_COLLISION_2 =
        ". Please ensure there are no duplicate JSON names or conflicting field names."
}
