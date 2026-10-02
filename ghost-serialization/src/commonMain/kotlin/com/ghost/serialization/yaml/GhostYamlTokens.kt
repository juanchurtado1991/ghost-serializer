package com.ghost.serialization.yaml

/** Fixed YAML syntax: bytes/ints for structural characters, tag/scalar letters, and literal tokens (booleans, null,
 * merge key, document markers, directive names). */
@PublishedApi
internal object GhostYamlTokens {

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

    // ── Tag / scalar letter bytes ──────────────────────────────────────────────

    const val CHAR_B_BYTE: Byte = 0x62
    const val CHAR_I_BYTE: Byte = 0x69
    const val CHAR_M_BYTE: Byte = 0x6D
    const val CHAR_O_BYTE: Byte = 0x6F
    const val CHAR_P_BYTE: Byte = 0x70
    const val CHAR_Q_BYTE: Byte = 0x71
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

    // ── Int counterparts for control characters ──────────────────

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
    const val CHAR_CR_INT: Int = 13
    const val CHAR_TAB_INT: Int = 9
    const val CHAR_BS_INT: Int = 8
    const val CHAR_FF_INT: Int = 12
    const val CHAR_N_INT: Int = 0x6E
    const val CHAR_R_INT: Int = 0x72
    const val CHAR_T_INT: Int = 0x74
    const val CHAR_B_INT: Int = 0x62
    const val CHAR_F_INT: Int = 0x66
    const val CHAR_U_INT: Int = 0x75
    const val STR_NULL = "null"

    // ── Document markers ─────────────────────────────────────────────────────

    const val STR_DOC_START = "---"
    const val STR_DOC_END = "..."
    const val DOC_MARKER_LEN = 3
}
