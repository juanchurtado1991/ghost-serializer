package com.ghost.serialization

import kotlin.js.JsAny

internal fun createJsObject(): JsAny = js("({})")

internal fun createJsArray(): JsAny = js("[]")

internal fun setJsProperty(obj: JsAny, key: String, value: JsAny?) {
    js("obj[key] = value")
}

internal fun pushJsArray(arr: JsAny, value: JsAny?) {
    js("arr.push(value)")
}

internal fun intToJs(v: Int): JsAny = js("v")

internal fun doubleToJs(v: Double): JsAny = js("v")

internal fun boolToJs(v: Boolean): JsAny = js("v")

internal fun stringToJs(v: String): JsAny = js("v")

@JsFun("(arr) => arr.length")
external fun getJsArrayLength(arr: JsAny): Int

@JsFun("(arr, index) => arr[index]")
external fun getJsArrayByte(arr: JsAny, index: Int): Byte

internal fun jsToByteArray(jsArray: JsAny): ByteArray {
    val length = getJsArrayLength(jsArray)
    val result = ByteArray(length)
    for (i in 0 until length) {
        result[i] = getJsArrayByte(jsArray, i)
    }
    return result
}
