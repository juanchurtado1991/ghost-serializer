package com.ghost.serialization.compiler.codegen

/**
 * Escapes a JSON field name for the precomputed `H_*`/`HS_*` key headers and the warm-up document
 * exactly as the runtime writers escape a string (`writer.name(...)`): `"` and `\`, the short
 * escapes `\b \f \n \r \t`, and `\u00xx` (lowercase hex) for the remaining control characters;
 * everything else is copied verbatim. A name such as `a"b` would otherwise end the JSON string early.
 */
internal object JsonStringEscaping {

    private const val CONTROL_CHAR_LIMIT = 0x20
    private const val HEX_DIGITS = "0123456789abcdef"
    private const val HEX_DIGIT_BITS = 4
    private const val HEX_DIGIT_MASK = 0xF
    private const val UNICODE_ESCAPE_DIGITS = 4
    private const val UNICODE_ESCAPE_PREFIX = "\\u"

    private val SHORT_ESCAPES = mapOf(
        '"' to "\\\"",
        '\\' to "\\\\",
        '\b' to "\\b",
        '\u000C' to "\\f",
        '\n' to "\\n",
        '\r' to "\\r",
        '\t' to "\\t"
    )

    fun escape(
        text: String
    ): String = buildString(capacity = text.length) {
        for (char in text) {
            val shortEscape = SHORT_ESCAPES[char]
            when {
                shortEscape != null -> append(shortEscape)
                char.code < CONTROL_CHAR_LIMIT -> appendUnicodeEscape(char = char)
                else -> append(char)
            }
        }
    }

    private fun StringBuilder.appendUnicodeEscape(char: Char) {
        append(UNICODE_ESCAPE_PREFIX)
        for (digit in UNICODE_ESCAPE_DIGITS - 1 downTo 0) {
            append(HEX_DIGITS[(char.code shr (digit * HEX_DIGIT_BITS)) and HEX_DIGIT_MASK])
        }
    }
}
