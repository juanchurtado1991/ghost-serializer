package com.ghost.serialization.generated

import com.ghost.serialization.createJsObject
import com.ghost.serialization.setJsProperty
import com.ghost.serialization.stringToJs
import com.ghost.serialization.intToJs
import com.ghost.serialization.boolToJs
import com.ghost.serialization.createJsArray
import com.ghost.serialization.pushJsArray
import kotlin.js.JsAny

fun BridgeModel.toJsAny(): JsAny {
    val obj = createJsObject()
    setJsProperty(obj, "id", intToJs(this.id))
    setJsProperty(obj, "label", stringToJs(this.label))
    return obj
}

fun <T : Any> List<T>.toJsAny(mapper: (T) -> JsAny?): JsAny {
    val arr = createJsArray()
    forEach { pushJsArray(arr, mapper(it)) }
    return arr
}
