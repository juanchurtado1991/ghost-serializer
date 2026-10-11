package com.ghost.serialization.proto.wkt

/**
 * Parses a well-known type's JSON string form with [parse], reporting a malformed value
 * ([IllegalArgumentException] from the codec) through the reader's [throwError], so it surfaces
 * as the channel's own decode exception with the JSON path.
 */
internal inline fun <T> parseWktString(
    text: String,
    throwError: (String) -> Nothing,
    parse: (String) -> T
): T = try {
    parse(text)
} catch (error: IllegalArgumentException) {
    throwError(error.message.orEmpty())
}
