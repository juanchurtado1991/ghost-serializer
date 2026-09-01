package com.ghost.serialization

import kotlin.js.JsAny

/**
 * Browser [TextDecoder] — avoids Kotlin/Wasm `ByteArray.decodeToString`, which is far slower
 * than JSC’s native UTF-8 decoder for playground-sized JSON (#16 encode cliff).
 *
 * Uses [JsAny] for `Uint8Array` so this module does not need the kotlinx-browser / WebGL
 * typed-array dependency.
 */
@OptIn(ExperimentalWasmJsInterop::class)
@InternalGhostApi
internal actual fun ghostUtf8BytesToString(bytes: ByteArray, offset: Int, length: Int): String {
    if (length <= 0) return ""
    val u8 = acquireUtf8View(length)
    var i = 0
    while (i < length) {
        u8Set(u8, i, bytes[offset + i].toInt() and 0xff)
        i++
    }
    return textDecodeUtf8(u8, length)
}

@OptIn(ExperimentalWasmJsInterop::class)
private var cachedUtf8View: JsAny? = null

@OptIn(ExperimentalWasmJsInterop::class)
private var cachedUtf8ViewLength: Int = 0

@OptIn(ExperimentalWasmJsInterop::class)
private fun acquireUtf8View(length: Int): JsAny {
    if (cachedUtf8View != null && cachedUtf8ViewLength >= length) {
        return cachedUtf8View!!
    }
    val min = length.coerceAtLeast(4096)
    val size = if (cachedUtf8ViewLength > 0) maxOf(cachedUtf8ViewLength * 2, min) else min
    val grown = newUint8Array(size)
    cachedUtf8View = grown
    cachedUtf8ViewLength = size
    return grown
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun newUint8Array(size: Int): JsAny = js("new Uint8Array(size)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun textDecodeUtf8(bytes: JsAny, length: Int): String =
    js("new TextDecoder('utf-8').decode(bytes.length === length ? bytes : bytes.subarray(0, length))")

@OptIn(ExperimentalWasmJsInterop::class)
private fun u8Set(arr: JsAny, index: Int, value: Int) {
    js("arr[index] = value")
}
