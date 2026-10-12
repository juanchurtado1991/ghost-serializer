@file:Suppress("NOTHING_TO_INLINE")

package com.ghost.serialization.parser.yaml

import com.ghost.serialization.parser.common.parseDecimalLiteralToDouble
import com.ghost.serialization.parser.common.parseDecimalLiteralToFloat
import com.ghost.serialization.yaml.GhostYamlScanConstants as SC
import com.ghost.serialization.yaml.GhostYamlTokens as TOK

/**
 * Scans and parses YAML's numeric scalar forms (decimal, `0x`/`0o`/`0b`, `.inf`/`.nan`). Split
 * out of `GhostYamlScalarDecoding.kt`, whose [interpretScalar] is [tryParseNumber]'s only caller
 * outside this file.
 */

internal fun GhostYamlFlatReader.readNumber(): Any {
    val startPosition = position
    val localLimit = limit
    val localRawData = rawData

    // Negative hex/octal/binary ("-0x10", "-0o17", "-0b101") aren't plain decimal digits, so the
    // digit-only loop below would stop right after the leading "-0". Scan their digit classes
    // explicitly and hand the full token to tryParseNumber, which parses (and negates) these bases.
    var prefixPosition = position
    if (prefixPosition < localLimit && localRawData[prefixPosition] == TOK.DASH_BYTE) {
        prefixPosition++
    }
    if (prefixPosition + 1 < localLimit && localRawData[prefixPosition] == TOK.ZERO_BYTE) {
        val baseByte = localRawData[prefixPosition + 1]
        val isHex = baseByte == TOK.LOWERCASE_X_BYTE || baseByte == TOK.UPPERCASE_X_BYTE
        val isOctal = baseByte == TOK.LOWERCASE_O_BYTE || baseByte == TOK.UPPERCASE_O_BYTE
        val isBinary = baseByte == TOK.LOWERCASE_B_BYTE || baseByte == TOK.UPPERCASE_B_BYTE
        val isNonDecimalRadix = isHex || isOctal || isBinary
        if (isNonDecimalRadix) {
            position = prefixPosition + 2
            while (position < localLimit) {
                val currentByte = localRawData[position]
                val isBaseDigit = when {
                    isHex -> isDigit(currentByte) ||
                            currentByte in TOK.LOWERCASE_A_BYTE..TOK.LOWERCASE_F_BYTE ||
                            currentByte in TOK.UPPERCASE_A_BYTE..TOK.UPPERCASE_F_BYTE

                    isOctal -> currentByte in TOK.ZERO_BYTE..TOK.SEVEN_BYTE
                    else -> currentByte == TOK.ZERO_BYTE || currentByte == TOK.ONE_BYTE
                }
                if (!isBaseDigit) break
                position++
            }
            return tryParseNumber(data = localRawData, start = startPosition, end = position)
                ?: localRawData.decodeToString(startPosition, position)
        }
    }

    while (position < localLimit && isNumberScanByte(byte = localRawData[position])) {
        position++
    }
    return tryParseNumber(data = localRawData, start = startPosition, end = position)
        ?: localRawData.decodeToString(startPosition, position)
}

/** True if [byte] can appear in a number token: a digit, sign, decimal point, or `e`/`E` exponent. */
private inline fun GhostYamlFlatReader.isNumberScanByte(byte: Byte): Boolean =
    isDigit(byte) || byte == TOK.DASH_BYTE || byte == TOK.PLUS_BYTE || byte == TOK.DOT_BYTE ||
        byte == TOK.LOWERCASE_E_BYTE || byte == TOK.UPPERCASE_E_BYTE

/**
 * Attempts to parse [data] between [start] and [end] as a [Long] or [Double].
 *
 * Returns `null` when the slice is not a valid number. Parsing is performed incrementally
 * over bytes; callers must not decode the entire range with [String.toInt] or [String.toDouble].
 */
internal fun GhostYamlFlatReader.tryParseNumber(data: ByteArray, start: Int, end: Int): Any? {
    val length = end - start
    if (length == 0) return null

    var currentPosition = start
    var isNegative = false

    if (data[currentPosition] == TOK.DASH_BYTE) {
        isNegative = true; currentPosition++
    }
    if (currentPosition >= end) return null

    // Check for hex (0x), octal (0o), binary (0b)
    if (end - currentPosition >= 3 && data[currentPosition] == TOK.ZERO_BYTE) {
        val nextByte = data[currentPosition + 1]
        if (nextByte == TOK.LOWERCASE_X_BYTE || nextByte == TOK.UPPERCASE_X_BYTE) {
            var value = 0L
            var index = currentPosition + 2
            while (index < end) {
                val currentByte = data[index]
                val digit = when {
                    isDigit(currentByte) -> (currentByte - TOK.ZERO_BYTE).toLong()
                    currentByte in TOK.LOWERCASE_A_BYTE..TOK.LOWERCASE_F_BYTE ->
                        (currentByte - TOK.LOWERCASE_A_BYTE + SC.HEX_RADIX_10).toLong()
                    currentByte in TOK.UPPERCASE_A_BYTE..TOK.UPPERCASE_F_BYTE ->
                        (currentByte - TOK.UPPERCASE_A_BYTE + SC.HEX_RADIX_10).toLong()
                    else -> return null
                }
                value = (value shl SC.HEX_SHIFT) or digit
                index++
            }
            return if (isNegative) -value else value
        }
        if (nextByte == TOK.LOWERCASE_O_BYTE || nextByte == TOK.UPPERCASE_O_BYTE) {
            var value = 0L
            var index = currentPosition + 2
            while (index < end) {
                val currentByte = data[index]
                if (currentByte < TOK.ZERO_BYTE || currentByte > TOK.SEVEN_BYTE) return null
                val digit = (currentByte - TOK.ZERO_BYTE).toLong()
                value = (value shl SC.OCTAL_SHIFT) or digit
                index++
            }
            return if (isNegative) -value else value
        }
        if (nextByte == TOK.LOWERCASE_B_BYTE || nextByte == TOK.UPPERCASE_B_BYTE) {
            var value = 0L
            var index = currentPosition + 2
            while (index < end) {
                val currentByte = data[index]
                if (currentByte != TOK.ZERO_BYTE && currentByte != TOK.ONE_BYTE) return null
                val digit = (currentByte - TOK.ZERO_BYTE).toLong()
                value = (value shl SC.BINARY_SHIFT) or digit
                index++
            }
            return if (isNegative) -value else value
        }
    }

    // Check for .inf / .nan
    if (data[currentPosition] == TOK.DOT_BYTE) {
        val stringRepresentation = data.decodeToString(start, end)
        return when (stringRepresentation.lowercase()) {
            TOK.STR_DOT_INF, TOK.STR_PLUS_DOT_INF -> Double.POSITIVE_INFINITY
            TOK.STR_MINUS_DOT_INF -> Double.NEGATIVE_INFINITY
            TOK.STR_DOT_NAN -> Double.NaN
            else -> null
        }
    }

    // Parse integer part byte by byte
    var accumulatedLongValue = 0L
    var hasDigit = false
    var isFloatingPoint = false

    while (currentPosition < end) {
        val currentByte = data[currentPosition]
        when {
            isDigit(currentByte) -> {
                hasDigit = true
                val digit = (currentByte - TOK.ZERO_BYTE).toLong()
                // Overflow check
                if (accumulatedLongValue > (Long.MAX_VALUE - digit) / SC.DECIMAL_RADIX) {
                    isFloatingPoint = true
                    break
                }
                accumulatedLongValue = accumulatedLongValue * SC.DECIMAL_RADIX + digit
                currentPosition++
            }

            currentByte == TOK.DOT_BYTE || currentByte == TOK.LOWERCASE_E_BYTE || currentByte == TOK.UPPERCASE_E_BYTE -> {
                isFloatingPoint = true
                break
            }

            else -> return null
        }
    }

    if (!hasDigit) return null

    if (!isFloatingPoint && currentPosition == end) {
        return if (isNegative) -accumulatedLongValue else accumulatedLongValue
    }

    val stringRepresentation = data.decodeToString(start, end)
    val approximateDouble = stringRepresentation.toDoubleOrNull() ?: return null
    val parsedDouble = if (isPlainDecimalLiteral(text = stringRepresentation)) {
        parseDecimalLiteralToDouble(literal = stringRepresentation)
    } else {
        approximateDouble
    }
    return if (losesFloatPrecisionViaDouble(text = stringRepresentation, parsed = parsedDouble)) {
        stringRepresentation
    } else {
        parsedDouble
    }
}

/**
 * True when narrowing [parsed] to `Float` rounds differently from parsing [text] as a `Float` (the double
 * rounding case: [parsed] lands exactly on a float midpoint). The tree keeps the text then, so `nextFloat`
 * parses it once; `nextDouble` still reads the same value from it. Only midpoints are checked, so ordinary
 * floats pay no second parse.
 */
private fun losesFloatPrecisionViaDouble(
    text: String,
    parsed: Double
): Boolean {
    val narrowed = parsed.toFloat()
    if (!narrowed.isFinite()) return false
    val neighbour = adjacentFloatToward(from = narrowed, target = parsed)
    val isFloatMidpoint = (narrowed.toDouble() + neighbour.toDouble()) / 2.0 == parsed
    return isFloatMidpoint && parseDecimalLiteralToFloat(literal = text) != narrowed
}

/** The float next to [from] on the side of [target]; `nextUp`/`nextDown` are not available in common Kotlin. */
private fun adjacentFloatToward(
    from: Float,
    target: Double
): Float {
    val towardPositive = target > from.toDouble()
    if (from == 0f) {
        val smallest = Float.fromBits(1)
        return if (towardPositive) smallest else -smallest
    }
    val growsMagnitude = (from > 0f) == towardPositive
    val bits = from.toRawBits()
    return Float.fromBits(if (growsMagnitude) bits + 1 else bits - 1)
}

/**
 * A `Float` parsed once from [text]. `toFloatOrNull` rounds twice on Kotlin/Wasm and Native, so plain decimal
 * literals go through the exact cross-platform parse; anything else (`NaN`, hex, suffixes) keeps the platform one.
 */
internal fun exactFloatOrNull(
    text: String
): Float? {
    val approximate = text.toFloatOrNull() ?: return null
    return if (isPlainDecimalLiteral(text = text)) parseDecimalLiteralToFloat(literal = text) else approximate
}

/** True for `-?digits(.digits)?([eE][+-]?digits)?`, the only shape the exact literal parse accepts. */
private fun isPlainDecimalLiteral(
    text: String
): Boolean {
    var index = if (text.startsWith(prefix = "-")) 1 else 0
    fun skipDigits(): Boolean {
        val start = index
        while (index < text.length && text[index] in '0'..'9') index++
        return index > start
    }
    if (!skipDigits()) return false
    if (index < text.length && text[index] == '.') {
        index++
        if (!skipDigits()) return false
    }
    if (index < text.length && (text[index] == 'e' || text[index] == 'E')) {
        index++
        if (index < text.length && (text[index] == '+' || text[index] == '-')) index++
        if (!skipDigits()) return false
    }
    return index == text.length
}
