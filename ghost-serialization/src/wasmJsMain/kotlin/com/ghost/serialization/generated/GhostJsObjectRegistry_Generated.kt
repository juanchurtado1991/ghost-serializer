package com.ghost.serialization.generated

import com.ghost.serialization.GhostJsObjectRegistry
import kotlin.js.JsAny

object GhostJsRegistryInitializer {
    fun register() {
        GhostJsObjectRegistry.register("GhostCharacterOrigin", { obj -> (obj as GhostCharacterOrigin).toJsAny() })
        GhostJsObjectRegistry.register("GhostCharacter", { obj -> (obj as GhostCharacter).toJsAny() })
        GhostJsObjectRegistry.register("CharacterResponseInfo", { obj -> (obj as CharacterResponseInfo).toJsAny() })
        GhostJsObjectRegistry.register("CharacterResponse", { obj -> (obj as CharacterResponse).toJsAny() })
    }
}
