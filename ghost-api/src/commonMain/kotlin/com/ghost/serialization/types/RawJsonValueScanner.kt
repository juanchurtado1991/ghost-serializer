package com.ghost.serialization.types

/**
 * Zero-allocation scanners for [RawJson] JSON value classification and scalar coercion.
 *
 * Operates directly on [RawJson.storage] / [RawJson.storageOffset] / [RawJson.storageLength]
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

    fun asDisplayString(raw: RawJson): String {
        val classified = kind(raw)
        return when (classified) {
            RawJsonKind.STRING -> asStringOrNull(raw) ?: raw.decodeToString()
            RawJsonKind.NUMBER,
            RawJsonKind.BOOLEAN,
            RawJsonKind.NULL -> raw.decodeToString()
            RawJsonKind.OBJECT,
            RawJsonKind.ARRAY,
            RawJsonKind.INVALID -> raw.decodeToString()
        }
    }

    private fun RawJson.byteAt(relativeIndex: Int): Int =
        storage[storageOffset + relativeIndex].toInt() and 0xFF

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
            return if (negative) 0L else 0L
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
        return if (negative) value else -value
    }

    private fun RawJson.decodeJsonStringWithEscapes(contentStart: Int, contentEnd: Int): String {
        val estimated = contentEnd - contentStart
        val builder = StringBuilder(estimated)
        var index = contentStart
        while (index < contentEnd) {
            val byte = storage[index++].toInt() and 0xFF
            if (byte == BACKSLASH && index < contentEnd) {
                when (val escaped = storage[index++].toInt() and 0xFF) {
                    QUOTE -> builder.append('"')
                    BACKSLASH -> builder.append('\\')
                    'b'.code -> builder.append('\b')
                    'f'.code -> builder.append('\u000C')
                    'n'.code -> builder.append('\n')
                    'r'.code -> builder.append('\r')
                    't'.code -> builder.append('\t')
                    'u'.code -> {
                        if (index + 3 >= contentEnd) return builder.toString()
                        val hex = readHex4(index)
                        index += 4
                        if (hex in 0xD800..0xDBFF && index + 5 < contentEnd &&
                            storage[index] == BACKSLASH.toByte() &&
                            storage[index + 1] == 'u'.code.toByte()
                        ) {
                            val low = readHex4(index + 2)
                            index += 6
                            appendCodePoint(builder, 0x10000 + ((hex - 0xD800) shl 10) + (low - 0xDC00))
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
        if (codePoint <= 0xFFFF) {
            builder.append(codePoint.toChar())
            return
        }
        val offset = codePoint - 0x10000
        builder.append((0xD800 + (offset shr 10)).toChar())
        builder.append((0xDC00 + (offset and 0x3FF)).toChar())
    }

    private fun RawJson.readHex4(start: Int): Int {
        var value = 0
        var shift = 0
        while (shift < 4) {
            val nibble = hexValue(storage[start + shift].toInt() and 0xFF)
            if (nibble < 0) return 0xFFFD
            value = (value shl 4) or nibble
            shift++
        }
        return value
    }

    private fun hexValue(byte: Int): Int = when (byte) {
        in ZERO..NINE -> byte - ZERO
        in 'a'.code..'f'.code -> byte - 'a'.code + 10
        in 'A'.code..'F'.code -> byte - 'A'.code + 10
        else -> -1
    }
}
