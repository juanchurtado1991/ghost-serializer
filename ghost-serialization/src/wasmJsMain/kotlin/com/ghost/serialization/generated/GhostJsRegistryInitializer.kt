package com.ghost.serialization.generated
import kotlin.js.JsAny
import com.ghost.serialization.GhostJsObjectRegistry
import com.ghost.serialization.InternalGhostApi

@OptIn(InternalGhostApi::class)
object GhostJsRegistryInitializer {
    fun register() {
        GhostJsObjectRegistry.register("PageInfo") { (it as PageInfo).toJsAny() }
        GhostJsObjectRegistry.register("LocationRef") { (it as LocationRef).toJsAny() }
        GhostJsObjectRegistry.register("GhostCharacter") { (it as GhostCharacter).toJsAny() }
        GhostJsObjectRegistry.register("CharacterResponse") { (it as CharacterResponse).toJsAny() }
    }

    fun toJsAny(obj: Any): JsAny? = when(obj) {
        is PageInfo -> obj.toJsAny()
        is LocationRef -> obj.toJsAny()
        is GhostCharacter -> obj.toJsAny()
        is CharacterResponse -> obj.toJsAny()
        else -> null
    }
}
