package com.ghost.serialization.types

/**
 * Scanners for [RawJson] JSON value classification and scalar coercion.
 *
 * Operates directly on [RawJson.storage], [RawJson.storageOffset], and [RawJson.storageLength]
 * without materializing [RawJson.bytes] unless a [String] result is required.
 */
internal object RawJsonValueScanner {

    private const val QUOTE = '"'.code
    private const val OPEN_OBJ = '{'.code
    private const val OPEN_ARR = '['.code
    private const val MINUS = '-'.code
    private const val PLUS = '+'.code
    private const val DOT = '.'.code
    private const val ZERO = '0'.code
    private const val NINE = '9'.code
    private const val EXP_LOWER = 'e'.code
    private const val EXP_UPPER = 'E'.code
    private const val BACKSLASH = '\\'.code

    private const val TRUE_LEN = 4
    private const val FALSE_LEN = 5
    private const val NULL_LEN = 4

    private const val NULL_CHAR = 'n'.code
    private const val TRUE_CHAR = 't'.code
    private const val FALSE_CHAR = 'f'.code
    private const val ONE = '1'.code
    private const val CHAR_R = 'r'.code
    private const val CHAR_U = 'u'.code
    private const val CHAR_E = 'e'.code
    private const val CHAR_A = 'a'.code
    private const val CHAR_L = 'l'.code
    private const val CHAR_S = 's'.code

    private const val BYTE_MASK = 0xFF
    private const val HIGH_SURROGATE_START = 0xD800
    private const val HIGH_SURROGATE_END = 0xDBFF
    private const val LOW_SURROGATE_START = 0xDC00
    private const val UNICODE_BASE = 0x10000
    private const val SURROGATE_PAIR_SHIFT = 10
    private const val SURROGATE_PAIR_MASK = 0x3FF
    private const val BMP_LIMIT = 0xFFFF
    private const val UNICODE_REPLACEMENT = 0xFFFD
    private const val UNICODE_HEX_LENGTH = 4
    private const val HEX_SHIFT = 4
    private const val HEX_LETTER_OFFSET = 10

    private const val ESCAPE_B = 'b'.code
    private const val ESCAPE_F = 'f'.code
    private const val ESCAPE_N = 'n'.code
    private const val ESCAPE_R = 'r'.code
    private const val ESCAPE_T = 't'.code
    private const val ESCAPE_U = 'u'.code
    private const val HEX_A_LOWER = 'a'.code
    private const val HEX_F_LOWER = 'f'.code
    private const val HEX_A_UPPER = 'A'.code
    private const val HEX_F_UPPER = 'F'.code

    private const val BS_CHAR = '\b'
    private const val FF_CHAR = '\u000C'
    private const val LF_CHAR = '\n'
    private const val CR_CHAR = '\r'
    private const val TAB_CHAR = '\t'

    fun kind(raw: RawJson): RawJsonKind {
        if (raw.storageLength <= 0) return RawJsonKind.INVALID
        return when (val first = raw.byteAt(0)) {
            OPEN_OBJ -> RawJsonKind.OBJECT
            OPEN_ARR -> RawJsonKind.ARRAY
            QUOTE -> RawJsonKind.STRING
            NULL_CHAR -> if (raw.matchesNullLiteral()) RawJsonKind.NULL else RawJsonKind.INVALID
            TRUE_CHAR -> if (raw.matchesTrueLiteral()) RawJsonKind.BOOLEAN else RawJsonKind.INVALID
            FALSE_CHAR -> if (raw.matchesFalseLiteral()) RawJsonKind.BOOLEAN else RawJsonKind.INVALID
            MINUS, ZERO -> if (raw.isJsonNumberToken()) RawJsonKind.NUMBER else RawJsonKind.INVALID
            in ONE..NINE -> if (raw.isJsonNumberToken()) RawJsonKind.NUMBER else RawJsonKind.INVALID
            else -> RawJsonKind.INVALID
        }
    }

    fun isJsonNull(raw: RawJson): Boolean =
        raw.storageLength == NULL_LEN && raw.matchesNullLiteral()

    fun asBooleanOrNull(raw: RawJson): Boolean? = when {
        raw.storageLength == TRUE_LEN && raw.matchesTrueLiteral() -> true
        raw.storageLength == FALSE_LEN && raw.matchesFalseLiteral() -> false
        else -> null
    }

    fun asIntOrNull(raw: RawJson): Int? {
        val longValue = raw.parseIntegerOrNull() ?: return null
        if (longValue < Int.MIN_VALUE || longValue > Int.MAX_VALUE) return null
        return longValue.toInt()
    }

    fun asLongOrNull(raw: RawJson): Long? = raw.parseIntegerOrNull()

    fun asDoubleOrNull(raw: RawJson): Double? {
        raw.parseIntegerOrNull()?.let { return it.toDouble() }
        if (!raw.isJsonNumberToken()) return null
        return raw.decodeToString().toDoubleOrNull()
    }

    fun asStringOrNull(raw: RawJson): String? {
        if (raw.storageLength < 2 || raw.byteAt(0) != QUOTE) return null
        val contentStart = raw.storageOffset + 1
        val contentEnd = raw.storageOffset + raw.storageLength - 1
        if (contentEnd < contentStart) return ""
        for (index in contentStart until contentEnd) {
            if (raw.storage[index] == BACKSLASH.toByte()) {
                return raw.decodeJsonStringWithEscapes(contentStart, contentEnd)
            }
        }
        return raw.storage.decodeToString(contentStart, contentEnd)
    }

    fun asDisplayString(raw: RawJson): String =
        when (kind(raw)) {
            RawJsonKind.STRING -> asStringOrNull(raw) ?: raw.decodeToString()
            else -> raw.decodeToString()
        }

    private fun RawJson.byteAt(relativeIndex: Int): Int =
        storage[storageOffset + relativeIndex].toInt() and BYTE_MASK

    private fun RawJson.matchesTrueLiteral(): Boolean =
        storageLength == TRUE_LEN &&
                storage[storageOffset].toInt() == TRUE_CHAR &&
                storage[storageOffset + 1].toInt() == CHAR_R &&
                storage[storageOffset + 2].toInt() == CHAR_U &&
                storage[storageOffset + 3].toInt() == CHAR_E

    private fun RawJson.matchesFalseLiteral(): Boolean =
        storageLength == FALSE_LEN &&
                storage[storageOffset].toInt() == FALSE_CHAR &&
                storage[storageOffset + 1].toInt() == CHAR_A &&
                storage[storageOffset + 2].toInt() == CHAR_L &&
                storage[storageOffset + 3].toInt() == CHAR_S &&
                storage[storageOffset + 4].toInt() == CHAR_E

    private fun RawJson.matchesNullLiteral(): Boolean =
        storageLength == NULL_LEN &&
                storage[storageOffset].toInt() == NULL_CHAR &&
                storage[storageOffset + 1].toInt() == CHAR_U &&
                storage[storageOffset + 2].toInt() == CHAR_L &&
                storage[storageOffset + 3].toInt() == CHAR_L

    private fun RawJson.isJsonNumberToken(): Boolean {
        var index = 0
        if (byteAt(index) == MINUS) {
            index++
            if (index >= storageLength) return false
        }
        if (byteAt(index) == ZERO) {
            index++
        } else {
            if (byteAt(index) !in ONE..NINE) return false
            index++
            while (index < storageLength && byteAt(index) in ZERO..NINE) {
                index++
            }
        }
        if (index < storageLength && byteAt(index) == DOT) {
            index++
            if (index >= storageLength || byteAt(index) !in ZERO..NINE) return false
            while (index < storageLength && byteAt(index) in ZERO..NINE) {
                index++
            }
        }
        if (index < storageLength && (byteAt(index) == EXP_LOWER || byteAt(index) == EXP_UPPER)) {
            index++
            if (index < storageLength && (byteAt(index) == MINUS || byteAt(index) == PLUS)) {
                index++
            }
            if (index >= storageLength || byteAt(index) !in ZERO..NINE) return false
            while (index < storageLength && byteAt(index) in ZERO..NINE) {
                index++
            }
        }
        return index == storageLength
    }

    /**
     * Single-pass integer parse: rejects fraction/exponent without rescanning the token.
     */
    private fun RawJson.parseIntegerOrNull(): Long? {
        if (storageLength <= 0) return null
        var index = 0
        var negative = false
        when (byteAt(index)) {
            MINUS -> {
                negative = true
                index++
            }

            ZERO, in ONE..NINE -> Unit
            else -> return null
        }
        if (index >= storageLength) return null

        var value = 0L
        val limit = if (negative) Long.MIN_VALUE else -Long.MAX_VALUE

        if (byteAt(index) == ZERO) {
            index++
            if (index < storageLength) {
                when (byteAt(index)) {
                    DOT, EXP_LOWER, EXP_UPPER -> return null
                    in ZERO..NINE -> return null
                }
            }
            return 0L
        }

        while (index < storageLength) {
            when (val byte = byteAt(index)) {
                in ZERO..NINE -> {
                    val digit = byte - ZERO
                    if (value < limit / 10) return null
                    value *= 10
                    val next = value - digit
                    if (next > value) return null
                    value = next
                    index++
                }

                DOT, EXP_LOWER, EXP_UPPER -> return null
                else -> return null
            }
        }
        // A positive token whose magnitude is exactly -Long.MIN_VALUE accumulates to
        // Long.MIN_VALUE here; negating it would silently wrap back to Long.MIN_VALUE
        // instead of overflowing, so treat it as out of range.
        if (!negative && value == Long.MIN_VALUE) return null
        return if (negative) value else -value
    }

    private fun RawJson.decodeJsonStringWithEscapes(contentStart: Int, contentEnd: Int): String {
        val estimated = contentEnd - contentStart
        val builder = StringBuilder(estimated)
        var index = contentStart
        while (index < contentEnd) {
            val byte = storage[index++].toInt() and BYTE_MASK
            if (byte == BACKSLASH && index < contentEnd) {
                when (val escaped = storage[index++].toInt() and BYTE_MASK) {
                    QUOTE -> builder.append('"')
                    BACKSLASH -> builder.append('\\')
                    ESCAPE_B -> builder.append(BS_CHAR)
                    ESCAPE_F -> builder.append(FF_CHAR)
                    ESCAPE_N -> builder.append(LF_CHAR)
                    ESCAPE_R -> builder.append(CR_CHAR)
                    ESCAPE_T -> builder.append(TAB_CHAR)
                    ESCAPE_U -> {
                        if (index + 3 >= contentEnd) return builder.toString()
                        val hex = readHex4(index)
                        index += UNICODE_HEX_LENGTH
                        if (hex in HIGH_SURROGATE_START..HIGH_SURROGATE_END &&
                            index + 5 < contentEnd &&
                            storage[index] == BACKSLASH.toByte() &&
                            storage[index + 1] == ESCAPE_U.toByte()
                        ) {
                            val lowSurrogate = readHex4(index + 2)
                            index += 6
                            val codePoint = UNICODE_BASE +
                                    ((hex - HIGH_SURROGATE_START) shl SURROGATE_PAIR_SHIFT) +
                                    (lowSurrogate - LOW_SURROGATE_START)
                            appendCodePoint(builder, codePoint)
                        } else {
                            appendCodePoint(builder, hex)
                        }
                    }

                    else -> builder.append(escaped.toChar())
                }
            } else {
                builder.append(byte.toChar())
            }
        }
        return builder.toString()
    }

    private fun appendCodePoint(builder: StringBuilder, codePoint: Int) {
        if (codePoint <= BMP_LIMIT) {
            builder.append(codePoint.toChar())
            return
        }
        val planeOffset = codePoint - UNICODE_BASE
        builder.append((HIGH_SURROGATE_START + (planeOffset shr SURROGATE_PAIR_SHIFT)).toChar())
        builder.append((LOW_SURROGATE_START + (planeOffset and SURROGATE_PAIR_MASK)).toChar())
    }

    private fun RawJson.readHex4(start: Int): Int {
        var value = 0
        var shift = 0
        while (shift < UNICODE_HEX_LENGTH) {
            val nibble = hexValue(storage[start + shift].toInt() and BYTE_MASK)
            if (nibble < 0) return UNICODE_REPLACEMENT
            value = (value shl HEX_SHIFT) or nibble
            shift++
        }
        return value
    }

    private fun hexValue(byte: Int): Int = when (byte) {
        in ZERO..NINE -> byte - ZERO
        in HEX_A_LOWER..HEX_F_LOWER -> byte - HEX_A_LOWER + HEX_LETTER_OFFSET
        in HEX_A_UPPER..HEX_F_UPPER -> byte - HEX_A_UPPER + HEX_LETTER_OFFSET
        else -> -1
    }
}
