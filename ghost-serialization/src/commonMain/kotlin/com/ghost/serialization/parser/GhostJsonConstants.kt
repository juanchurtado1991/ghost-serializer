package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import okio.ByteString.Companion.encodeUtf8
import kotlin.math.pow

/**
 * Central repository for all constants used by the Ghost JSON parser and writer.
 * Constants are organized by their role in the serialization lifecycle.
 */
@PublishedApi
internal object GhostJsonConstants {

    // --- Error Messages ---
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
    const val ERR_HIGH_SURROGATE = "Lone high surrogate"

    // --- Pre-encoded ByteStrings (Fast-Path Writing) ---
    @PublishedApi
    internal val TRUE_BS = "true".encodeUtf8()
    @PublishedApi
    internal val FALSE_BS = "false".encodeUtf8()
    @PublishedApi
    internal val NULL_BS = "null".encodeUtf8()
    internal val EMPTY_STRING_BS = "\"\"".encodeUtf8()
    @PublishedApi
    internal val MIN_INT_BS = "-2147483648".encodeUtf8()
    @PublishedApi
    internal val MIN_LONG_BS = "-9223372036854775808".encodeUtf8()
    @PublishedApi
    internal val DOT_ZERO = ".0".encodeToByteArray()
    @PublishedApi
    internal val COLON_QUOTE_BS = "\":".encodeUtf8()
    @PublishedApi
    internal val MINUS_ONE_BS = "-1".encodeUtf8()
    @PublishedApi
    internal val TYPE_BS = "type".encodeUtf8()

    // --- Numeric Limits & Formatting ---
    const val MAX_SAFE_INTEGER_DOUBLE = 1e15
    const val MIN_SAFE_INTEGER_DOUBLE = -1e15
    const val WHOLE_NUMBER_CHECK = 1.0
    const val ZERO_DOUBLE = 0.0
    const val HUNDRED_LONG = 100L
    const val TEN_LONG = 10L
    val EMPTY_BYTES = ByteArray(0)

    // --- Bitwise Optimization Constants ---
    /** The unit bit (1L) used for flag shifting. */
    const val BYTE_SHIFT_UNIT = 1L

    /** Result of a bitwise check when no flags match. */
    const val RESULT_NONE = 0L

    /** Bitmask for ASCII digits '0'-'9' (bits 48-57 set). Used for zero-cast validation. */
    @PublishedApi
    internal const val DIGIT_BITMASK = 0x03FF000000000000L

    /** Bitmask for JSON whitespace: Space (32), LF (10), CR (13), HT (9). */
    const val WHITESPACE_MASK = (1L shl 32) or (1L shl 10) or (1L shl 13) or (1L shl 9)

    /** Standard mask for unsigned byte access. */
    const val BYTE_MASK = 0xFF

    // --- ASCII Token Codes (Integers) ---
    const val NEWLINE_INT = '\n'.code
    const val COMMA_INT = ','.code
    const val COLON_INT = ':'.code
    const val QUOTE_INT = '"'.code
    const val OPEN_OBJ_INT = '{'.code
    const val CLOSE_OBJ_INT = '}'.code
    const val OPEN_ARR_INT = '['.code
    const val CLOSE_ARR_INT = ']'.code
    const val NULL_CHAR_INT = 'n'.code
    const val TRUE_CHAR_INT = 't'.code
    const val FALSE_CHAR_INT = 'f'.code
    const val MINUS_INT = '-'.code
    const val PLUS_INT = '+'.code
    const val DOT_INT = '.'.code
    const val ZERO_INT = '0'.code
    const val ONE_INT = '1'.code
    const val TWO_INT = '2'.code
    const val BACKSLASH_INT = '\\'.code
    const val UNICODE_PREFIX_U_INT = 'u'.code
    const val EXP_LOWER_INT = 'e'.code
    const val EXP_UPPER_INT = 'E'.code

    // --- ASCII Token Codes (Bytes) ---
    const val COLON = ':'.code.toByte()
    const val QUOTE = '"'.code.toByte()
    const val OPEN_OBJ = '{'.code.toByte()
    const val CLOSE_OBJ = '}'.code.toByte()
    const val OPEN_ARR = '['.code.toByte()
    const val CLOSE_ARR = ']'.code.toByte()
    const val NULL_CHAR = 'n'.code.toByte()
    const val TRUE_CHAR = 't'.code.toByte()
    const val MINUS = '-'.code.toByte()
    const val DOT = '.'.code.toByte()
    const val ZERO = '0'.code.toByte()
    const val BACKSLASH = '\\'.code.toByte()
    const val UNICODE_PREFIX_U = 'u'.code.toByte()

    // --- Pooling & Cache Metrics ---
    /** Number of buckets in the string reuse pool. Must be power of two. */
    const val STR_POOL_SIZE = 2048

    /** Bit shift used for rolling hash calculation. */
    const val HASH_SHIFT = 5

    /** Bitmask to normalize ASCII uppercase to lowercase (e.g. 'E' or 32 == 'e'). */
    const val CASE_INSENSITIVE_MASK = 32

    /** Max digits of precision for numeric formatting. */
    const val DOUBLE_PRECISION_LIMIT = 17
    const val FLOAT_PRECISION_LIMIT = 9

    // --- Buffer Sizes & Scaling ---
    /** Minimum scratch buffer size for small objects. */
    const val TIER_SMALL_INT = 1024

    /** Scaling factor when growing internal buffers. */
    const val BUFFER_SCALE_FACTOR = 2

    /** Byte sizes for common UTF/JSON sequences. */
    const val SINGLE_CHAR_SIZE = 1
    const val SURROGATE_PAIR_SIZE = 2
    const val UNICODE_ESCAPE_PREFIX_SIZE = 2
    const val INT_SAFE_DIGITS = 9
    const val LONG_SAFE_DIGITS = 18

    const val NUMERIC_HEADER_QUOTED = 1
    const val NUMERIC_HEADER_NEGATIVE = 2

    /** Max depth for nested objects and arrays. */
    const val MAX_DEPTH = 255

    /** Size of the scratch buffer for numeric itoa/dtoa. */
    const val LONG_SCRATCH_SIZE = 24

    /** Size of the hot-path writer scratch buffer. */
    const val WRITER_SCRATCH_SIZE = 512

    /** Initial size for the [com.ghost.serialization.writer.FlatByteArrayWriter]. */
    const val INITIAL_WRITE_BUFFER_SIZE = 8 * 1024

    // --- Mathematical Tables ---
    /** Pre-calculated powers of ten to avoid expensive Math.pow calls. */
    val POWERS_OF_TEN = DoubleArray(309)
        .apply { for (i in indices) this[i] = 10.0.pow(i.toDouble()) }
    val INVERSE_POWERS_OF_TEN = DoubleArray(309)
        .apply { for (i in indices) this[i] = 1.0 / 10.0.pow(i.toDouble()) }
    val POWERS_OF_TEN_FLOAT = FloatArray(39)
        .apply { for (i in indices) this[i] = 10.0f.pow(i.toDouble().toFloat()) }
    val INVERSE_POWERS_OF_TEN_FLOAT = FloatArray(39)
        .apply { for (i in indices) this[i] = 1.0f / 10.0f.pow(i.toDouble().toFloat()) }

    // --- Lookup Tables (LUTs) ---
    /** Hexadecimal character bytes. */
    val HEX_CHARS = "0123456789abcdef".encodeToByteArray()

    /** Maps ASCII bytes (0-255) to their hex numeric value (-1 if invalid). */
    val HEX_LUT = IntArray(256) { -1 }.apply {
        for (i in 0..9) this['0'.code + i] = i
        for (i in 0..5) {
            this['A'.code + i] = 10 + i
            this['a'.code + i] = 10 + i
        }
    }

    /** Stores two ASCII bytes per number (00-99) for fast numeric formatting. */
    val DOUBLE_DIGIT_LUT = ByteArray(200) { i ->
        val num = i / 2
        if (i % 2 == 0) (num / 10 + '0'.code).toByte() else (num % 10 + '0'.code).toByte()
    }

    /** Mask to extract the bit index within a 64-bit Long. */
    const val BITMASK_INDEX_MASK = 63

    /** Shift to get the index in the LongArray bitmask (index = charCode shr 6). */
    const val BITMASK_SHIFT = 6

    /** Unit long for bitwise comparisons. */
    const val BITMASK_UNIT = 1L

    /** Maximum ASCII value (0-127). */
    const val ASCII_LIMIT = 128

    /** Bitmask for ASCII characters 0-63 that require escaping (Controls + Quote). */
    const val NEEDS_ESCAPE_MASK_LOW = 0x4FFFFFFFFL

    /** Bitmask for ASCII characters 64-127 that require escaping (Backslash). */
    const val NEEDS_ESCAPE_MASK_HIGH = 0x10000000L

    /** Combined masks for branch-free lookup. */
    val ESCAPE_MASKS = longArrayOf(NEEDS_ESCAPE_MASK_LOW, NEEDS_ESCAPE_MASK_HIGH)

    /** Pre-encoded replacement bytes for common escape sequences (e.g., \n -> ['\\', 'n']). */
    val ESCAPE_REPLACEMENTS = arrayOfNulls<ByteArray>(128).apply {
        this['"'.code] = "\\\"".encodeToByteArray()
        this['\\'.code] = "\\\\".encodeToByteArray()
        this['\n'.code] = "\\n".encodeToByteArray()
        this['\r'.code] = "\\r".encodeToByteArray()
        this['\t'.code] = "\\t".encodeToByteArray()
        this['\b'.code] = "\\b".encodeToByteArray()
        this['\u000C'.code] = "\\f".encodeToByteArray()
    }

    // --- Parser Internals & Overflow Checks ---
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

    // --- Scanning & Escape Identifiers ---
    const val SPACE_INT = 32
    const val QUOTE_BYTE: Byte = 34
    const val CONTROL_CHAR_START_INT = 0
    const val CONTROL_CHAR_LIMIT_INT = 31
    const val N_BYTE_INT = 110
    const val R_BYTE_INT = 114
    const val T_BYTE_INT = 116
    const val B_BYTE_INT = 98
    const val F_BYTE_INT = 102

    // --- Control Characters (Int values for parsing) ---
    const val LF_INT = 0x0A
    const val CR_INT = 0x0D
    const val TAB_INT = 0x09
    const val BS_INT = 0x08
    const val FF_INT = 0x0C

    // --- Writer Formatting Constants ---
    const val SCRATCH_BUFFER_SIZE = 48
    const val BASE_TEN = 10
    const val BASE_HUNDRED = 100
    const val ASCII_OFFSET = 48

    // --- Quote / Escape Layout ---
    /** Bytes consumed by the opening + closing quotes around a JSON string value. */
    const val STRING_QUOTE_PAIR_BYTES = 2

    /** Number of UTF-16 code units in a Unicode surrogate pair. */
    const val SURROGATE_PAIR_LENGTH = 2

    /** Total bytes in a JSON `\uXXXX` escape sequence (`\` + `u` + 4 hex digits). */
    const val UNICODE_ESCAPE_LENGTH = 6

    // --- UTF-8 Encoding (used by FlatByteArrayWriter) ---
    /** Code point upper bound (exclusive) for the 1-byte UTF-8 form (i.e. ASCII). */
    const val UTF8_1BYTE_LIMIT = 0x80
    const val UTF8_1BYTE_MAX = 0x7F

    /** Code point upper bound (exclusive) for the 2-byte UTF-8 form. */
    const val UTF8_2BYTE_LIMIT = 0x800
    const val UTF8_2BYTE_MAX = 0x7FF

    /** Leading-byte prefix for a 2-byte UTF-8 sequence (110xxxxx). */
    const val UTF8_2BYTE_PREFIX = 0xC0

    /** Leading-byte prefix for a 3-byte UTF-8 sequence (1110xxxx). */
    const val UTF8_3BYTE_PREFIX = 0xE0

    /** Leading-byte prefix for a 4-byte UTF-8 sequence (11110xxx). */
    const val UTF8_4BYTE_PREFIX = 0xF0

    /** Continuation-byte prefix for trailing UTF-8 bytes (10xxxxxx). */
    const val UTF8_CONT_PREFIX = 0x80

    /** Six-bit mask used to extract the payload of a UTF-8 continuation byte. */
    const val UTF8_CONT_MASK = 0x3F

    /** Right-shift by 6 bits when packing UTF-8 continuation bytes. */
    const val UTF8_SHIFT_6 = 6

    /** Right-shift by 12 bits when packing the middle byte of a 3-byte UTF-8 sequence. */
    const val UTF8_SHIFT_12 = 12

    /** Right-shift by 18 bits when packing the leading byte of a 4-byte UTF-8 sequence. */
    const val UTF8_SHIFT_18 = 18

    /** Worst-case number of UTF-8 bytes for any BMP code point (3 + 1 trailing surrogate). */
    const val UTF8_MAX_BMP_BYTES = 4

    /** UTF-8 encoding of the Unicode replacement character `U+FFFD`. */
    val UTF8_REPLACEMENT_CHAR: ByteArray = byteArrayOf(
        0xEF.toByte(),
        0xBF.toByte(),
        0xBD.toByte()
    )

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

    // --- Scan results packing (Long) ---
    /** Mask to extract the 32-bit hash from the scan result Long. */
    const val SCAN_HASH_MASK = 0xFFFFFFFFL

    /** Shift to extract the 31-bit length from the scan result Long. */
    const val SCAN_LENGTH_SHIFT = 32

    /** Bit indicating that the scanned string contains only 7-bit ASCII characters. */
    const val SCAN_7BIT_BIT = 1L shl 63

    /** Mask to extract the 31-bit length from the scan result Long (bits 32-62). */
    const val SCAN_LENGTH_MASK = 0x7FFFFFFF00000000L

    @PublishedApi
    internal fun packScanResult(length: Int, hash: Int, is7Bit: Boolean): Long {
        var res = (length.toLong() shl SCAN_LENGTH_SHIFT) or (hash.toLong() and SCAN_HASH_MASK)
        if (is7Bit) res = res or SCAN_7BIT_BIT
        return res
    }
}

/** Utility extension to suppress unused result warnings in a zero-cost way. */
@Suppress("NOTHING_TO_INLINE")
@InternalGhostApi
inline fun Any?.ignore() { }
