@file:OptIn(ExperimentalWasmJsInterop::class)

package com.ghost.serialization.core.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.GhostJsStringIntern
import kotlin.js.JsAny

@JsFun("(arr, index) => arr[index]")
private external fun getByteInternal(arr: JsAny, index: Int): Byte

/**
 * High-performance Zero-Copy source for WebAssembly.
 * Uses a JavaScript Uint8Array view to read memory directly.
 */
@InternalGhostApi
class WasmSource(
    private val jsBuffer: JsAny,
    override val size: Int
) : GhostSource {

    override fun get(index: Int): Byte = getByteInternal(jsBuffer, index)

    override fun decodeToString(start: Int, end: Int): String {
        val length = end - start
        if (length <= 0) return ""
        
        // Use the optimized JS TextDecoder via the interop bridge
        return GhostJsStringIntern.decodeDirect(jsBuffer, start, length).toString()
    }
}
