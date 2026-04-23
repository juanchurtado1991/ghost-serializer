package com.ghost.serialization.generated

import com.ghost.serialization.GhostJsObjectRegistry
import kotlin.js.JsAny

object GhostJsRegistryInitializer {
    fun register() {
        GhostJsObjectRegistry.register("BridgeModel") { obj -> (obj as BridgeModel).toJsAny() }
    }
}
