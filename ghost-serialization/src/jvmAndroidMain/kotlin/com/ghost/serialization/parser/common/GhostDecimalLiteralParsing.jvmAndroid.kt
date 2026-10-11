package com.ghost.serialization.parser.common

internal actual fun parseDecimalLiteralToDouble(
    literal: String
): Double = literal.toDouble()

internal actual fun parseDecimalLiteralToFloat(
    literal: String
): Float = literal.toFloat()
