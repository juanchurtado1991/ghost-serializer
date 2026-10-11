package com.ghost.serialization.parser.common

/**
 * Correctly rounded conversion of a decimal number literal (`[-]digits[.digits][e[+-]digits]`)
 * to a floating-point value: the slow path of the number kernels when scaling the parsed
 * mantissa cannot be exact. Kotlin/Wasm's `String.toDouble()` is not correctly rounded
 * (`"64.90e-5"` lands one ULP off), so the Wasm actual delegates to the JS engine.
 */
internal expect fun parseDecimalLiteralToDouble(
    literal: String
): Double

internal expect fun parseDecimalLiteralToFloat(
    literal: String
): Float
