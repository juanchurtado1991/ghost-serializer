package com.ghost.serialization.parser.common.json

import com.ghost.serialization.parser.common.accumulateIntWithOverflowCheck
import com.ghost.serialization.parser.common.accumulateLongWithOverflowCheck
import com.ghost.serialization.parser.common.isDigit
import com.ghost.serialization.parser.common.isExponentMarker
import com.ghost.serialization.parser.common.isNumericSeparator
import com.ghost.serialization.parser.common.constants.GhostJsonErrorMessages as EM
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK
import com.ghost.serialization.parser.common.constants.GhostJsonWriterConstants as WR

/**
 * Integer/Long digit-accumulation kernels shared by flat, streaming, and string JSON readers.
 *
 * Reader-specific state (position, getByte, errors) is supplied via inlined adapters so each
 * call site stays monomorphic after inlining.
 *
 * Integer types follow kotlinx.serialization: an exponent is applied exactly (`1e2` is 100,
 * `100e-2` is 1), while a fraction (`1.5`, `1.0`), a non-integral result (`1e-2`) or a value out
 * of range (`1e10` for [Int]) throws instead of being truncated or saturated. That exact path is
 * [parseIntegralMagnitudeCore], entered only from the cold separator/overflow branches so the plain
 * digit run stays unchanged.
 */

/**
 * Accumulates an [Int] from a digit run with overflow checks; a fractional/exponent separator
 * or an overflow re-reads the literal through [parseIntegralLiteralAsIntCore]. Digit walk lives
 * in this core (same shape as `skipNumberBodyCore`) to stay monomorphic after inlining —
 * avoid nested function-type callbacks that can allocate.
 */
internal inline fun parseIntDigitsCore(
    isNegative: Boolean,
    resetNextTokenByte: () -> Unit,
    getPosition: () -> Int,
    setPosition: (Int) -> Unit,
    limit: Int,
    getByte: (Int) -> Int,
    throwError: (String) -> Nothing,
): Int {
    var accumulatedValue = 0
    var digitCount = 0
    var hasDigitsFound = false
    resetNextTokenByte()
    val startPosition = getPosition()
    var position = startPosition

    while (position < limit) {
        val byte = getByte(position)
        if (isDigit(byteCode = byte)) {
            val digit = byte - TOK.ZERO_INT
            accumulatedValue = if (digitCount < NUM.INT_SAFE_DIGITS) {
                accumulatedValue * WR.BASE_TEN + digit
            } else {
                accumulateIntWithOverflowCheck(
                    current = accumulatedValue,
                    digitValue = digit,
                    isNegative = isNegative
                ) {
                    return parseIntegralLiteralAsIntCore(
                        startPosition = startPosition,
                        isNegative = isNegative,
                        limit = limit,
                        getByte = getByte,
                        setPosition = setPosition,
                        throwError = throwError
                    )
                }
            }
            digitCount++
            hasDigitsFound = true
            position++
        } else {
            setPosition(position)
            if (isNumericSeparator(byteCode = byte)) {
                return parseIntegralLiteralAsIntCore(
                    startPosition = startPosition,
                    isNegative = isNegative,
                    limit = limit,
                    getByte = getByte,
                    setPosition = setPosition,
                    throwError = throwError
                )
            }
            break
        }
    }
    setPosition(position)
    if (!hasDigitsFound) {
        throwError(EM.ERR_EXPECTED_INT_PART)
    }
    return accumulatedValue
}

/**
 * Accumulates a [Long] from a digit run; a fractional/exponent separator re-reads the literal
 * through [parseIntegralLiteralCore].
 */
internal inline fun parseLongDigitsCore(
    isNegative: Boolean,
    resetNextTokenByte: () -> Unit,
    getPosition: () -> Int,
    setPosition: (Int) -> Unit,
    limit: Int,
    getByte: (Int) -> Int,
    throwError: (String) -> Nothing,
): Long {
    var accumulatedValue = 0L
    var digitCount = 0
    var hasDigitsFound = false
    resetNextTokenByte()
    val startPosition = getPosition()
    var position = startPosition

    while (position < limit) {
        val byte = getByte(position)
        if (isDigit(byteCode = byte)) {
            val digit = byte - TOK.ZERO_INT
            accumulatedValue = if (digitCount < NUM.LONG_SAFE_DIGITS) {
                accumulatedValue * WR.BASE_TEN + digit
            } else {
                accumulateLongWithOverflowCheck(
                    current = accumulatedValue,
                    digitValue = digit,
                    isNegative = isNegative
                ) {
                    throwError(EM.ERR_LONG_OVERFLOW)
                }
            }
            digitCount++
            hasDigitsFound = true
            position++
        } else {
            setPosition(position)
            if (isNumericSeparator(byteCode = byte)) {
                return parseIntegralLiteralCore(
                    startPosition = startPosition,
                    isNegative = isNegative,
                    limit = limit,
                    getByte = getByte,
                    setPosition = setPosition,
                    throwError = throwError
                )
            }
            break
        }
    }
    setPosition(position)
    if (!hasDigitsFound) {
        throwError(EM.ERR_EXPECTED_INT_PART)
    }
    return accumulatedValue
}

/**
 * Re-reads the integer literal at [startPosition] (digits only, sign already consumed) as an
 * unsigned magnitude, applying an optional exponent exactly; anything past [ULong.MAX_VALUE]
 * throws [overflowMessage]. The `as*` variants below narrow it to the target type.
 */
internal inline fun parseIntegralMagnitudeCore(
    startPosition: Int,
    limit: Int,
    getByte: (Int) -> Int,
    setPosition: (Int) -> Unit,
    overflowMessage: String,
    throwError: (String) -> Nothing,
): ULong {
    var magnitude = 0uL
    var position = startPosition
    while (position < limit) {
        val byte = getByte(position)
        if (!isDigit(byteCode = byte)) {
            break
        }
        val digit = byte - TOK.ZERO_INT
        val wouldOverflow = magnitude > NUM.ULONG_OVERFLOW_LIMIT ||
            (magnitude == NUM.ULONG_OVERFLOW_LIMIT && digit > NUM.ULONG_MAX_LAST_DIGIT)
        if (wouldOverflow) {
            throwError(overflowMessage)
        }
        magnitude = magnitude * WR.BASE_TEN.toULong() + digit.toULong()
        position++
    }
    if (position == startPosition) {
        setPosition(position)
        throwError(EM.ERR_EXPECTED_INT_PART)
    }
    if (position < limit) {
        val byte = getByte(position)
        if (byte == TOK.DOT_INT) {
            setPosition(position)
            throwError(EM.ERR_FRACTIONAL_INTEGER)
        }
        if (isExponentMarker(markerByte = byte)) {
            val exponent = parseExponentValueCore(
                startPosition = position,
                limit = limit,
                getByte = getByte,
                setPosition = { position = it },
                throwError = throwError
            )
            magnitude = scaleMagnitudeByPowerOfTen(
                magnitude = magnitude,
                exponent = exponent,
                overflowMessage = overflowMessage,
                throwError = throwError
            )
        }
    }
    setPosition(position)
    return magnitude
}

/** [parseIntegralMagnitudeCore] narrowed to an [Int] magnitude (the caller applies the sign). */
internal inline fun parseIntegralLiteralAsIntCore(
    startPosition: Int,
    isNegative: Boolean,
    limit: Int,
    getByte: (Int) -> Int,
    setPosition: (Int) -> Unit,
    throwError: (String) -> Nothing,
): Int {
    val magnitude = parseIntegralMagnitudeCore(
        startPosition = startPosition,
        limit = limit,
        getByte = getByte,
        setPosition = setPosition,
        overflowMessage = EM.ERR_INT_OVERFLOW,
        throwError = throwError
    )
    val maxMagnitude = if (isNegative) NUM.INT_MIN_MAGNITUDE else NUM.INT_MAX_MAGNITUDE
    if (magnitude > maxMagnitude) {
        throwError(EM.ERR_INT_OVERFLOW)
    }
    return magnitude.toInt()
}

/** [parseIntegralMagnitudeCore] narrowed to a [Long] magnitude (the caller applies the sign). */
internal inline fun parseIntegralLiteralCore(
    startPosition: Int,
    isNegative: Boolean,
    limit: Int,
    getByte: (Int) -> Int,
    setPosition: (Int) -> Unit,
    throwError: (String) -> Nothing,
): Long {
    val magnitude = parseIntegralMagnitudeCore(
        startPosition = startPosition,
        limit = limit,
        getByte = getByte,
        setPosition = setPosition,
        overflowMessage = EM.ERR_LONG_OVERFLOW,
        throwError = throwError
    )
    val maxMagnitude = if (isNegative) NUM.LONG_MIN_MAGNITUDE else NUM.LONG_MAX_MAGNITUDE
    if (magnitude > maxMagnitude) {
        throwError(EM.ERR_LONG_OVERFLOW)
    }
    return magnitude.toLong()
}

/**
 * Multiplies or divides [magnitude] by 10^[exponent] exactly. A zero magnitude stays zero for
 * any exponent; overflow throws [overflowMessage] and a dropped nonzero digit throws
 * [EM.ERR_FRACTIONAL_INTEGER]. Both loops end within 20 steps.
 */
internal inline fun scaleMagnitudeByPowerOfTen(
    magnitude: ULong,
    exponent: Int,
    overflowMessage: String,
    throwError: (String) -> Nothing,
): ULong {
    if (magnitude == 0uL) {
        return 0uL
    }
    val base = WR.BASE_TEN.toULong()
    var scaled = magnitude
    var remainingExponent = exponent
    while (remainingExponent > 0) {
        if (scaled > ULong.MAX_VALUE / base) {
            throwError(overflowMessage)
        }
        scaled *= base
        remainingExponent--
    }
    while (remainingExponent < 0) {
        if (scaled % base != 0uL) {
            throwError(EM.ERR_FRACTIONAL_INTEGER)
        }
        scaled /= base
        remainingExponent++
    }
    return scaled
}
