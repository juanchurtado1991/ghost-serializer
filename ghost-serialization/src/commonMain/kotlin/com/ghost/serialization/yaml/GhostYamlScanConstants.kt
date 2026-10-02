package com.ghost.serialization.yaml

import com.ghost.serialization.yaml.GhostYamlTokens.NINE_BYTE
import com.ghost.serialization.yaml.GhostYamlTokens.ZERO_BYTE

/** Reader numeric constants: digit/case masks, nesting/buffer limits, number-base shifts, UTF-8 and escape-sequence
 * decoding. */
@PublishedApi
internal object GhostYamlScanConstants {

    // ── Bitwise masks for hot-path validations ────────────────────────────────

    /** Bounds for an ASCII decimal digit (0-9), used as `b in DIGIT_LOWER_BOUND..DIGIT_UPPER_BOUND`. */
    const val DIGIT_LOWER_BOUND: Byte = ZERO_BYTE   // 0x30
    const val DIGIT_UPPER_BOUND: Byte = NINE_BYTE   // 0x39

    /** Mask to convert a known-alphabetic uppercase ASCII byte to lowercase. */
    const val ASCII_TO_LOWER_MASK: Int = 0x20

    // ── Indentation ───────────────────────────────────────────────────────────

    /** Sentinel value for "no indentation level set yet". */
    const val INDENT_UNSET: Int = -1

    /** Columns a block sequence entry consumes before its value: the `-` and the mandatory space after it. */
    const val SEQUENCE_ENTRY_INDENT: Int = 2

    /** Radix for decimal digit accumulation in plain-scalar number parsing. */
    const val DECIMAL_RADIX: Int = 10

    /** Maximum supported nesting depth. */
    const val MAX_DEPTH: Int = 64

    // ── Bit shift constants for parsing numeric bases ──────────────────────────

    const val HEX_SHIFT = 4
    const val OCTAL_SHIFT = 3
    const val BINARY_SHIFT = 1

    // ── UTF-8 and Escape code parsing constants ──────────────────

    const val BUFFER_SCALE_FACTOR = 2
    const val SHIFT_12 = 12
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

    // ── Sizes ────────────────────────────────────────────────────────────────

    const val DEFAULT_MAP_CAPACITY = 8
    const val SCRATCH_BUFFER_SIZE = 256
    const val HEX_ESCAPE_X_LEN = 2
    const val HEX_ESCAPE_U_LEN = 4
    const val HEX_ESCAPE_U32_LEN = 8
}
