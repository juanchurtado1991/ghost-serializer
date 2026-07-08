package com.ghost.serialization.parser

import okio.ByteString.Companion.encodeUtf8
import kotlin.math.pow

/**
 * Central repository for all constants used by the Ghost JSON parser and writer.
 * Constants are organized by their role in the serialization lifecycle.
 */
public object GhostJsonConstants {

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
    const val ERR_UNEXPECTED_COMMA = "Unexpected comma"
    const val ERR_EXPECTED_COMMA = "Expected comma"
    const val ERR_EXPECTED_COLON = "Expected ':'"
    const val ERR_EXPECTED_BOOLEAN = "Expected boolean but found "
    const val ERR_EXPECTED_KEY = "Expected key but found "
    const val ERR_EXPECTED_STRING = "Expected string"
    const val ERR_MAX_COLLECTION_SIZE = "Collection size exceeds maximum allowed"
    const val ERR_EXPECTED_COMMA_OR_CLOSE_ARR = "Expected ',' or ']'"
    const val ERR_EXPECTED_COMMA_OR_CLOSE_OBJ = "Expected ',' or '}'"
    const val ERR_UNEXPECTED_EOF = "Unexpected end of input"
    const val ERR_EXPECTED_QUOTE = "Expected '\"'"
    const val ERR_EXPECTED_SINGLE_CHAR_STRING = "Expected single-character JSON string"
    const val ERR_SINGLE_CHAR_STRING_WRONG_LENGTH = "Expected single-character JSON string, found length "
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
    const val EXPONENT_CLAMP_THRESHOLD = 1000
    const val ERR_EXPECTED_LITERAL = "Expected literal "
    const val ERR_CAPACITY_OVERFLOW_PREFIX = "FlatByteArrayWriter capacity overflow: "
    const val ERR_TEXT_CHANNEL_DISABLED = "String deserialization is disabled. Please configure arg(\"ghost.textChannel\", \"true\") in your KSP options to use the String parser."

    // --- Digit & Limit Constants ---
    const val MIN_SINGLE_DIGIT = 0
    const val MAX_SINGLE_DIGIT = 9
    const val MIN_SINGLE_DIGIT_NEG = -9
    const val MAX_SINGLE_DIGIT_NEG = -1
    const val MIN_SINGLE_DIGIT_L = 0L
    const val MAX_SINGLE_DIGIT_L = 9L
    const val MIN_SINGLE_DIGIT_NEG_L = -9L
    const val MAX_SINGLE_DIGIT_NEG_L = -1L
    const val MIN_INT_STR = "-2147483648"
    const val MIN_LONG_STR = "-9223372036854775808"

    // --- Escape String Constants ---
    const val ESCAPE_QUOTE = "\\\""
    const val ESCAPE_BACKSLASH = "\\\\"
    const val ESCAPE_BACKSPACE = "\\b"
    const val ESCAPE_FORM_FEED = "\\f"
    const val ESCAPE_NEWLINE = "\\n"
    const val ESCAPE_CARRIAGE_RETURN = "\\r"
    const val ESCAPE_TAB = "\\t"

    // --- Character Constants ---
    const val CHAR_QUOTE = '"'
    const val CHAR_T = 't'
    const val CHAR_R = 'r'
    const val CHAR_U = 'u'
    const val CHAR_E = 'e'
    const val CHAR_F = 'f'
    const val CHAR_A = 'a'
    const val CHAR_L = 'l'
    const val CHAR_S = 's'
    const val CHAR_N = 'n'
    const val CHAR_DOT = '.'
    const val CHAR_ZERO = '0'
    const val CHAR_BACKSLASH = '\\'
    const val CHAR_B = 'b'
    const val CHAR_HYPHEN = '-'
    const val CHAR_COLON = ':'
    const val CHAR_T_UPPER = 'T'
    const val CHAR_Z_UPPER = 'Z'
    const val CHAR_Z_LOWER = 'z'
    const val CHAR_COMMA = ','
    const val CHAR_UNDERSCORE = '_'

    const val ESC_B_INT = 98
    const val ESC_F_INT = 102
    const val ESC_N_INT = 110
    const val ESC_R_INT = 114
    const val ESC_T_INT = 116

    // --- Case-Folded ASCII Byte Constants ---
    // Each constant is `'X'.code or 32` where 32 = CASE_INSENSITIVE_MASK.
    // Folding sets bit 5 of the byte, which turns any ASCII uppercase letter into
    // its lowercase equivalent. Allows zero-allocation case-insensitive comparisons:
    //   `(rawByte or CASE_INSENSITIVE_MASK) == FOLD_X`

    /** Case-folded byte for 'T' / 't'. Used in "true". */
    const val FOLD_T = 't'.code or 32
    /** Case-folded byte for 'R' / 'r'. Used in "true". */
    const val FOLD_R = 'r'.code or 32
    /** Case-folded byte for 'U' / 'u'. Used in "true". */
    const val FOLD_U = 'u'.code or 32
    /** Case-folded byte for 'E' / 'e'. Used in "true", "false", "yes". */
    const val FOLD_E = 'e'.code or 32
    /** Case-folded byte for 'F' / 'f'. Used in "false", "off". */
    const val FOLD_F = 'f'.code or 32
    /** Case-folded byte for 'A' / 'a'. Used in "false". */
    const val FOLD_A = 'a'.code or 32
    /** Case-folded byte for 'L' / 'l'. Used in "false". */
    const val FOLD_L = 'l'.code or 32
    /** Case-folded byte for 'S' / 's'. Used in "false", "yes". */
    const val FOLD_S = 's'.code or 32
    /** Case-folded byte for 'Y' / 'y'. Used in "yes", "y". */
    const val FOLD_Y = 'y'.code or 32
    /** Case-folded byte for 'N' / 'n'. Used in "no", "n". */
    const val FOLD_N = 'n'.code or 32
    /** Case-folded byte for 'O' / 'o'. Used in "on", "no", "off". */
    const val FOLD_O = 'o'.code or 32

    /** String lengths used as a fast gate before byte-level coercion matching. */
    const val BOOL_STR_LEN_1 = 1   // "y", "n", "1", "0"
    const val BOOL_STR_LEN_2 = 2   // "on", "no"
    const val BOOL_STR_LEN_3 = 3   // "yes", "off"
    const val BOOL_STR_LEN_4 = 4   // "true"
    const val BOOL_STR_LEN_5 = 5   // "false"

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

    /** Bitmask for JSON whitespace: Space (32), LF (10), CR (13), HT (9). */
    const val WHITESPACE_MASK = (1L shl 32) or (1L shl 10) or (1L shl 13) or (1L shl 9)

    /** Standard mask for unsigned byte access. */
    const val BYTE_MASK = 0xFF
    /** Standard mask for unsigned Long byte access. */
    const val LONG_BYTE_MASK = 0xFFL

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
    const val NINE_INT = '9'.code
    const val DEFAULT_PRIMITIVE_COLLECTION_CAPACITY = 16
    const val BACKSLASH_INT = '\\'.code
    const val UNICODE_PREFIX_U_INT = 'u'.code
    const val EXP_LOWER_INT = 'e'.code
    const val EXP_UPPER_INT = 'E'.code

    // --- ASCII Token Codes (Bytes) ---
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

    // --- Dispatch Table Defaults ---
    /** Default shift for JsonReaderOptions when no perfect-hash search has been run. */
    const val DEFAULT_DISPATCH_SHIFT = 0
    /** Default multiplier for JsonReaderOptions factory methods. */
    const val DEFAULT_DISPATCH_MULTIPLIER = 31
    /** Default dispatch table size. Must be a power of two. */
    const val DEFAULT_DISPATCH_TABLE_SIZE = 1024
    /** Polynomial multiplier for collision disambiguation (must match all reader computeKeyHash and PerfectHashFinder). */
    const val COLLISION_HASH_MULTIPLIER = 31

    // --- Pooling & Cache Metrics ---
    /** Number of buckets in the string reuse pool. Must be power of two. */
    const val STR_POOL_SIZE = 2048
    /** Multiplier for string pool hashing. */
    const val STR_POOL_HASH_MULTIPLIER = 31

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

    /** Shift factor when growing buffer capacity by 1.5. */
    const val CAPACITY_GROWTH_SHIFT = 1

    /** Byte sizes for common UTF/JSON sequences. */
    const val SINGLE_CHAR_SIZE = 1

    /** Expected UTF-16 code-unit count when decoding a JSON [Char] field. */
    const val SINGLE_CHAR_JSON_LENGTH = SINGLE_CHAR_SIZE
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

    const val INITIAL_WRITE_BUFFER_SIZE = 8 * 1024
    const val STREAMING_BUFFER_SIZE = 8192

    const val DEFAULT_DISCRIMINATOR_KEY = "type"

    // --- Mathematical Tables ---
    /** Pre-calculated powers of ten to avoid expensive Math.pow calls. */
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

    // --- Lookup Tables (LUTs) ---
    /** Hexadecimal character bytes. */
    val HEX_CHARS = "0123456789abcdef".encodeToByteArray()

    /** Hexadecimal characters. */
    val HEX_CHARS_CHARS = CharArray(16) { i -> "0123456789abcdef"[i] }

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

    /** Stores two ASCII characters per number (00-99) for fast numeric formatting. */
    val DOUBLE_DIGIT_LUT_CHARS = CharArray(200) { i ->
        val num = i / 2
        if (i % 2 == 0) (num / 10 + '0'.code).toChar() else (num % 10 + '0'.code).toChar()
    }

    /** Mask to extract the bit index within a 64-bit Long. */
    const val BITMASK_INDEX_MASK = 63

    /** Maximum depth supported by 64-bit Long bitmask. */
    const val MAX_BITMASK_DEPTH = 64

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
    const val RESET_TOKEN_BYTE = -1
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
    const val U_BYTE_INT = 117
    const val A_BYTE_INT = 97
    const val L_BYTE_INT = 108
    const val S_BYTE_INT = 115
    const val E_BYTE_INT = 101

    // --- ProtoJSON Constants ---
    const val I_BYTE_INT = 73 // 'I'.code
    const val PROTO_AT_BYTE = 64 // '@'.code
    const val PROTO_S_BYTE = 115 // 's'.code
    const val ERR_PROTO_UINT32_OVERFLOW = "uint32 value exceeds 4294967295"
    const val ERR_PROTO_FRACTIONAL_INT = "Fractional value not allowed for integer type"

    const val N_LOWER_BYTE_INT = 110 // 'n'.code
    const val F_LOWER_BYTE_INT = 102 // 'f'.code
    const val I_LOWER_BYTE_INT = 105 // 'i'.code
    const val T_LOWER_BYTE_INT = 116 // 't'.code
    const val Y_LOWER_BYTE_INT = 121 // 'y'.code
    const val A_LOWER_BYTE_INT = 97  // 'a'.code
    const val N_UPPER_BYTE_INT = 78  // 'N'.code
    const val PROTO_UINT32_MAX = 4294967295L
    const val NAN_QUOTED_LEN = 5
    const val INFINITY_QUOTED_LEN = 10
    const val NEG_INFINITY_QUOTED_LEN = 11

    const val TS_YEAR_START = 0
    const val TS_YEAR_END = 4
    const val TS_MONTH_START = 5
    const val TS_MONTH_END = 7
    const val TS_DAY_START = 8
    const val TS_DAY_END = 10
    const val TS_HOUR_START = 11
    const val TS_HOUR_END = 13
    const val TS_MIN_START = 14
    const val TS_MIN_END = 16
    const val TS_SEC_START = 17
    const val TS_SEC_END = 19
    const val TS_FRAC_START = 20
    const val TS_MIN_LENGTH = 20
    const val TS_TZ_OFFSET_LEN = 6
    const val NANOS_DIGITS = 9

    const val HINNANT_ERA_YEARS = 400L
    const val HINNANT_DAYS_PER_ERA = 146097L
    const val HINNANT_EPOCH_OFFSET = 719468L
    const val HINNANT_DAYS_CYCLE_4 = 1460
    const val HINNANT_DAYS_CYCLE_100 = 36524
    const val HINNANT_DAYS_CYCLE_ERA = 146096
    const val SECONDS_PER_DAY = 86400L
    const val SECONDS_PER_HOUR = 3600L
    const val SECONDS_PER_MINUTE = 60L

    const val WKT_ANY_TYPE = "google.protobuf.Any"
    const val WKT_STRUCT_TYPE = "google.protobuf.Struct"
    const val WKT_VALUE_TYPE = "google.protobuf.Value"
    const val WKT_EMPTY_TYPE = "google.protobuf.Empty"
    const val WKT_FIELDMASK_TYPE = "google.protobuf.FieldMask"
    const val WKT_TIMESTAMP_TYPE = "google.protobuf.Timestamp"
    const val WKT_DURATION_TYPE = "google.protobuf.Duration"
    const val WKT_BOOL_VALUE_TYPE = "google.protobuf.BoolValue"
    const val WKT_STRING_VALUE_TYPE = "google.protobuf.StringValue"
    const val WKT_BYTES_VALUE_TYPE = "google.protobuf.BytesValue"
    const val WKT_DOUBLE_VALUE_TYPE = "google.protobuf.DoubleValue"
    const val WKT_FLOAT_VALUE_TYPE = "google.protobuf.FloatValue"
    const val WKT_INT32_VALUE_TYPE = "google.protobuf.Int32Value"
    const val WKT_INT64_VALUE_TYPE = "google.protobuf.Int64Value"
    const val WKT_UINT32_VALUE_TYPE = "google.protobuf.UInt32Value"
    const val WKT_UINT64_VALUE_TYPE = "google.protobuf.UInt64Value"

    const val PROTO_TYPE_URL_KEY = "@type"

    const val ERR_DURATION_SIGN = "Coherence error: seconds and nanos signs must match"
    const val ERR_DURATION_SUFFIX = "Missing 's' suffix in Duration"
    const val ERR_TIMESTAMP_SHORT = "Malformed timestamp: too short"
    const val ERR_TIMESTAMP_YEAR_HYPHEN = "Malformed timestamp: missing year hyphen"
    const val ERR_TIMESTAMP_MONTH_HYPHEN = "Malformed timestamp: missing month hyphen"
    const val ERR_TIMESTAMP_T = "Malformed timestamp: missing T separator"
    const val ERR_TIMESTAMP_HOUR_COLON = "Malformed timestamp: missing hour colon"
    const val ERR_TIMESTAMP_MINUTE_COLON = "Malformed timestamp: missing minute colon"
    const val ERR_TIMESTAMP_TZ = "Malformed timestamp: missing timezone"
    const val ERR_TIMESTAMP_TZ_SUPPORT = "Unsupported offset timezone"
    const val ERR_REQUIRES_PROTO_READER = "Requires GhostProtoJsonFlatReader"
    const val ERR_MALFORMED_DIGIT = "Malformed digit"

    const val EQUALS_INT = '='.code
    const val EQUALS_BYTE = '='.code.toByte()
    const val NANOS_DIVISOR_LIMIT = 100_000_000
    const val TS_BUFFER_SIZE = 35
    const val DUR_BUFFER_SIZE = 32
    const val LONG_BUFFER_SIZE = 22
    const val BASE64_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/"
    val BASE64_ALPHABET_BYTES = BASE64_ALPHABET.encodeToByteArray()

    const val B64_PAD_DIVISOR = 3
    const val B64_PAD_MULTIPLIER = 4
    const val B64_SHIFT_4 = 4
    const val B64_SHIFT_2 = 2
    const val B64_SHIFT_6 = 6
    const val B64_MASK_6BITS = 63
    const val B64_MASK_4BITS = 15
    const val B64_MASK_2BITS = 3
    const val B64_BYTE_MASK = 0xFF
    const val B64_OFFSET_2 = 2
    const val B64_OFFSET_1 = 1

    val BASE64_LUT = IntArray(256) { -1 }.apply {
        for (i in 0..25) {
            this['A'.code + i] = i
            this['a'.code + i] = 26 + i
        }
        for (i in 0..9) {
            this['0'.code + i] = 52 + i
        }
        this['+'.code] = 62
        this['/'.code] = 63
        this['-'.code] = 62 // URL-safe
        this['_'.code] = 63 // URL-safe
        this['='.code] = -2 // Padding sentinel
    }

    val EMPTY_OBJECT_BS = okio.ByteString.of(123.toByte(), 125.toByte()) // "{}"

    /**
     * Maximum string length for the plain-ASCII writeQuotedAscii fast-path.
     * Strings longer than this still fall through to the scratch-buffer escape path.
     * Chosen to be larger than the scratch buffer so every short JSON string benefits.
     */
    const val PLAIN_ASCII_FAST_PATH_LIMIT = 512

    // --- Control Characters (Int values for parsing) ---
    const val LF_INT = 0x0A
    const val CR_INT = 0x0D
    const val TAB_INT = 0x09
    const val BS_INT = 0x08
    const val FF_INT = 0x0C
    const val LF_CHAR = '\n'
    const val CR_CHAR = '\r'
    const val TAB_CHAR = '\t'
    const val BS_CHAR = '\b'
    const val FF_CHAR = '\u000C'

    // --- Writer Formatting Constants ---
    const val SCRATCH_BUFFER_SIZE = 48
    const val BASE_TEN = 10
    const val BASE_HUNDRED = 100
    const val ASCII_OFFSET = 48

    // --- Quote / Escape Layout ---
    /** Bytes consumed by the opening + closing quotes around a JSON string value. */
    const val STRING_QUOTE_PAIR_BYTES = 2

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

    // --- UTF-8 sequence sizes (char → byte width) ---
    // Used by charToBytePosition / byteToCharPosition in GhostParserUtils.

    /** Width in bytes of a 1-byte (ASCII) UTF-8 code point. */
    const val UTF8_1BYTE_SIZE = 1

    /** Width in bytes of a 2-byte UTF-8 code point (U+0080..U+07FF). */
    const val UTF8_2BYTE_SIZE = 2

    /** Width in bytes of a 3-byte UTF-8 code point (U+0800..U+FFFF, excluding surrogates). */
    const val UTF8_3BYTE_SIZE = 3

    /** Width in bytes of a 4-byte UTF-8 code point (U+10000..U+10FFFF, surrogate pair in Kotlin String). */
    const val UTF8_4BYTE_SIZE = 4

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
