package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import okio.ByteString.Companion.encodeUtf8
import kotlin.math.pow

@PublishedApi
internal object GhostJsonConstants {
    const val UNTERMINATED_STRING_ERROR = "Unterminated string"
    const val UNTERMINATED_ESCAPE_ERROR = "Unterminated escape sequence"
    const val UNTERMINATED_UNICODE_ERROR = "Unterminated unicode escape"
    const val UNESCAPED_CONTROL_CHAR_ERROR = "Unescaped control character in string"
    const val STRICT_MODE_UNKNOWN_FIELD = "Unknown field in strict mode: "
    const val ERR_EXPECTED_BEGIN_OBJ = "Expected '{'"
    const val ERR_EXPECTED_END_OBJ = "Expected '}'"
    const val ERR_EXPECTED_BEGIN_ARR = "Expected '['"
    const val ERR_EXPECTED_END_ARR = "Expected ']'"
    const val ERR_TRAILING_COMMA = "Trailing comma"
    const val ERR_EXPECTED_COLON = "Expected ':'"
    const val ERR_EXPECTED_BOOLEAN = "Expected boolean but found "
    const val ERR_EXPECTED_KEY = "Expected key but found "
    const val ERR_UNTERMINATED_KEY = "Unterminated key"
    const val ERR_EXPECTED_STRING = "Expected string"
    const val ERR_MAX_COLLECTION_SIZE = "Collection size exceeds maximum allowed"
    const val ERR_EXPECTED_COMMA_OR_CLOSE_ARR = "Expected ',' or ']'"
    const val ERR_EXPECTED_COMMA_OR_CLOSE_OBJ = "Expected ',' or '}'"
    const val ERR_UNEXPECTED_EOF = "Unexpected end of input"
    const val ERR_EXPECTED_QUOTE = "Expected '\"'"
    const val ERR_DEPTH_EXCEEDED = "Reached maximum recursion depth"
    const val ERR_NON_FINITE = "JSON does not support non-finite numbers like NaN or Infinity"
    const val ERR_INT_OVERFLOW = "Integer overflow: "
    const val ERR_LONG_OVERFLOW = "Long overflow"
    const val ERR_EXPECTED_NUMBER = "Expected number but reached EOF"
    const val ERR_COERCION_DISABLED = "Unexpected string for numeric type (coercion disabled)"
    const val ERR_ISOLATED_MINUS = "Isolated minus sign"
    const val ERR_LEADING_ZEROS = "Leading zeros are not allowed"
    const val ERR_EXPECTED_COERCION_QUOTE = "Expected closing quote for coerced number"
    const val ERR_EXPECTED_INT_PART = "Expected integer part of number"
    const val ERR_EXPECTED_DECIMAL_DIGITS = "Expected digits after decimal point"
    const val ERR_EXPECTED_EXPONENT_DIGITS = "Expected digits in exponent"
    const val ERR_NUMERIC_OVERFLOW = "Numeric overflow or NaN is not allowed in JSON"


    @PublishedApi
    internal val TRUE_BS = "true".encodeUtf8()
    @PublishedApi
    internal val FALSE_BS = "false".encodeUtf8()
    @PublishedApi
    internal val NULL_BS = "null".encodeUtf8()
    @PublishedApi
    internal val MIN_INT_BS = "-2147483648".encodeUtf8()
    @PublishedApi
    internal val MIN_LONG_BS = "-9223372036854775808".encodeUtf8()
    @PublishedApi
    internal val DOT_ZERO = ".0".encodeToByteArray()
    @PublishedApi
    internal val COLON_QUOTE_BS = "\":".encodeUtf8()
    const val MAX_SAFE_INTEGER_DOUBLE = 1e15
    const val MIN_SAFE_INTEGER_DOUBLE = -1e15
    const val WHOLE_NUMBER_CHECK = 1.0
    const val ZERO_DOUBLE = 0.0

    const val NEWLINE = '\n'.code.toByte()
    const val NEWLINE_INT = '\n'.code

    const val COMMA = ','.code.toByte()
    const val COMMA_INT = ','.code
    const val COLON = ':'.code.toByte()
    const val COLON_INT = ':'.code
    const val QUOTE = '"'.code.toByte()
    const val QUOTE_INT = '"'.code
    const val OPEN_OBJ = '{'.code.toByte()
    const val OPEN_OBJ_INT = '{'.code
    const val CLOSE_OBJ = '}'.code.toByte()
    const val CLOSE_OBJ_INT = '}'.code
    const val OPEN_ARR = '['.code.toByte()
    const val OPEN_ARR_INT = '['.code
    const val CLOSE_ARR = ']'.code.toByte()
    const val CLOSE_ARR_INT = ']'.code
    const val NULL_CHAR = 'n'.code.toByte()
    const val NULL_CHAR_INT = 'n'.code
    const val TRUE_CHAR = 't'.code.toByte()
    const val TRUE_CHAR_INT = 't'.code
    const val FALSE_CHAR = 'f'.code.toByte()
    const val FALSE_CHAR_INT = 'f'.code
    const val MINUS = '-'.code.toByte()
    const val MINUS_INT = '-'.code
    const val PLUS_INT = '+'.code
    const val DOT = '.'.code.toByte()
    const val DOT_INT = '.'.code
    const val ZERO = '0'.code.toByte()
    const val ZERO_INT = '0'.code
    const val ONE_INT = '1'.code
    const val TWO_INT = '2'.code
    const val NINE = '9'.code.toByte()
    const val NINE_INT = '9'.code
    @PublishedApi
    internal const val DIGIT_LIMIT_UINT = 9u
    
    /** Bitmask for ASCII digits '0'-'9' (bits 48-57 set). Used for zero-cast validation. */
    @PublishedApi
    internal const val DIGIT_BITMASK = 0x03FF000000000000L
    const val BACKSLASH = '\\'.code.toByte()
    const val BACKSLASH_INT = '\\'.code
    const val UNICODE_PREFIX_U = 'u'.code.toByte()
    const val UNICODE_PREFIX_U_INT = 'u'.code
    const val EXP_LOWER = 'e'.code.toByte()
    const val EXP_LOWER_INT = 'e'.code
    const val EXP_UPPER = 'E'.code.toByte()
    const val EXP_UPPER_INT = 'E'.code
    const val BYTE_MASK = 0xFF

    @PublishedApi
    internal val TYPE_BS = "type".encodeUtf8()

    // --- STR POOL METRICS ---
    const val STR_POOL_SIZE = 2048
    const val SHIFT_32 = 32
    const val MASK_32 = 0xFFFFFFFFL
    const val HASH_SHIFT = 5
    const val DOUBLE_PRECISION_LIMIT = 17
    const val FLOAT_PRECISION_LIMIT = 9

    val POWERS_OF_TEN = DoubleArray(309).apply {
        for (i in indices) this[i] = 10.0.pow(i.toDouble())
    }

    val INVERSE_POWERS_OF_TEN = DoubleArray(309).apply {
        for (i in indices) this[i] = 1.0 / 10.0.pow(i.toDouble())
    }

    val POWERS_OF_TEN_FLOAT = FloatArray(39).apply {
        for (i in indices) this[i] = 10.0f.pow(i.toDouble().toFloat())
    }

    val INVERSE_POWERS_OF_TEN_FLOAT = FloatArray(39).apply {
        for (i in indices) this[i] = 1.0f / 10.0f.pow(i.toDouble().toFloat())
    }

    // --- HEX LOOKUP TABLE (Literal) ---
    val HEX_LUT = IntArray(256) { -1 }.apply {
        this['0'.code] = 0;  this['1'.code] = 1;  this['2'.code] = 2;  this['3'.code] = 3
        this['4'.code] = 4;  this['5'.code] = 5;  this['6'.code] = 6;  this['7'.code] = 7
        this['8'.code] = 8;  this['9'.code] = 9
        this['A'.code] = 10; this['B'.code] = 11; this['C'.code] = 12; this['D'.code] = 13
        this['E'.code] = 14; this['F'.code] = 15
        this['a'.code] = 10; this['b'.code] = 11; this['c'.code] = 12; this['d'.code] = 13
        this['e'.code] = 14; this['f'.code] = 15
    }

    // --- ESCAPE LOOKUP TABLE (Literal) ---
    val NEEDS_ESCAPE_LUT = booleanArrayOf(
        true,  true,  true,  true,  true,  true,  true,  true,  true,  true,  true,  true,  true,  true,  true,  true,
        true,  true,  true,  true,  true,  true,  true,  true,  true,  true,  true,  true,  true,  true,  true,  true,
        false, false, true,  false, false, false, false, false, false, false, false, false, false, false, false, false,
        false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false,
        false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false,
        false, false, false, false, false, false, false, false, false, false, false, false, true,  false, false, false,
        false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false,
        false, false, false, false, false, false, false, false, false, false, false, false, false, false, false, false
    )

    val ESCAPE_REPLACEMENTS = arrayOfNulls<ByteArray>(128).apply {
        this['"'.code] = "\\\"".encodeToByteArray()
        this['\\'.code] = "\\\\".encodeToByteArray()
        this['\n'.code] = "\\n".encodeToByteArray()
        this['\r'.code] = "\\r".encodeToByteArray()
        this['\t'.code] = "\\t".encodeToByteArray()
        this['\b'.code] = "\\b".encodeToByteArray()
        this['\u000C'.code] = "\\f".encodeToByteArray()
    }

    // --- READER CONSTANTS ---
    const val LONG_OVERFLOW_LIMIT = 922337203685477580L
    const val LONG_MIN_LAST_DIGIT = 8
    const val LONG_MAX_LAST_DIGIT = 7
    const val INT_OVERFLOW_LIMIT = 214748364
    const val INT_MIN_LAST_DIGIT = 8
    const val INT_MAX_LAST_DIGIT = 7
    const val HASH_MASK = 1023
    const val MATCH_END = -1
    const val MATCH_NONE = -2
    const val SHIFT_24 = 24
    const val SHIFT_16 = 16
    const val SHIFT_12 = 12
    const val SHIFT_8 = 8
    const val SHIFT_4 = 4
    const val HEX_MASK = 0xF
    const val UNICODE_HEX_LENGTH = 4
    const val SURROGATE_OFFSET = 6

    const val HIGH_SURROGATE_START = 0xD800
    const val HIGH_SURROGATE_END = 0xDBFF
    const val LOW_SURROGATE_START = 0xDC00
    const val LOW_SURROGATE_END = 0xDFFF
    const val UNICODE_BASE = 0x10000
    const val SHIFT_10 = 10
    const val BMP_LIMIT = 0xFFFF
    const val SURROGATE_LOW_BITS_MASK = 0x3FF

    // --- SCANNING CONSTANTS ---
    const val SEARCH_UNROLL_STEP = 8
    const val SEARCH_UNROLL_LIMIT = 7
    const val SPACE: Byte = 32
    const val SPACE_INT = 32
    const val QUOTE_BYTE: Byte = 34
    const val BACKSLASH_BYTE: Byte = 92
    const val CONTROL_CHAR_START: Byte = 0
    const val CONTROL_CHAR_START_INT = 0
    const val CONTROL_CHAR_LIMIT: Byte = 31
    const val CONTROL_CHAR_LIMIT_INT = 31

    const val U_BYTE: Byte = 117
    const val U_BYTE_INT = 117
    const val N_BYTE: Byte = 110
    const val N_BYTE_INT = 110
    const val R_BYTE: Byte = 114
    const val R_BYTE_INT = 114
    const val T_BYTE: Byte = 116
    const val T_BYTE_INT = 116
    const val B_BYTE: Byte = 98
    const val B_BYTE_INT = 98
    const val F_BYTE: Byte = 102
    const val F_BYTE_INT = 102

    const val BYTE_LF: Byte = 10
    const val BYTE_LF_INT = 10
    const val BYTE_CR: Byte = 13
    const val BYTE_CR_INT = 13
    const val BYTE_TAB: Byte = 9
    const val BYTE_TAB_INT = 9
    const val BYTE_BS: Byte = 8
    const val BYTE_BS_INT = 8
    const val BYTE_FF: Byte = 12
    const val BYTE_FF_INT = 12

    const val UTF8_ONE_BYTE_LIMIT = 0x7F
    const val UTF8_TWO_BYTE_LIMIT = 0x7FF
    const val UTF8_THREE_BYTE_LIMIT = 0xFFFF
    
    const val UTF8_TWO_BYTE_MASK = 0xC0
    const val UTF8_THREE_BYTE_MASK = 0xE0
    const val UTF8_FOUR_BYTE_MASK = 0xF0
    const val UTF8_CONTINUATION_MASK = 0x80
    const val UTF8_CONTINUATION_BITS = 0x3F

    const val SHIFT_18 = 18
    const val SHIFT_6 = 6

    // --- WRITER CONSTANTS ---
    const val SCRATCH_BUFFER_SIZE = 48
    const val BASE_TEN = 10
    const val BASE_HUNDRED = 100
    const val ASCII_OFFSET = 48
    const val ASCII_LIMIT = 128

    const val MAX_DEPTH = 255
    val HEX_CHARS = "0123456789abcdef".encodeToByteArray()

    internal object FormatUtils {
        val DIGIT_TENS = ByteArray(100)
        val DIGIT_ONES = ByteArray(100)

        init {
            for (i in 0 until 100) {
                DIGIT_TENS[i] = ((i / BASE_TEN) + ASCII_OFFSET).toByte()
                DIGIT_ONES[i] = ((i % BASE_TEN) + ASCII_OFFSET).toByte()
            }
        }
    }
}

@Suppress("NOTHING_TO_INLINE")
@InternalGhostApi
inline fun Any?.ignore() {}
