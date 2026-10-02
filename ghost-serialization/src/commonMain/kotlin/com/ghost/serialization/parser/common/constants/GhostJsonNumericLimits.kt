package com.ghost.serialization.parser.common.constants

import kotlin.math.pow

/** Numeric overflow bounds and formatting precision/tables. */
object GhostJsonNumericLimits {
    const val MIN_INT_STR = "-2147483648"
    const val MIN_LONG_STR = "-9223372036854775808"

    const val MAX_SAFE_INTEGER_DOUBLE = 1e15
    const val MIN_SAFE_INTEGER_DOUBLE = -1e15
    const val WHOLE_NUMBER_CHECK = 1.0
    const val ZERO_DOUBLE = 0.0
    const val HUNDRED_LONG = 100L
    const val TEN_LONG = 10L

    /** Max digits of precision for numeric formatting. */
    const val DOUBLE_PRECISION_LIMIT = 17
    const val FLOAT_PRECISION_LIMIT = 9

    const val DEFAULT_PRIMITIVE_COLLECTION_CAPACITY = 16

    /** Expected UTF-16 code-unit count when decoding a JSON [Char] field. */
    const val SINGLE_CHAR_SIZE = 1
    const val SINGLE_CHAR_JSON_LENGTH = SINGLE_CHAR_SIZE
    const val UNICODE_ESCAPE_PREFIX_SIZE = 2
    const val INT_SAFE_DIGITS = 9
    const val LONG_SAFE_DIGITS = 18

    const val NUMERIC_HEADER_QUOTED = 1
    const val NUMERIC_HEADER_NEGATIVE = 2

    const val MAX_DEPTH = 255

    /**
     * Largest exponent for which `10^n` is exactly representable as a [Double] — beyond this,
     * [POWERS_OF_TEN] entries are themselves rounded, so scaling by them is no longer
     * correctly-rounded. See `finalizeParsedDouble`.
     */
    const val MAX_EXACT_DOUBLE_POWER_OF_TEN = 22

    /** Largest [Long] mantissa exactly representable as a [Double] (2^53). */
    const val MAX_EXACT_DOUBLE_MANTISSA = 1L shl 53

    const val EXPONENT_CLAMP_THRESHOLD = 1000

    // --- Overflow Checks ---
    const val LONG_OVERFLOW_LIMIT = 922337203685477580L
    const val LONG_MIN_LAST_DIGIT = 8
    const val LONG_MAX_LAST_DIGIT = 7
    const val INT_OVERFLOW_LIMIT = 214748364
    const val INT_MIN_LAST_DIGIT = 8
    const val INT_MAX_LAST_DIGIT = 7

    /** Pre-calculated powers of ten to avoid expensive Math.pow calls. */
    val POWERS_OF_TEN = DoubleArray(size = 309).apply {
        for (i in indices) this[i] = 10.0.pow(x = i.toDouble())
    }
    val POWERS_OF_TEN_FLOAT = FloatArray(size = 39).apply {
        for (i in indices) this[i] = 10.0f.pow(x = i.toDouble().toFloat())
    }
    val INVERSE_POWERS_OF_TEN_FLOAT = FloatArray(size = 39).apply {
        for (i in indices) this[i] = 1.0f / 10.0f.pow(x = i.toDouble().toFloat())
    }
}