package com.ghost.serialization

import com.ghost.serialization.InternalGhostApi

/**
 * Converts a UTF-8 byte range to a [String].
 *
 * Wasm uses the browser [TextDecoder] (JavaScriptCore’s Kotlin `ByteArray.decodeToString`
 * / `CharArray.concatToString` path is catastrophically slow for ~20KB JSON — see #16).
 */
@InternalGhostApi
internal expect fun ghostUtf8BytesToString(bytes: ByteArray, offset: Int, length: Int): String
