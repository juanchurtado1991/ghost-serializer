package com.ghost.serialization.yaml

/**
 * Byte-level constants for YAML control characters.
 *
 * Every control byte used by the YAML parser is declared here with a descriptive name.
 * PROHIBITED: magic numbers in the hot path. Always use these constants.
 * PROHIBITED: .toChar() comparisons. Always compare Byte to Byte (these constants).
 */
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

    /** '+' is reused as PLUS_BYTE above */

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

    /**
     * Mask to check if a byte is an ASCII decimal digit (0-9).
     *
     * Usage: `(b.toInt() - 0x30) ushr 4 == 0` is cheaper but
     * `b in ZERO_BYTE..NINE_BYTE` compiles to the same range check.
     * For bitwise: `(b.toInt() and 0xF0) == 0x30` does NOT work for all digits.
     * Correct fast check: `(b - 0x30).toUByte() <= 9u`
     */
    const val DIGIT_LOWER_BOUND: Byte = ZERO_BYTE   // 0x30
    const val DIGIT_UPPER_BOUND: Byte = NINE_BYTE   // 0x39

    /**
     * Mask to convert lowercase ASCII letter to uppercase.
     * Apply: `b.toInt() and ASCII_UPPER_MASK` on a known letter byte.
     * PROHIBITED for unknown bytes — only use when byte is confirmed alphabetic.
     */
    const val ASCII_TO_UPPER_MASK: Int = 0xDF

    /**
     * Mask to convert uppercase ASCII letter to lowercase.
     */
    const val ASCII_TO_LOWER_MASK: Int = 0x20.inv().inv() or 0x20  // = 0x20 OR

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
}
