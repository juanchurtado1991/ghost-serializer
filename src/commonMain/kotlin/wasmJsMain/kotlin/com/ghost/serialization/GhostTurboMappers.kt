package com.ghost.serialization
import com.ghost.serialization.generated.*

fun <T> ghostToJsArray(list: List<T>, mapper: (T) -> kotlin.js.JsAny): kotlin.js.JsAny {
    val arr = createJsArray()
    list.forEach { pushJsArray(arr, mapper(it)) }
    return arr
}
