package com.ghost.serialization.proto

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.proto.GhostProtoConstants as PC

/**
 * proto3 JSON writes non-finite `double`/`float` values as the strings `"NaN"`, `"Infinity"` and
 * `"-Infinity"`, since plain JSON has no literal for them; finite values keep the writer's normal
 * shortest round-trip formatting (a `Float` is formatted as a `Float`, not widened to `Double`).
 * Used by generated `@GhostProtoSerialization` serializers and the `DoubleValue`/`FloatValue`
 * well-known types; YAML already has `.nan`/`.inf`, so the YAML writer just writes the number.
 */
@InternalGhostApi
fun GhostJsonWriter.protoValue(
    number: Double
): GhostJsonWriter {
    val nonFiniteName = protoNonFiniteName(number = number) ?: return value(number = number)
    return value(text = nonFiniteName)
}

@InternalGhostApi
fun GhostJsonWriter.protoValue(
    number: Float
): GhostJsonWriter {
    val nonFiniteName = protoNonFiniteName(number = number.toDouble()) ?: return value(number = number)
    return value(text = nonFiniteName)
}

@InternalGhostApi
fun GhostJsonStringWriter.protoValue(
    number: Double
): GhostJsonStringWriter {
    val nonFiniteName = protoNonFiniteName(number = number) ?: return value(number = number)
    return value(text = nonFiniteName)
}

@InternalGhostApi
fun GhostJsonStringWriter.protoValue(
    number: Float
): GhostJsonStringWriter {
    val nonFiniteName = protoNonFiniteName(number = number.toDouble()) ?: return value(number = number)
    return value(text = nonFiniteName)
}

@InternalGhostApi
fun GhostYamlWriter.protoValue(
    number: Double
): GhostYamlWriter = value(number = number)

@InternalGhostApi
fun GhostYamlWriter.protoValue(
    number: Float
): GhostYamlWriter = value(number = number)

private fun protoNonFiniteName(
    number: Double
): String? = when {
    number.isNaN() -> PC.STR_NAN
    number == Double.POSITIVE_INFINITY -> PC.STR_INFINITY
    number == Double.NEGATIVE_INFINITY -> PC.STR_NEGATIVE_INFINITY
    else -> null
}
