package com.ghost.serialization

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.JsAny

/**
 * JS/Wasm Bridge for Ghost Serialization.
 * Provides a high-performance entry point for JavaScript.
 */
@OptIn(kotlin.js.ExperimentalJsExport::class)
@JsExport
@JsName("ghostAddRegistry")
fun ghostAddRegistry(registry: com.ghost.serialization.core.contract.GhostRegistry) {
    Ghost.addRegistry(registry)
}

@OptIn(kotlin.js.ExperimentalJsExport::class)
@JsExport
@JsName("ghostPrewarm")
fun ghostPrewarm() {
    Ghost.prewarm()
}

@OptIn(kotlin.js.ExperimentalJsExport::class)
@JsExport
@JsName("ghostSerialize")
fun ghostSerialize(value: String): String {
    // In Wasm, we keep it simple for now
    return Ghost.serialize(value)
}

/**
 * Since KClass is not available in JS, we use this bridge.
 * To support Wasm, we return a String (JSON) that can be parsed in JS.
 * This ensures the export is visible and functional.
 */
@OptIn(kotlin.js.ExperimentalJsExport::class)
@JsExport
@JsName("ghostDeserialize")
fun ghostDeserialize(json: String, typeName: String): String? {
    return try {
        val serializer = Ghost.getSerializerByName(typeName) ?: run {
            println(">>> [Ghost] Serializer not found for type: $typeName")
            return null
        }
        val reader = com.ghost.serialization.core.parser.GhostJsonReader(json.encodeToByteArray())
        val result = serializer.deserialize(reader)
        
        // For Wasm interop, we return as JSON string for now
        // This is a bridge until we implement direct JS-Object writing
        Ghost.serialize(result!!)
    } catch (e: Exception) {
        println(">>> [Ghost] Critical Deserialization Error ($typeName): ${e.message}")
        null
    }
}
