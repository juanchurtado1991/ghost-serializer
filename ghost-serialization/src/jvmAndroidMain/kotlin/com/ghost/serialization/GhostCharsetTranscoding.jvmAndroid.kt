package com.ghost.serialization

import java.nio.charset.Charset

/**
 * Lets the HTTP integrations honor a declared `charset=` on a body: the parsers read UTF-8, so a body
 * declared in any other charset (`ISO-8859-1`, `UTF-16`, ...) is re-encoded to UTF-8 first. `US-ASCII` is a
 * strict subset of UTF-8 and needs no work.
 */
@InternalGhostApi
fun ghostInternalNeedsTranscoding(
    charset: Charset?
): Boolean = charset != null && charset != Charsets.UTF_8 && charset != Charsets.US_ASCII

/** Re-encodes the first [length] bytes of [bytes], declared in [charset], as UTF-8. */
@InternalGhostApi
fun ghostInternalTranscodeToUtf8(
    bytes: ByteArray,
    length: Int,
    charset: Charset
): ByteArray = String(bytes, 0, length, charset).encodeToByteArray()
