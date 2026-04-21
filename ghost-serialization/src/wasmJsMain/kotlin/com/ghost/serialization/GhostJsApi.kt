package com.ghost.serialization

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.ExperimentalJsExport
import com.ghost.serialization.core.contract.GhostRegistry
/**
 * JS/Wasm Bridge for Ghost Serialization.
 * Provides a high-performance entry point for JavaScript.
 */
internal fun ghostAddRegistry(registry: GhostRegistry) {
    Ghost.addRegistry(registry)
}

@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("ghostPrewarm")
fun ghostPrewarm() {
    // Automated model registration hook
    try {
        com.ghost.serialization.generated.GhostAutoRegistry.registerAll()
    } catch (e: Exception) {
        // Fallback for non-generated environments
    }
    Ghost.prewarm()
}

@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("ghostSerialize")
fun ghostSerialize(value: String): String {
    // Direct serialization to string for WASM-JS interoperability.
    return Ghost.serialize(value)
}

/**
 * Since KClass is not available in JS, we use this bridge.
 * To support Wasm, we return a String (JSON) that can be parsed in JS.
 * This ensures the export is visible and functional.
 */
@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("ghostDeserialize")
fun ghostDeserialize(json: String, typeName: String): String? {
    return try {
        val serializer = Ghost.getSerializerByName(typeName) ?: run {
            println(">>> [Ghost] Serializer not found for type: $typeName")
            return null
        }
        val reader = com.ghost.serialization.core.parser.GhostJsonReader(json.encodeToByteArray())
        val result = serializer.deserialize(reader) ?: return null
        
        // WASM-JS Bridge: Return result as JSON string.
        // Direct JS-Object writing is planned for future iterations.
        Ghost.serialize(result)
    } catch (e: Exception) {
        println(">>> [Ghost] Critical Deserialization Error ($typeName): ${e.message}")
        null
    }
}
