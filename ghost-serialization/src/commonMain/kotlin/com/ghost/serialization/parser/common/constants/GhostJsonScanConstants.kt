package com.ghost.serialization.parser.common.constants

/** SWAR bit-tricks, perfect-hash dispatch, and bitmask packing constants. */
object GhostJsonScanConstants {
    const val BYTE_SHIFT_UNIT = 1L
    const val RESULT_NONE = 0L

    /** Bitmask for JSON whitespace: Space (32), LF (10), CR (13), HT (9). */
    const val WHITESPACE_MASK = (1L shl 32) or (1L shl 10) or (1L shl 13) or (1L shl 9)

    // --- State Sentinels ---
    const val MATCH_END = -1
    const val RESET_TOKEN_BYTE = -1
    const val MATCH_NONE = -2

    // --- SWAR Packing Shifts ---
    const val SHIFT_56 = 56
    const val SHIFT_48 = 48
    const val SHIFT_40 = 40
    const val SHIFT_32 = 32
    const val SHIFT_24 = 24
    const val SHIFT_16 = 16
    const val SHIFT_12 = 12
    const val SHIFT_8 = 8
    const val SHIFT_4 = 4

    /** Number of bytes packed into a Long by [com.ghost.serialization.parser.bytes.ghostReadLong8] for SWAR scanning. */
    const val LONG_BYTES = 8

    /** Number of UTF-16 chars packed into a Long by [com.ghost.serialization.parser.strings.packChars4]. */
    const val LONG_CHARS = 4

    /** Max predicted-key length (in chars) covered by the string-channel SWAR fast path (2 packed Longs). */
    const val MAX_CHAR_FASTPATH_LEN = 2 * LONG_CHARS

    /** A Long whose 8 bytes are all ASCII space (0x20); byte-order-independent (symmetric). */
    const val SPACE_RUN_LONG = 0x2020202020202020L

    /**
     * SWAR "broadcast 1" mask: each of the 8 bytes is 0x01.
     * Used by McIlroy zero-byte detection (`(v - ONES) & ~v & HIGHS`).
     */
    const val SWAR_ONES = 0x0101010101010101L

    /**
     * SWAR high-bit mask: each of the 8 bytes is 0x80 (`0x8080808080808080`).
     * Written as a signed Long literal because bit 63 is set.
     */
    const val SWAR_HIGHS = -0x7f7f7f7f7f7f7f80L

    /** SWAR broadcast of `'"'`; XOR then zero-byte detect finds quotes. */
    const val SWAR_QUOTES = 0x2222222222222222L

    /** SWAR broadcast of `'\\'`; XOR then zero-byte detect finds escapes. */
    const val SWAR_BACKSLASHES = 0x5C5C5C5C5C5C5C5CL

    /** Byte offsets within an 8-byte [com.ghost.serialization.parser.bytes.ghostReadLong8] window (scalar platform assembly). */
    const val LONG_BYTE_OFFSET_1 = 1
    const val LONG_BYTE_OFFSET_2 = 2
    const val LONG_BYTE_OFFSET_3 = 3
    const val LONG_BYTE_OFFSET_4 = 4
    const val LONG_BYTE_OFFSET_5 = 5
    const val LONG_BYTE_OFFSET_6 = 6
    const val LONG_BYTE_OFFSET_7 = 7

    /** Step/offsets for the 4-wide manual loop unroll shared by the byte-scan kernels. */
    const val UNROLL_STEP = 4
    const val UNROLL_OFFSET_1 = 1
    const val UNROLL_OFFSET_2 = 2
    const val UNROLL_OFFSET_3 = 3

    /**
     * Initial / reset value for optimistic in-order field prediction
     * ([com.ghost.serialization.parser.bytes.GhostJsonFlatReader] `predictedFieldIndex`).
     */
    const val FIELD_PREDICTION_START = 0

    /** Hash bits written by [packScanResult] when the SWAR scan skips rolling-hash accumulation. */
    const val SCAN_HASH_NONE = 0

    // --- Dispatch Table Defaults ---
    /** Default shift for JsonReaderOptions when no perfect-hash search has been run. */
    const val DEFAULT_DISPATCH_SHIFT = 0
    const val DEFAULT_DISPATCH_MULTIPLIER = 31

    /** Default dispatch table size. Must be a power of two. */
    const val DEFAULT_DISPATCH_TABLE_SIZE = 1024

    /** Polynomial multiplier for collision disambiguation (must match all reader computeKeyHash and PerfectHashFinder). */
    const val COLLISION_HASH_MULTIPLIER = 31

    /** Bytes packed into one `Int` word by `computeKeyHashCore`; also its collision-scan start offset. */
    const val KEY_HASH_WORD_BYTES = 4

    // --- Pooling & Cache Metrics ---
    /** Number of buckets in the string reuse pool. Must be power of two. */
    const val STR_POOL_SIZE = 4096
    const val STR_POOL_HASH_MULTIPLIER = 31
    const val HASH_SHIFT = 5

    /** Mask to extract the bit index within a 64-bit Long. */
    const val BITMASK_INDEX_MASK = 63

    /** Maximum depth supported by 64-bit Long bitmask. */
    const val MAX_BITMASK_DEPTH = 64

    /** Shift to get the index in the LongArray bitmask (index = charCode shr 6). */
    const val BITMASK_SHIFT = 6

    const val BITMASK_UNIT = 1L

    // --- Scan Result Packing (Long) ---
    /** Mask to extract the 32-bit hash from the scan result Long. */
    const val SCAN_HASH_MASK = 0xFFFFFFFFL

    /** Shift to extract the 31-bit length from the scan result Long. */
    const val SCAN_LENGTH_SHIFT = 32

    /** Bit indicating that the scanned string contains only 7-bit ASCII characters. */
    const val SCAN_7BIT_BIT = 1L shl 63

    /** Mask to extract the 31-bit length from the scan result Long (bits 32-62). */
    const val SCAN_LENGTH_MASK = 0x7FFFFFFF00000000L

    // --- Eight-Digit SWAR Digit Read ---
    
    /**
     * Quoted verbatim from fast_float's ascii_number.h (parse_eight_digits_unrolled /
     * is_made_of_eight_digits_fast) rather than hand-derived. Exhaustively verified against all
     * 100,000,000 possible 8-digit inputs before use.
     */
    const val EIGHT_DIGITS_ASCII_ZERO = 0x3030303030303030L
    const val EIGHT_DIGITS_CHECK_ADD = 0x4646464646464646L
    val EIGHT_DIGITS_HIGH_BIT = 0x8080808080808080UL.toLong()
    const val EIGHT_DIGITS_MASK = 0x000000FF000000FFL
    const val EIGHT_DIGITS_MUL1 = 0x000F424000000064L
    const val EIGHT_DIGITS_MUL2 = 0x0000271000000001L

    /** `10^8`, the multiplier to fold an 8-digit SWAR chunk into an existing mantissa. */
    const val HUNDRED_MILLION = 100_000_000L

    @PublishedApi
    internal fun packScanResult(
        length: Int,
        hash: Int,
        is7Bit: Boolean
    ): Long {
        var result = (length.toLong() shl SCAN_LENGTH_SHIFT) or (hash.toLong() and SCAN_HASH_MASK)
        if (is7Bit) result = result or SCAN_7BIT_BIT
        return result
    }
}