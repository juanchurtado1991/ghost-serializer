package com.ghost.serialization.generated
import kotlin.js.JsAny

object GhostJsObjectRegistry_Generated {
    fun toJsAny(obj: Any): JsAny? = when(obj) {
        is GhostCharacterOrigin -> obj.toJsAny()
        is GhostCharacter -> obj.toJsAny()
        is CharacterResponseInfo -> obj.toJsAny()
        is CharacterResponse -> obj.toJsAny()
        else -> null
    }
}
