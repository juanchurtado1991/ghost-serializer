package com.ghost.serialization.parser.common

/** JS `Number()` is correctly rounded to Double; a Float is narrowed from it with the exact tie check. */
internal actual fun parseDecimalLiteralToDouble(
    literal: String
): Double = jsNumber(literal = literal)

internal actual fun parseDecimalLiteralToFloat(
    literal: String
): Float = correctlyRoundedFloat(literal = literal) { magnitudeLiteral -> jsNumber(literal = magnitudeLiteral) }

@OptIn(ExperimentalWasmJsInterop::class)
private fun jsNumber(
    literal: String
): Double = js(code = "Number(literal)")
