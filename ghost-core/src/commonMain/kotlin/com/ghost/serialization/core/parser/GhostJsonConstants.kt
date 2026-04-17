package com.ghost.serialization.core.parser

import okio.ByteString.Companion.encodeUtf8
import kotlin.math.pow

@PublishedApi internal object GhostJsonConstants {
    const val STRING_BUILDER_CAPACITY = 64
    const val NULL_LENGTH = 4L
    const val TRUE_LENGTH = 4L
    const val FALSE_LENGTH = 5L
    const val UNTERMINATED_STRING_ERROR = "Unterminated string"

    @PublishedApi internal val TRUE_BYTES = "true".encodeUtf8()
    @PublishedApi internal val FALSE_BYTES = "false".encodeUtf8()
    @PublishedApi internal val NULL_BYTES = "null".encodeUtf8()

    const val SPACE = ' '.code.toByte()
    const val NEWLINE = '\n'.code.toByte()
    const val CR = '\r'.code.toByte()
    const val TAB = '\t'.code.toByte()

    const val COMMA = ','.code.toByte()
    const val COLON = ':'.code.toByte()
    const val QUOTE = '"'.code.toByte()
    const val BACKSLASH = '\\'.code.toByte()
    const val OPEN_OBJ = '{'.code.toByte()
    const val CLOSE_OBJ = '}'.code.toByte()
    const val OPEN_ARR = '['.code.toByte()
    const val CLOSE_ARR = ']'.code.toByte()
    const val NULL_CHAR = 'n'.code.toByte()
    const val TRUE_CHAR = 't'.code.toByte()
    const val FALSE_CHAR = 'f'.code.toByte()
    const val MINUS = '-'.code.toByte()
    const val PLUS = '+'.code.toByte()
    const val DOT = '.'.code.toByte()
    const val EXP_LOWER = 'e'.code.toByte()
    const val EXP_UPPER = 'E'.code.toByte()

    // --- STR POOL METRICS ---
    const val STR_POOL_SIZE = 2048
    const val MAX_POOL_STRING_LENGTH = 64

    val POWERS_OF_TEN = DoubleArray(309).apply {
        for (i in indices) this[i] = 10.0.pow(i.toDouble())
    }

    val BLOCK_ESCAPE = ByteArray(128).apply {
        for (i in 0..31) this[i] = 1 // Control characters
        this['"'.code] = 1
        this['"'.code] = 1
        this['\\'.code] = 1
    }

    val IS_TERMINATOR = BooleanArray(128).apply {
        ",}] \n\r\t".forEach { this[it.code] = true }
    }
}
