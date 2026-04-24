package com.ghost.serialization.generated

import com.ghost.serialization.toJsAny
import com.ghost.serialization.setJsProperty
import com.ghost.serialization.createJsObject
import com.ghost.serialization.stringToJs
import com.ghost.serialization.intToJs
import com.ghost.serialization.boolToJs
import com.ghost.serialization.doubleToJs


@JsFun("(p0, p1) => ({ name: p0, url: p1 })")
private external fun createJs_GhostCharacterOrigin_Raw(p0: JsAny?, p1: JsAny?): JsAny

internal fun createJs_GhostCharacterOrigin(p0: JsAny?, p1: JsAny?): JsAny = createJs_GhostCharacterOrigin_Raw(p0, p1)

fun GhostCharacterOrigin.toJsAny(): JsAny {
    return createJs_GhostCharacterOrigin(
        stringToJs(this.name),
        stringToJs(this.url)
    )
}

fun GhostCharacter.toJsAny(): JsAny {
    val obj = createJsObject()
    setJsProperty(obj, "id", intToJs(this.id))
    setJsProperty(obj, "name", stringToJs(this.name))
    setJsProperty(obj, "status", stringToJs(this.status))
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

fun CharacterResponseInfo.toJsAny(): JsAny {
    val obj = createJsObject()
    setJsProperty(obj, "count", intToJs(this.count))
    setJsProperty(obj, "pages", intToJs(this.pages))
    this.next?.let { setJsProperty(obj, "next", stringToJs(it)) } ?: setJsProperty(obj, "next", null)
    this.prev?.let { setJsProperty(obj, "prev", stringToJs(it)) } ?: setJsProperty(obj, "prev", null)
    return obj
}

fun CharacterResponse.toJsAny(): JsAny {
    val obj = createJsObject()
    setJsProperty(obj, "info", this.info.toJsAny())
    setJsProperty(obj, "results", this.results.toJsAny { it.toJsAny() })
    return obj
}
