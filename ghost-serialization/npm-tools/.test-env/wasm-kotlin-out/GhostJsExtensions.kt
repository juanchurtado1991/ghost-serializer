package com.ghost.serialization.standalone

import com.ghost.serialization.*
import com.ghost.serialization.InternalGhostApi
import kotlin.js.JsAny

@OptIn(InternalGhostApi::class)

fun Model.toJsAny(): JsAny {
    val obj = createJsObject()
    setJsProperty(obj, "id", intToJs(this.id))
    setJsProperty(obj, "tags", this.tags.toJsAny { stringToJs(it) })
    return obj
}
