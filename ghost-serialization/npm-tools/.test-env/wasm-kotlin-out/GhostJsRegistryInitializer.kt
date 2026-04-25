package com.ghost.serialization.standalone
import kotlin.js.JsAny
import com.ghost.serialization.GhostJsObjectRegistry
import com.ghost.serialization.InternalGhostApi

@OptIn(InternalGhostApi::class)
object GhostJsRegistryInitializer {
    fun register() {
        GhostJsObjectRegistry.register("Model") { (it as Model).toJsAny() }
    }

    fun toJsAny(obj: Any): JsAny? = when(obj) {
        is Model -> obj.toJsAny()
        else -> null
    }
}
