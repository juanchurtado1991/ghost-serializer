@file:OptIn(ExperimentalWasmJsInterop::class)

package com.ghost.serialization

import kotlin.js.JsAny
import kotlin.js.toJsString



/** Helper to convert collections to JS arrays with a mapper */
@InternalGhostApi
fun <T> Collection<T>.toJsAny(mapper: (T) -> JsAny?): JsAny {
    val arr = createJsArray()
    this.forEach { pushJsArray(arr, mapper(it)) }
    return arr
}

@InternalGhostApi
fun <T> Array<T>.toJsAny(mapper: (T) -> JsAny?): JsAny {
    val arr = createJsArray()
    this.forEach { pushJsArray(arr, mapper(it)) }
    return arr
}

@JsFun("() => ({})")
private external fun createJsObjectRaw(): JsAny

@InternalGhostApi
fun createJsObject(): JsAny = createJsObjectRaw()

@JsFun("() => []")
private external fun createJsArrayRaw(): JsAny
@InternalGhostApi
fun createJsArray(): JsAny = createJsArrayRaw()

@InternalGhostApi
fun setJsProperty(obj: JsAny, key: String, value: JsAny?) {
    setJsPropertyRaw(obj, key.toJsString(), value)
}

@JsFun("(obj, key, value) => { obj[key] = value; }")
private external fun setJsPropertyRaw(obj: JsAny, key: JsAny, value: JsAny?)

@JsFun("(arr, value) => { arr.push(value); }")
private external fun pushJsArrayRaw(arr: JsAny, value: JsAny?)
@InternalGhostApi
fun pushJsArray(arr: JsAny, value: JsAny?) = pushJsArrayRaw(arr, value)

@JsFun("(v) => v")
private external fun intToJsRaw(v: Int): JsAny
@InternalGhostApi
fun intToJs(v: Int): JsAny = intToJsRaw(v)

@JsFun("(v) => v")
private external fun doubleToJsRaw(v: Double): JsAny
@InternalGhostApi
fun doubleToJs(v: Double): JsAny = doubleToJsRaw(v)

@JsFun("(v) => v")
private external fun boolToJsRaw(v: Boolean): JsAny
@InternalGhostApi
fun boolToJs(v: Boolean): JsAny = boolToJsRaw(v)

@InternalGhostApi
fun stringToJs(v: String): JsAny = v.toJsString()

@JsFun("(arr) => arr.length")
private external fun getJsArrayLengthRaw(arr: JsAny): Int
@InternalGhostApi
fun getJsArrayLength(arr: JsAny): Int = getJsArrayLengthRaw(arr)

@JsFun("(arr, index) => arr[index]")
private external fun getJsArrayByteRaw(arr: JsAny, index: Int): Byte
@InternalGhostApi
fun getJsArrayByte(arr: JsAny, index: Int): Byte = getJsArrayByteRaw(arr, index)

// ---------------------------------------------------------------------------
// Int32 Chunking Bridge (4x fewer FFI crossings)
// ---------------------------------------------------------------------------

/**
 * Creates an Int32Array view over the same buffer as a Uint8Array.
 * Includes alignment safety: if byteOffset is not a multiple of 4,
 * copies to an aligned buffer first to avoid RangeError.
 */
@JsFun("(arr) => { if (arr.byteOffset % 4 !== 0) return new Int32Array(arr.slice().buffer); return new Int32Array(arr.buffer, arr.byteOffset, arr.byteLength >> 2); }")
private external fun jsArrayToInt32ViewRaw(arr: JsAny): JsAny

/** Read a single 32-bit integer from an Int32Array */
@JsFun("(arr, index) => arr[index]")
private external fun getJsInt32Raw(arr: JsAny, index: Int): Int

/**
 * High-performance byte array transfer from JS to Kotlin/WasmGC.
 *
 * Instead of crossing the FFI boundary once per byte (15,000 crossings
 * for a typical API response), reads 4 bytes at a time through an
 * Int32Array view, reducing crossings by 75%.
 *
 * Assumes Little-Endian byte order (standard on all modern web platforms:
 * x86, ARM, Apple Silicon).
 */
@InternalGhostApi
fun jsToByteArray(jsArray: JsAny): ByteArray {
    val length = getJsArrayLength(jsArray)
    val result = ByteArray(length)

    // Fast path: read 4 bytes per FFI crossing via Int32Array view
    val int32View = jsArrayToInt32ViewRaw(jsArray)
    val intsCount = length shr 2 // length / 4
    var offset = 0

    for (i in 0 until intsCount) {
        val chunk = getJsInt32Raw(int32View, i)
        // Unpack 4 bytes (Little-Endian). The `and 0xFF` mask handles
        // JS sign extension safely — no data loss even with high-bit bytes.
        result[offset++] = (chunk and 0xFF).toByte()
        result[offset++] = ((chunk shr 8) and 0xFF).toByte()
        result[offset++] = ((chunk shr 16) and 0xFF).toByte()
        result[offset++] = ((chunk shr 24) and 0xFF).toByte()
    }

    // Tail: copy remaining 0-3 bytes individually
    for (i in offset until length) {
        result[i] = getJsArrayByte(jsArray, i)
    }

    return result
}

// ---------------------------------------------------------------------------
// Zero-Copy TextDecoder (Bypass Kotlin string conversion)
// ---------------------------------------------------------------------------

/** Global TextDecoder instance (reused across all calls) */
@JsFun("() => new TextDecoder('utf-8')")
private external fun createTextDecoderRaw(): JsAny
@InternalGhostApi
fun createTextDecoder(): JsAny = createTextDecoderRaw()

/**
 * Decodes a UTF-8 string directly from a JS byte buffer without ever
 * creating a Kotlin String. V8's native C++ TextDecoder handles the
 * decoding at maximum speed.
 */
@JsFun("(decoder, arr, start, length) => decoder.decode(arr.subarray(start, start + length))")
private external fun decodeJsStringDirectRaw(decoder: JsAny, arr: JsAny, start: Int, length: Int): JsAny
@InternalGhostApi
fun decodeJsStringDirect(decoder: JsAny, arr: JsAny, start: Int, length: Int): JsAny = 
    decodeJsStringDirectRaw(decoder, arr, start, length)

/**
 * Converts a Kotlin ByteArray to a JS Uint8Array for use with TextDecoder.
 */
@JsFun("(arr) => new Uint8Array(arr)")
private external fun byteArrayToJsUint8ArrayRaw(arr: JsAny): JsAny
@InternalGhostApi
fun byteArrayToJsUint8Array(arr: JsAny): JsAny = byteArrayToJsUint8ArrayRaw(arr)

// ---------------------------------------------------------------------------
// JS String Interning (Cross-Reference Cache)
// ---------------------------------------------------------------------------

@OptIn(InternalGhostApi::class)
internal object GhostJsStringIntern {
    private val cache = HashMap<Int, JsAny>(64)
    private var decoder: JsAny? = null

    private fun getDecoder(): JsAny {
        return decoder ?: createTextDecoder().also { decoder = it }
    }

    fun getOrDecode(jsBuffer: JsAny, start: Int, length: Int, hash: Int): JsAny {
        val cached = cache[hash]
        if (cached != null) return cached

        val decoded = decodeJsStringDirect(getDecoder(), jsBuffer, start, length)
        cache[hash] = decoded
        return decoded
    }

    fun decodeDirect(jsBuffer: JsAny, start: Int, length: Int): JsAny {
        return decodeJsStringDirect(getDecoder(), jsBuffer, start, length)
    }

    fun clear() {
        cache.clear()
    }
}
