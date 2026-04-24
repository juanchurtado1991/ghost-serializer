package com.ghost.serialization.generated

import com.ghost.serialization.*
import kotlin.js.JsAny

@OptIn(InternalGhostApi::class)
fun PageInfo.toJsAny(): JsAny {
    val obj = createJsObject()
    setJsProperty(obj, "count", intToJs(this.count))
    setJsProperty(obj, "pages", intToJs(this.pages))
    setJsProperty(obj, "next", this.next?.let { stringToJs(it) })
    setJsProperty(obj, "prev", this.prev?.let { stringToJs(it) })
    return obj
}

@OptIn(InternalGhostApi::class)
fun LocationRef.toJsAny(): JsAny {
    val obj = createJsObject()
    setJsProperty(obj, "name", stringToJs(this.name))
    setJsProperty(obj, "url", stringToJs(this.url))
    return obj
}

@OptIn(InternalGhostApi::class)
fun GhostCharacter.toJsAny(): JsAny {
    val obj = createJsObject()
    setJsProperty(obj, "id", intToJs(this.id))
    setJsProperty(obj, "name", stringToJs(this.name))
    setJsProperty(obj, "status", stringToJs(this.status.name))
    setJsProperty(obj, "species", stringToJs(this.species))
    setJsProperty(obj, "type", stringToJs(this.type))
    setJsProperty(obj, "gender", stringToJs(this.gender))
    setJsProperty(obj, "origin", this.origin.toJsAny())
    setJsProperty(obj, "location", this.location.toJsAny())
    setJsProperty(obj, "image", stringToJs(this.image))
    setJsProperty(obj, "episode", this.episode.toJsAny { stringToJs(it) })
    setJsProperty(obj, "url", stringToJs(this.url))
    setJsProperty(obj, "created", stringToJs(this.created))
    return obj
}

@OptIn(InternalGhostApi::class)
fun CharacterResponse.toJsAny(): JsAny {
    val obj = createJsObject()
    setJsProperty(obj, "info", this.info.toJsAny())
    setJsProperty(obj, "results", this.results.toJsAny { (it as GhostCharacter).toJsAny() })
    return obj
}
