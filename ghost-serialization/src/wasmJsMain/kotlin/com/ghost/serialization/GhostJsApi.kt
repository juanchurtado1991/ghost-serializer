package com.ghost.serialization

import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * JS/Wasm Bridge for Ghost Serialization.
 * Provides a high-performance entry point for JavaScript.
 */
// @JsExport
// @JsName("ghostAddRegistry")
fun ghostAddRegistry(registry: com.ghost.serialization.core.contract.GhostRegistry) {
    Ghost.addRegistry(registry)
}

@OptIn(kotlin.js.ExperimentalJsExport::class)
@JsExport
@JsName("ghostPrewarm")
fun ghostPrewarm() {
    Ghost.prewarm()
}

// @JsExport
// @JsName("ghostSerialize")
fun ghostSerialize(value: Any): String {
    // Note: In Wasm, Any might need specific handling depending on the registry
    return Ghost.serialize(value)
}

/**
 * Since KClass is not available in JS, we use this bridge.
 * The actual implementation will need to be linked to a registry that knows 
 * how to map "CharacterResponse" to its serializer.
 */
/**
 * Wasm JS Bridge.
 * NOTE: Kotlin/Wasm has strict rules for @JsExport. Complex Kotlin objects 
 * cannot be exported directly as Any. 
 * For now, this bridge is provided for internal Wasm use. 
 * Use the JS(IR) target for full Next.js/TS interop.
 */
// @JsExport
// @JsName("ghostDeserialize")
fun ghostDeserialize(json: String, typeName: String): Any? {
    return try {
        val serializer = Ghost.getSerializerByName(typeName) ?: run {
            println(">>> [Ghost] Serializer not found for type: $typeName")
            return null
        }
        val reader = com.ghost.serialization.core.parser.GhostJsonReader(json.encodeToByteArray())
        serializer.deserialize(reader)
    } catch (e: Exception) {
        println(">>> [Ghost] Critical Deserialization Error ($typeName): ${e.message}")
        null
    }
}
