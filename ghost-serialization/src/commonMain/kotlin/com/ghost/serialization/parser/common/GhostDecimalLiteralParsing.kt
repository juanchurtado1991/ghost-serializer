package com.ghost.serialization.parser.common

/**
 * Correctly rounded conversion of a decimal number literal (`[-]digits[.digits][e[+-]digits]`)
 * to a floating-point value: the slow path of the number kernels when scaling the parsed
 * mantissa cannot be exact. Kotlin/Wasm's and Kotlin/Native's `String.toDouble()` are not
 * correctly rounded (`"64.90e-5"` and `"2.2250738585072012e-308"` land one ulp off), so the Wasm
 * actual uses the JS engine's `Number()` and the Native actual refines its own approximation with
 * `refineToNearestDouble`; both narrow Floats with `narrowToNearestFloat`.
 */
internal expect fun parseDecimalLiteralToDouble(
    literal: String
): Double

internal expect fun parseDecimalLiteralToFloat(
    literal: String
): Float
