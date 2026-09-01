package com.ghost.serialization.yaml

/**
 * Byte-level constants for YAML control characters.
 *
 * Every control byte used by the YAML parser is declared here with a descriptive name.
 * Call sites should use these named constants instead of raw byte literals, and compare
 * bytes directly (Byte to Byte) rather than converting to [Char].
 */
@PublishedApi
internal object GhostYamlConstants {

    // ── Basic ASCII structure ──────────────────────────────────────────────────

    /** ':' — key-value separator */
    const val COLON_BYTE: Byte = 0x3A

    /** ' ' — space (used after ':' and '-' as mandatory separator) */
    const val SPACE_BYTE: Byte = 0x20

    /** '\n' — line feed (primary line terminator) */
    const val NEWLINE_BYTE: Byte = 0x0A

    /** '\r' — carriage return (CRLF support) */
    const val CR_BYTE: Byte = 0x0D

    /** '\t' — horizontal tab (valid whitespace in YAML) */
    const val TAB_BYTE: Byte = 0x09

    /** '#' — comment start */
    const val HASH_BYTE: Byte = 0x23

    /** '-' — block sequence entry / negative number / block scalar chomp */
    const val DASH_BYTE: Byte = 0x2D

    /** '?' — explicit block mapping key indicator */
    const val QUESTION_BYTE: Byte = 0x3F

    /** '.' — document end marker start / float decimal point */
    const val DOT_BYTE: Byte = 0x2E

    // ── String delimiters ─────────────────────────────────────────────────────

    /** '"' — double-quoted scalar start/end */
    const val DOUBLE_QUOTE_BYTE: Byte = 0x22

    /** '\'' — single-quoted scalar start/end */
    const val SINGLE_QUOTE_BYTE: Byte = 0x27

    /** '\\' — escape character inside double-quoted scalars */
    const val BACKSLASH_BYTE: Byte = 0x5C

    // ── Block scalar indicators ───────────────────────────────────────────────

    /** '|' — literal block scalar indicator */
    const val PIPE_BYTE: Byte = 0x7C

    /** '>' — folded block scalar indicator */
    const val GT_BYTE: Byte = 0x3E

    /** '+' — keep chomp indicator (after '|' or '>') */
    const val PLUS_BYTE: Byte = 0x2B

    // ── Flow style delimiters ─────────────────────────────────────────────────

    /** '{' — flow mapping start */
    const val LEFT_BRACE_BYTE: Byte = 0x7B

    /** '}' — flow mapping end */
    const val RIGHT_BRACE_BYTE: Byte = 0x7D

    /** '[' — flow sequence start */
    const val LEFT_BRACKET_BYTE: Byte = 0x5B

    /** ']' — flow sequence end */
    const val RIGHT_BRACKET_BYTE: Byte = 0x5D

    /** ',' — flow collection item separator */
    const val COMMA_BYTE: Byte = 0x2C

    // ── Anchors, Aliases, Tags, Directives ───────────────────────────────────

    /** '&' — anchor definition start */
    const val AMPERSAND_BYTE: Byte = 0x26

    /** '*' — alias reference start */
    const val ASTERISK_BYTE: Byte = 0x2A

    /** '!' — tag indicator (e.g. !!str, !<TypeName>) */
    const val EXCLAMATION_BYTE: Byte = 0x21

    /** '%' — YAML directive start (%YAML, %TAG) */
    const val PERCENT_BYTE: Byte = 0x25

    /** '<' — opening bracket in verbose tags !<TypeName> */
    const val LT_BYTE: Byte = 0x3C

    // ── Document markers ──────────────────────────────────────────────────────

    /** '—' first byte of document-start marker '---' */
    // Same as DASH_BYTE. Marker is detected by checking 3 consecutive DASH_BYTE at column 0.

    // ── Numeric helpers ───────────────────────────────────────────────────────

    /** '0' */
    const val ZERO_BYTE: Byte = 0x30

    /** '9' */
    const val NINE_BYTE: Byte = 0x39

    /** 'x' */
    const val LOWERCASE_X_BYTE: Byte = 0x78

    /** 'X' */
    const val UPPERCASE_X_BYTE: Byte = 0x58

    /** 'o' */
    const val LOWERCASE_O_BYTE: Byte = 0x6F

    /** 'O' */
    const val UPPERCASE_O_BYTE: Byte = 0x4F

    /** 'b' */
    const val LOWERCASE_B_BYTE: Byte = 0x62

    /** 'B' */
    const val UPPERCASE_B_BYTE: Byte = 0x42

    // ── Tag Character constants ──────────────────────────────────────────────
    const val CHAR_A_BYTE: Byte = 0x61
    const val CHAR_B_BYTE: Byte = 0x62
    const val CHAR_E_BYTE: Byte = 0x65
    const val CHAR_F_BYTE: Byte = 0x66
    const val CHAR_I_BYTE: Byte = 0x69
    const val CHAR_L_BYTE: Byte = 0x6C
    const val CHAR_M_BYTE: Byte = 0x6D
    const val CHAR_N_BYTE: Byte = 0x6E
    const val CHAR_O_BYTE: Byte = 0x6F
    const val CHAR_P_BYTE: Byte = 0x70
    const val CHAR_Q_BYTE: Byte = 0x71
    const val CHAR_R_BYTE: Byte = 0x72
    const val CHAR_S_BYTE: Byte = 0x73
    const val CHAR_T_BYTE: Byte = 0x74
    const val CHAR_U_BYTE: Byte = 0x75
    const val LOWERCASE_A_BYTE: Byte = 0x61
    const val UPPERCASE_A_BYTE: Byte = 0x41
    const val ONE_BYTE: Byte = 0x31
    const val SEVEN_BYTE: Byte = 0x37

    const val ESCAPE_SLASH_BYTE: Byte = 0x2F
    const val LOWERCASE_E_BYTE: Byte = 0x65
    const val UPPERCASE_E_BYTE: Byte = 0x45
    const val LOWERCASE_L_BYTE: Byte = 0x6C
    const val UPPERCASE_L_BYTE: Byte = 0x4C
    const val LOWERCASE_R_BYTE: Byte = 0x72
    const val LOWERCASE_U_BYTE: Byte = 0x75
    const val UPPERCASE_U_BYTE: Byte = 0x55
    const val LOWERCASE_V_BYTE: Byte = 0x76
    const val UPPERCASE_P_BYTE: Byte = 0x50
    const val UNDERSCORE_BYTE: Byte = 0x5F
    const val LOWERCASE_S_BYTE: Byte = 0x73

    // ── Boolean / null scalar first bytes ────────────────────────────────────

    /** 't' — start of 'true' */
    const val LOWERCASE_T_BYTE: Byte = 0x74

    /** 'T' — start of 'True' / 'TRUE' */
    const val UPPERCASE_T_BYTE: Byte = 0x54

    /** 'f' — start of 'false' */
    const val LOWERCASE_F_BYTE: Byte = 0x66

    /** 'F' — start of 'False' / 'FALSE' */
    const val UPPERCASE_F_BYTE: Byte = 0x46

    /** 'n' — start of 'null' / 'Null' / 'NULL' */
    const val LOWERCASE_N_BYTE: Byte = 0x6E

    /** 'N' — start of 'Null' / 'NULL' */
    const val UPPERCASE_N_BYTE: Byte = 0x4E

    /** '~' — YAML null shorthand */
    const val TILDE_BYTE: Byte = 0x7E

    // ── Bitwise masks for hot-path validations ────────────────────────────────

    /** Bounds for an ASCII decimal digit (0-9), used as `b in DIGIT_LOWER_BOUND..DIGIT_UPPER_BOUND`. */
    const val DIGIT_LOWER_BOUND: Byte = ZERO_BYTE   // 0x30
    const val DIGIT_UPPER_BOUND: Byte = NINE_BYTE   // 0x39

    /** Mask to convert a known-alphabetic lowercase ASCII byte to uppercase. */
    const val ASCII_TO_UPPER_MASK: Int = 0xDF

    /** Mask to convert a known-alphabetic uppercase ASCII byte to lowercase. */
    const val ASCII_TO_LOWER_MASK: Int = 0x20

    // ── Scalar byte packing (same as JSON reader) ─────────────────────────────

    /**
     * Maximum key length for perfect-hash dispatch.
     * Keys longer than this use a fallback linear comparison.
     */
    const val MAX_PACKED_KEY_BYTES: Int = 8

    // ── Indentation ───────────────────────────────────────────────────────────

    /** Sentinel value for "no indentation level set yet". */
    const val INDENT_UNSET: Int = -1

    /** Maximum supported nesting depth. */
    const val MAX_DEPTH: Int = 64

    // ── String literals / Control strings ──────────────────────────────────────
    const val STR_TRUE = "true"
    const val STR_FALSE = "false"
    const val STR_DOT_INF = ".inf"
    const val STR_PLUS_DOT_INF = "+.inf"
    const val STR_MINUS_DOT_INF = "-.inf"
    const val STR_DOT_NAN = ".nan"

    const val STR_MERGE_KEY = "<<"
    const val STR_TAG_KEY = "_tag"
    const val STR_TAG_DIRECTIVE = "TAG"
    const val STR_YAML_DIRECTIVE = "YAML"
    const val STR_EXCLAMATION = "!"

    // ── Bit shift constants for parsing numeric bases ──────────────────────────
    const val HEX_SHIFT = 4
    const val OCTAL_SHIFT = 3
    const val BINARY_SHIFT = 1

    // ── Int counterparts for control characters (Writer path) ──────────────────
    const val SPACE_INT: Int = 0x20
    const val DASH_INT: Int = 0x2D
    const val NEWLINE_INT: Int = 0x0A
    const val DOUBLE_QUOTE_INT: Int = 0x22
    const val BACKSLASH_INT: Int = 0x5C
    const val COLON_INT: Int = 0x3A
    const val ZERO_INT: Int = 0x30
    const val TILDE_INT: Int = 0x7E
    const val LEFT_BRACE_INT: Int = 0x7B
    const val RIGHT_BRACE_INT: Int = 0x7D
    const val LEFT_BRACKET_INT: Int = 0x5B
    const val RIGHT_BRACKET_INT: Int = 0x5D

    const val CHAR_LF_INT: Int = 10
    const val CHAR_CR_INT: Int = 13
    const val CHAR_TAB_INT: Int = 9
    const val CHAR_BS_INT: Int = 8
    const val CHAR_FF_INT: Int = 12
    const val CHAR_SPACE_INT: Int = 32

    const val CHAR_N_INT: Int = 0x6E
    const val CHAR_R_INT: Int = 0x72
    const val CHAR_T_INT: Int = 0x74
    const val CHAR_B_INT: Int = 0x62
    const val CHAR_F_INT: Int = 0x66
    const val CHAR_U_INT: Int = 0x75

    const val STR_HEX_CHARS = "0123456789abcdef"
    const val STR_NULL = "null"
    const val STR_MIN_LONG_ABS = "9223372036854775808"

    val HEX_CHARS_ARR = STR_HEX_CHARS.encodeToByteArray()

    // ── Structural & formatting helpers (Writer path) ──────────────────────────
    const val SPACES_PER_LEVEL = 2
    const val TYPE_OBJECT = 1
    const val TYPE_ARRAY = 2
    const val PLAIN_ASCII_LIMIT = 64
    const val SHIFT_12 = 12
    const val SHIFT_8 = 8
    const val SHIFT_4 = 4
    const val HEX_MASK = 0x0F
    const val TEN_LONG = 10L
    const val ASCII_LIMIT = 128

    // ── Pre-encoded JSON Header extraction constants ──────────────────────────
    const val HEADER_MIN_SIZE = 3
    const val HEADER_QUOTE_START_OFFSET = 0
    const val HEADER_QUOTE_END_OFFSET_SUB = 2
    const val HEADER_COLON_OFFSET_SUB = 1
    const val SUBSTRING_START_OFFSET = 1

    // ── UTF-8 and Escape code parsing constants (Reader path) ──────────────────
    const val BUFFER_SCALE_FACTOR = 2
    const val UTF8_1BYTE_MAX = 0x7F
    const val UTF8_2BYTE_MAX = 0x7FF
    const val UTF8_3BYTE_MAX = 0xFFFF
    const val UTF8_2BYTE_PREFIX = 0xC0
    const val UTF8_3BYTE_PREFIX = 0xE0
    const val UTF8_4BYTE_PREFIX = 0xF0
    const val UTF8_CONT_PREFIX = 0x80
    const val UTF8_CONT_MASK = 0x3F
    const val HEX_SHIFT_4 = 4
    const val HEX_RADIX_10 = 10
    const val SHIFT_18_BITS = 18
    const val SHIFT_6_BITS = 6

    const val CODE_ZERO = 0
    const val CODE_BEL = 7
    const val CODE_BS = 8
    const val CODE_TAB = 9
    const val CODE_FF = 12
    const val CODE_CR = 13
    const val CODE_VTAB = 11
    const val CODE_ESC = 27
    const val CODE_NEXT_LINE = 133
    const val CODE_NBSP = 160
    const val CODE_LINE_SEP = 8232
    const val CODE_PARA_SEP = 8233

    // ── Document markers ─────────────────────────────────────────────────────
    const val STR_DOC_START = "---"
    const val STR_DOC_END = "..."
    const val DOC_MARKER_LEN = 3

    // ── Sizes ────────────────────────────────────────────────────────────────
    const val DEFAULT_MAP_CAPACITY = 8
    const val SCRATCH_BUFFER_SIZE = 256
    const val HEX_ESCAPE_X_LEN = 2
    const val HEX_ESCAPE_U_LEN = 4
    const val HEX_ESCAPE_U32_LEN = 8

    // ── Error messages (parser / writer / extensions) ────────────────────────
    const val ERR_EXPECTED_MAP_PREFIX = "Expected Map but found "
    const val ERR_EXPECTED_INT_PREFIX = "Expected Int but found "
    const val ERR_EXPECTED_LONG_PREFIX = "Expected Long but found "
    const val ERR_EXPECTED_ULONG_PREFIX = "Expected ULong but found "
    const val ERR_EXPECTED_DOUBLE_PREFIX = "Expected Double but found "
    const val ERR_EXPECTED_FLOAT_PREFIX = "Expected Float but found "
    const val ERR_EXPECTED_BOOLEAN_PREFIX = "Expected Boolean but found "
    const val ERR_EXPECTED_SINGLE_CHAR_LEN_PREFIX = "Expected single-character string but found length "
    const val ERR_EXPECTED_LIST_PREFIX = "Expected List but found "

    const val ERR_PLAIN_SCALAR_PERCENT =
        "A plain scalar cannot start with '%' — reserved for directives"
    const val ERR_MAX_NESTING_DEPTH_PREFIX = "Maximum nesting depth ("
    const val ERR_MAX_NESTING_DEPTH_SUFFIX = ") exceeded"
    const val ERR_TAB_IN_BLOCK_MAPPING_INDENT =
        "Tab character not allowed in block mapping indentation"
    /** YAML [GhostYamlException] position suffix: `"$message$ERR_AT_POSITION_PAREN_PREFIX$pos$ERR_AT_POSITION_PAREN_SUFFIX"`. */
    const val ERR_AT_POSITION_PAREN_PREFIX = " (position="
    const val ERR_AT_POSITION_PAREN_SUFFIX = ")"
    const val ERR_EXPECTED_COLON_AFTER_KEY_PREFIX = "Expected ':' after key '"
    const val ERR_EXPECTED_COLON_AFTER_KEY_MID = "' at position "
    const val ERR_TAB_IN_BLOCK_SEQUENCE_INDENT =
        "Tab character not allowed in block sequence indentation"
    const val ERR_UNEXPECTED_INLINE_NESTED_MAPPING_COLON =
        "Unexpected ':' — a nested mapping can't start inline on the same line as its enclosing key"
    const val ERR_LONE_DASH_IN_FLOW =
        "A lone '-' is not a valid plain scalar in flow context"
    const val ERR_PLAIN_CONTINUATION_MAPPING_KEY =
        "Plain scalar continuation cannot contain a mapping key indicator"
    const val ERR_ANCHOR_TAG_PREFIX_NEEDS_KEY =
        "Anchor/tag prefix on a key must be followed by the key itself"
    const val ERR_IMPLICIT_KEY_MULTILINE = "Implicit keys cannot span multiple lines"
    const val ERR_PARSER_NO_PROGRESS_PREFIX = "Parser made no progress at position "
    const val ERR_PARSER_NO_PROGRESS_SUFFIX = " — malformed content"
    const val ERR_UNEXPECTED_AFTER_DOCUMENT_VALUE = "Unexpected content after document value"
    const val ERR_DIRECTIVES_NEED_DOC_END =
        "Directives must be preceded by an explicit document end marker (...)"

    const val ERR_UNEXPECTED_COMMA_FLOW_MAPPING =
        "Unexpected ',' in flow mapping — empty entries are not allowed"
    const val ERR_EXPECTED_COLON_AFTER_FLOW_KEY_PREFIX = "Expected ':' after flow mapping key '"
    const val ERR_EXPECTED_COMMA_OR_CLOSE_FLOW_MAP = "Expected ',' or '}' in flow mapping"
    const val ERR_UNEXPECTED_COMMA_FLOW_SEQUENCE =
        "Unexpected ',' in flow sequence — empty entries are not allowed"
    const val ERR_EXPECTED_COMMA_OR_CLOSE_FLOW_SEQ = "Expected ',' or ']' in flow sequence"

    const val ERR_UNTERMINATED_DOUBLE_QUOTED = "Unterminated double-quoted string"
    const val ERR_UNTERMINATED_SINGLE_QUOTED = "Unterminated single-quoted string"
    const val ERR_DOC_MARKER_IN_QUOTED_SCALAR_PREFIX = "Document marker '"
    const val ERR_DOC_MARKER_IN_QUOTED_SCALAR_SUFFIX = "' not allowed inside a quoted scalar"
    const val ERR_INCOMPLETE_X_ESCAPE = "Incomplete \\x escape"
    const val ERR_INCOMPLETE_U_ESCAPE = "Incomplete \\u escape"
    const val ERR_INCOMPLETE_U32_ESCAPE = "Incomplete \\U escape"
    const val ERR_UNKNOWN_ESCAPE_PREFIX = "Unknown escape: \\"
    const val ERR_INVALID_HEX_IN_ESCAPE = "Invalid hex char in escape sequence"

    const val ERR_BLOCK_INDENT_INDICATOR_DIGIT =
        "Block scalar indentation indicator must be a single digit"
    const val ERR_BLOCK_INDENT_RANGE_1_9 =
        "Block scalar indentation indicator must be between 1 and 9"
    const val ERR_COMMENT_AFTER_BLOCK_INDICATOR_WS =
        "Comment after block scalar indicator must be preceded by whitespace"
    const val ERR_INVALID_TEXT_AFTER_BLOCK_INDICATOR = "Invalid text after block scalar indicator"
    const val ERR_LEADING_EMPTY_LINE_OVERINDENTED =
        "Leading empty line in block scalar is more indented than its first content line"

    const val ERR_EOF_AFTER_TAG = "Unexpected end of input after tag indicator"
    const val ERR_TAG_HANDLE_UNDEFINED_PREFIX = "Tag handle '"
    const val ERR_TAG_HANDLE_UNDEFINED_SUFFIX =
        "' is not defined by a %TAG directive in this document"
    const val ERR_INVALID_CHAR_AFTER_TAG = "Invalid character immediately after tag"

    const val ERR_ANCHOR_FOLLOWED_BY_ALIAS_PREFIX = "Anchor '"
    const val ERR_ANCHOR_FOLLOWED_BY_ALIAS_SUFFIX = "' cannot be immediately followed by an alias"
    const val ERR_ANCHOR_FOLLOWED_BY_SEQ_SUFFIX =
        "' cannot be immediately followed by a block sequence entry on the same line"
    const val ERR_ANCHOR_NOT_FOUND_PREFIX = "Anchor '"
    const val ERR_ANCHOR_NOT_FOUND_SUFFIX = "' not found"

    const val ERR_COMMENT_NEEDS_WHITESPACE = "Comment must be preceded by whitespace"
    const val ERR_DUPLICATE_YAML_DIRECTIVE = "Duplicate %YAML directive"
    const val ERR_MALFORMED_YAML_VERSION_PREFIX = "Malformed %YAML directive version: "
    const val ERR_UNEXPECTED_AFTER_YAML_DIRECTIVE = "Unexpected content after %YAML directive"
    const val ERR_DIRECTIVES_NEED_DOC_START =
        "Directives must be followed by a document-start marker (---)"
    const val ERR_UNEXPECTED_AFTER_DOC_END = "Unexpected content after document-end marker"

    const val ERR_MAX_DEPTH_EXCEEDED = "Max depth exceeded"
    const val ERR_NAME_OUTSIDE_OBJECT = "Cannot write name outside of object scope"

    const val ERR_SERIALIZER_NOT_FOUND_PREFIX = "Serializer not found for "
    const val ERR_NOT_YAML_SERIALIZER_PREFIX = "Serializer for "
    const val ERR_NOT_YAML_SERIALIZER_SUFFIX = " does not implement GhostYamlSerializer"
    const val ERR_YAML_LIST_NEEDS_YAML_ITEM_PREFIX =
        "GhostYamlListSerializer requires a GhostYamlSerializer item serializer, got "
    const val ERR_YAML_SET_NEEDS_YAML_ITEM_PREFIX =
        "GhostYamlSetSerializer requires a GhostYamlSerializer item serializer, got "
    const val ERR_YAML_MAP_NEEDS_YAML_VALUE_PREFIX =
        "GhostYamlMapSerializer requires a GhostYamlSerializer value serializer, got "
    const val STR_UNKNOWN_TYPE = "unknown"
}

