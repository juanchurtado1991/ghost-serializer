package com.ghost.serialization.parser.common

/**
 * Precomputed decimal digit lookup tables for fast integer-to-ASCII formatting.
 */
internal object GhostFormatUtils {
    private const val DIGIT_PAIR_TABLE_SIZE = 100

    val DIGIT_TENS = ByteArray(DIGIT_PAIR_TABLE_SIZE)
    val DIGIT_ONES = ByteArray(DIGIT_PAIR_TABLE_SIZE)

    init {
        for (i in 0 until DIGIT_PAIR_TABLE_SIZE) {
            DIGIT_TENS[i] = ((i / GhostJsonConstants.BASE_TEN) + GhostJsonConstants.ASCII_OFFSET).toByte()
            DIGIT_ONES[i] = ((i % GhostJsonConstants.BASE_TEN) + GhostJsonConstants.ASCII_OFFSET).toByte()
        }
    }
}
