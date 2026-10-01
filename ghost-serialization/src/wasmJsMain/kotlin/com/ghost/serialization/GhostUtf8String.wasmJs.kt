package com.ghost.serialization

import kotlin.js.JsAny

/**
 * Browser `TextDecoder` — avoids Kotlin/Wasm `ByteArray.decodeToString`, which is far slower
 * than JSC’s native UTF-8 decoder for playground-sized JSON (#16 encode cliff).
 *
 * Uses [JsAny] for `Uint8Array` so this module does not need the kotlinx-browser / WebGL
 * typed-array dependency.
 */
@OptIn(ExperimentalWasmJsInterop::class)
@InternalGhostApi
internal actual fun ghostUtf8BytesToString(
    bytes: ByteArray,
    offset: Int,
    length: Int
): String {
    if (length <= 0) return ""
    val view = acquireUtf8View(length = length)
    var index = 0
    while (index < length) {
        u8Set(
            arr = view,
            index = index,
            value = bytes[offset + index].toInt() and 0xff
        )
        index++
    }
    return textDecodeUtf8(bytes = view, length = length)
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
    val min = length.coerceAtLeast(minimumValue = 4096)
    val size = if (cachedUtf8ViewLength > 0) {
        maxOf(a = cachedUtf8ViewLength * 2, b = min)
    } else min
    val grown = newUint8Array(size = size)
    cachedUtf8View = grown
    cachedUtf8ViewLength = size
    return grown
}

@OptIn(ExperimentalWasmJsInterop::class)
private fun newUint8Array(
    size: Int
): JsAny = js(code = "new Uint8Array(size)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun textDecodeUtf8(
    bytes: JsAny,
    length: Int
): String = js(code = "new TextDecoder('utf-8').decode(bytes.length === length ? bytes : bytes.subarray(0, length))")

@OptIn(ExperimentalWasmJsInterop::class)
private fun u8Set(
    arr: JsAny,
    index: Int,
    value: Int
) {
    js(code = "arr[index] = value")
}
