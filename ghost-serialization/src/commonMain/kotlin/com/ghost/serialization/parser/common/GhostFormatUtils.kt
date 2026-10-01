package com.ghost.serialization.parser.common

import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants

/**
 * Precomputed decimal digit lookup tables for fast integer-to-ASCII formatting.
 */
internal object GhostFormatUtils {
    private const val DIGIT_PAIR_TABLE_SIZE = 100

    val DIGIT_TENS = ByteArray(DIGIT_PAIR_TABLE_SIZE)
    val DIGIT_ONES = ByteArray(DIGIT_PAIR_TABLE_SIZE)

    init {
        for (i in 0 until DIGIT_PAIR_TABLE_SIZE) {
            DIGIT_TENS[i] = ((i / GhostJsonWriterConstants.BASE_TEN) + GhostJsonWriterConstants.ASCII_OFFSET).toByte()
            DIGIT_ONES[i] = ((i % GhostJsonWriterConstants.BASE_TEN) + GhostJsonWriterConstants.ASCII_OFFSET).toByte()
        }
    }
}
