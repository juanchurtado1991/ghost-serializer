package com.ghost.serialization.yaml

/** Writer-only constants: indentation, context types, hex formatting, plain-scalar limits, pre-encoded header
 * extraction offsets. */
@PublishedApi
internal object GhostYamlWriterConstants {

    // ── Hex formatting ───────────────────────────────────────────────

    const val STR_HEX_CHARS = "0123456789abcdef"
    const val STR_MIN_LONG_ABS = "9223372036854775808"
    val HEX_CHARS_ARR = STR_HEX_CHARS.encodeToByteArray()

    // ── Indentation, context types and plain-scalar limits ───────────

    const val SPACES_PER_LEVEL = 2
    const val TYPE_OBJECT = 1
    const val TYPE_ARRAY = 2
    const val PLAIN_ASCII_LIMIT = 64
    const val SHIFT_12 = 12
    const val SHIFT_8 = 8
    const val SHIFT_4 = 4
    const val HEX_MASK = 0x0F
    const val ASCII_LIMIT = 128

    // ── Pre-encoded header extraction ────────────────────────────────

    const val HEADER_MIN_SIZE = 3
    const val HEADER_QUOTE_START_OFFSET = 0
    const val HEADER_QUOTE_END_OFFSET_SUB = 2
    const val HEADER_COLON_OFFSET_SUB = 1
    const val SUBSTRING_START_OFFSET = 1
}
