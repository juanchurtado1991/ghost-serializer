package com.ghost.serialization

import kotlin.js.JsAny

/**
 * Registry for direct JS object construction.
 * Populated by ghost-transpiler via GhostJsObjectRegistry generated code.
 */
object GhostJsObjectRegistry {
    private val builders = mutableMapOf<String, (Any) -> JsAny?>()

    fun register(typeName: String, builder: (Any) -> JsAny?) {
        builders[typeName] = builder
    }

    fun build(typeName: String, obj: Any): JsAny? = builders[typeName]?.invoke(obj)
}
