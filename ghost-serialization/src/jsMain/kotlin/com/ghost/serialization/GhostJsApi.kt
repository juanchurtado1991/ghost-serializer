package com.ghost.serialization

import com.ghost.serialization.core.contract.GhostRegistry
import com.ghost.serialization.core.parser.GhostJsonReader
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * JS/Wasm Bridge for Ghost Serialization.
 * Provides a high-performance entry point for JavaScript.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("ghostPrewarm")
fun ghostPrewarm() {
    Ghost.prewarm()
}

@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("ghostSerialize")
fun ghostSerialize(value: Any): String {
    return Ghost.serialize(value)
}

/**
 * Since KClass is not available in JS, we use this bridge.
 * The actual implementation will need to be linked to a registry that knows 
 * how to map "CharacterResponse" to its serializer.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("ghostDeserialize")
fun ghostDeserialize(json: String, typeName: String): Any? {
    return try {
        val serializer = Ghost.getSerializerByName(typeName) ?: run {
            println(">>> [Ghost] Serializer not found for type: $typeName")
            return null
        }
        val reader = GhostJsonReader(json.encodeToByteArray())
        serializer.deserialize(reader)
    } catch (e: Exception) {
        println(">>> [Ghost] Critical Deserialization Error ($typeName): ${e.message}")
        null
    }
}
