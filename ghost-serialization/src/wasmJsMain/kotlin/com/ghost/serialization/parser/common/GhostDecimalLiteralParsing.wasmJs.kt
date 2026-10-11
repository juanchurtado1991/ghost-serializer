package com.ghost.serialization.parser.common

import com.ghost.serialization.parser.common.json.isAmbiguousFloatNarrowing
import com.ghost.serialization.parser.common.constants.GhostJsonTokens as TOK

private const val FLOAT_OVERFLOW_THRESHOLD = 3.4028235677973366E38

/**
 * JS `Number()` is correctly rounded to Double. A Float is that Double narrowed, which rounds
 * twice only when the Double lands exactly halfway between two Floats (or on the overflow
 * threshold halfway between [Float.MAX_VALUE] and 2^128); that tie is broken by
 * `compareDecimalLiteralToDouble`, the exact sign of `literal - double` in BigInt arithmetic.
 */
internal actual fun parseDecimalLiteralToDouble(
    literal: String
): Double = jsNumber(literal = literal)

internal actual fun parseDecimalLiteralToFloat(
    literal: String
): Float {
    val isNegative = literal.firstOrNull()?.code == TOK.MINUS_INT
    val magnitudeLiteral = if (isNegative) literal.substring(startIndex = 1) else literal
    val magnitude = parseFloatMagnitude(magnitudeLiteral = magnitudeLiteral)
    return if (isNegative) -magnitude else magnitude
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun compareDecimalLiteralToDouble(
    magnitudeLiteral: String,
    value: Double
): Int = js(
    code = """{
        var parts = /^\+?([0-9]*)(?:\.([0-9]*))?(?:[eE]([+-]?[0-9]+))?$/.exec(magnitudeLiteral);
        var fraction = parts[2] || '';
        var decimalExponent = (parts[3] ? parseInt(parts[3], 10) : 0) - fraction.length;
        var decimal = BigInt(((parts[1] || '') + fraction) || '0');
        var view = new DataView(new ArrayBuffer(8));
        view.setFloat64(0, value);
        var bits = view.getBigUint64(0);
        var biasedExponent = Number(bits >> 52n);
        var binary = bits & 0xFFFFFFFFFFFFFn;
        if (biasedExponent !== 0) binary |= 1n << 52n;
        var binaryExponent = Math.max(biasedExponent, 1) - 1075;
        if (decimalExponent >= 0) decimal *= 10n ** BigInt(decimalExponent);
        else binary *= 10n ** BigInt(-decimalExponent);
        if (binaryExponent >= 0) binary <<= BigInt(binaryExponent);
        else decimal <<= BigInt(-binaryExponent);
        return decimal < binary ? -1 : (decimal > binary ? 1 : 0);
    }"""
)

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsNumber(
    literal: String
): Double = js(code = "Number(literal)")

private fun parseFloatMagnitude(
    magnitudeLiteral: String
): Float {
    val nearestDouble = jsNumber(literal = magnitudeLiteral)
    val narrowed = nearestDouble.toFloat()
    if (narrowed.isInfinite()) {
        val isBelowOverflowThreshold = nearestDouble == FLOAT_OVERFLOW_THRESHOLD &&
            compareDecimalLiteralToDouble(
                magnitudeLiteral = magnitudeLiteral,
                value = nearestDouble
            ) < 0
        return if (isBelowOverflowThreshold) Float.MAX_VALUE else narrowed
    }
    val isHalfwayBetweenFloats = isAmbiguousFloatNarrowing(
        exact = nearestDouble,
        narrowed = narrowed
    )
    if (!isHalfwayBetweenFloats) {
        return narrowed
    }
    val comparison = compareDecimalLiteralToDouble(
        magnitudeLiteral = magnitudeLiteral,
        value = nearestDouble
    )
    val narrowedIsAbove = narrowed.toDouble() > nearestDouble
    return when {
        comparison > 0 && !narrowedIsAbove -> Float.fromBits(bits = narrowed.toRawBits() + 1)
        comparison < 0 && narrowedIsAbove -> Float.fromBits(bits = narrowed.toRawBits() - 1)
        else -> narrowed
    }
}
