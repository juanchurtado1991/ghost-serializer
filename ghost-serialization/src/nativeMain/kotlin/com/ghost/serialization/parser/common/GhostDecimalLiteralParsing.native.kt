package com.ghost.serialization.parser.common

/** Kotlin/Native's `String.toDouble()` is only an approximation; it is refined to the nearest Double. */
internal actual fun parseDecimalLiteralToDouble(
    literal: String
): Double = correctlyRoundedDouble(literal = literal) { magnitudeLiteral ->
    refineToNearestDouble(
        magnitudeLiteral = magnitudeLiteral,
        approximation = magnitudeLiteral.toDouble()
    )
}

internal actual fun parseDecimalLiteralToFloat(
    literal: String
): Float = correctlyRoundedFloat(literal = literal) { magnitudeLiteral ->
    refineToNearestDouble(
        magnitudeLiteral = magnitudeLiteral,
        approximation = magnitudeLiteral.toDouble()
    )
}
