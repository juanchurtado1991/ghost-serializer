package com.ghost.serialization.parser.common.constants

/** Fixed JSON syntax bytes/chars and their fast-path case-folded forms. */
object GhostJsonTokens {
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
    const val CHAR_HYPHEN = '-'
    const val CHAR_PLUS = '+'
    const val CHAR_COLON = ':'
    const val CHAR_T_UPPER = 'T'
    const val CHAR_Z_UPPER = 'Z'
    const val CHAR_Z_LOWER = 'z'
    const val CHAR_COMMA = ','
    const val CHAR_UNDERSCORE = '_'

    // --- Case-Folded ASCII Byte Constants ---
    // Each constant is `'X'.code or 32` (32 = CASE_INSENSITIVE_MASK): folding bit 5 turns
    // uppercase into lowercase, enabling zero-allocation compares: `(rawByte or CASE_INSENSITIVE_MASK) == FOLD_X`.
    const val FOLD_T = 't'.code or 32 // "true"
    const val FOLD_R = 'r'.code or 32 // "true"
    const val FOLD_U = 'u'.code or 32 // "true"
    const val FOLD_E = 'e'.code or 32 // "true", "false", "yes"
    const val FOLD_F = 'f'.code or 32 // "false", "off"
    const val FOLD_A = 'a'.code or 32 // "false"
    const val FOLD_L = 'l'.code or 32 // "false"
    const val FOLD_S = 's'.code or 32 // "false", "yes"
    const val FOLD_Y = 'y'.code or 32 // "yes", "y"
    const val FOLD_N = 'n'.code or 32 // "no", "n"
    const val FOLD_O = 'o'.code or 32 // "on", "no", "off"

    /** Bitmask to normalize ASCII uppercase to lowercase (e.g. 'E' or 32 == 'e'). */
    const val CASE_INSENSITIVE_MASK = 32

    /** String lengths used as a fast gate before byte-level coercion matching. */
    const val BOOL_STR_LEN_1 = 1   // "y", "n", "1", "0"
    const val BOOL_STR_LEN_2 = 2   // "on", "no"
    const val BOOL_STR_LEN_3 = 3   // "yes", "off"
    const val BOOL_STR_LEN_4 = 4   // "true"
    const val BOOL_STR_LEN_5 = 5   // "false"

    // Tail lengths after the first character of JSON literals has been consumed.
    const val TRUE_TAIL_LEN = "true".length - 1
    const val FALSE_TAIL_LEN = "false".length - 1
    const val NULL_TAIL_LEN = "null".length - 1
    const val LITERAL_NULL = "null"
    const val LITERAL_NULL_LEN = LITERAL_NULL.length

    /** Length of a `\uXXXX` escape sequence written into the scratch buffer. */
    const val UNICODE_ESCAPE_LENGTH = 6

    const val BYTE_MASK = 0xFF
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
    const val BACKSLASH_INT = '\\'.code
    const val UNICODE_PREFIX_U_INT = 'u'.code
    const val EXP_LOWER_INT = 'e'.code
    const val EXP_UPPER_INT = 'E'.code
    const val EQUALS_INT = '='.code
    const val EQUALS_BYTE = '='.code.toByte()

    // --- ASCII Token Codes (Bytes) ---
    const val CLOSE_OBJ = '}'.code.toByte()
    const val CLOSE_ARR = ']'.code.toByte()
    const val MINUS = '-'.code.toByte()
    const val DOT = '.'.code.toByte()
    const val ZERO = '0'.code.toByte()
    const val BACKSLASH = '\\'.code.toByte()
    const val UNICODE_PREFIX_U = 'u'.code.toByte()

    // --- Control Characters (Int/Char values for parsing) ---
    const val LF_INT = 0x0A
    const val CR_INT = 0x0D
    const val TAB_INT = 0x09
    const val BS_INT = 0x08
    const val FF_INT = 0x0C
    const val LF_CHAR = '\n'
    const val CR_CHAR = '\r'
    const val TAB_CHAR = '\t'
    const val BS_CHAR = '\b'
    const val FF_CHAR = ''
    const val SPACE_INT = 32
    const val QUOTE_BYTE: Byte = 34
    const val CONTROL_CHAR_START_INT = 0
    const val CONTROL_CHAR_LIMIT_INT = 31
    const val ASCII_LIMIT = 128

    // Canonical byte codes for JSON literal ('true'/'false'/'null') and escape-sequence
    // ('\b'/'\f'/'\n'/'\r'/'\t') matching — the same 5 letters serve both purposes.
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

    /** Maps ASCII bytes (0-255) to their hex numeric value (-1 if invalid). */
    val HEX_LUT = IntArray(size = 256) { -1 }.apply {
        for (i in 0..9) this['0'.code + i] = i
        for (i in 0..5) {
            this['A'.code + i] = 10 + i
            this['a'.code + i] = 10 + i
        }
    }
    val HEX_CHARS = "0123456789abcdef".encodeToByteArray()
    val HEX_CHARS_CHARS = CharArray(size = 16) { i -> "0123456789abcdef"[i] }
    const val HEX_MASK = 0xF
    const val UNICODE_HEX_LENGTH = 4

    // --- Unicode Surrogate Pairs ---
    const val SURROGATE_OFFSET = 6
    const val HIGH_SURROGATE_START = 0xD800
    const val HIGH_SURROGATE_END = 0xDBFF
    const val LOW_SURROGATE_START = 0xDC00
    const val LOW_SURROGATE_END = 0xDFFF
    const val UNICODE_BASE = 0x10000
    const val SHIFT_10 = 10
    const val BMP_LIMIT = 0xFFFF

    /** Largest valid Unicode code point (U+10FFFF). */
    const val UNICODE_MAX_CODE_POINT = 0x10FFFF

    // --- Encoding Detection (BOM / NUL sniffing) ---
    const val NUL_BYTE = 0

    /** Sentinel for a byte position that is past `length` during BOM probing. */
    const val ABSENT_BYTE = -1

    /** BOM size to report when no byte-order mark is present. */
    const val NO_BOM = 0
    const val UTF16_UNIT_SIZE = 2
    const val UTF32_UNIT_SIZE = 4

    /** Minimum bytes to request before probing a streaming source for a BOM. */
    const val BOM_PROBE_MIN = 1

    /** Bytes inspected when sniffing the leading BOM / NUL pattern. */
    const val BOM_PROBE_SIZE = 4
    const val UTF8_BOM_0 = 0xEF
    const val UTF8_BOM_1 = 0xBB
    const val UTF8_BOM_2 = 0xBF
    const val UTF8_BOM_SIZE = 3
    const val UTF16_BE_BOM_0 = 0xFE
    const val UTF16_LE_BOM_0 = 0xFF

    val EMPTY_BYTES = ByteArray(0)
    const val DEFAULT_DISCRIMINATOR_KEY = "type"

    /** Warm-up payload fed through the parser once to trigger JIT/ART compilation. */
    const val WARM_RAW_JSON_PAYLOAD = """{"warm":true}"""
    const val TYPE_NAME_RAW_JSON = "RawJson"

    // --- Type Names (codegen `typeName` reflection strings) ---
    const val TYPE_NAME_STRING = "String"
    const val TYPE_NAME_INT = "Int"
    const val TYPE_NAME_LONG = "Long"
    const val TYPE_NAME_FLOAT = "Float"
    const val TYPE_NAME_DOUBLE = "Double"
    const val TYPE_NAME_BOOLEAN = "Boolean"
    const val TYPE_NAME_BYTE = "Byte"
    const val TYPE_NAME_SHORT = "Short"
    const val TYPE_NAME_CHAR = "Char"
    const val TYPE_NAME_INT_ARRAY = "IntArray"
    const val TYPE_NAME_LONG_ARRAY = "LongArray"
    const val TYPE_NAME_FLOAT_ARRAY = "FloatArray"
    const val TYPE_NAME_DOUBLE_ARRAY = "DoubleArray"
    const val TYPE_NAME_BOOLEAN_ARRAY = "BooleanArray"
    const val TYPE_NAME_LIST_PREFIX = "List<"
    const val TYPE_NAME_SET_PREFIX = "Set<"
    const val TYPE_NAME_MAP_STRING_PREFIX = "Map<String, "
    const val TYPE_NAME_GENERIC_SUFFIX = ">"
}