package com.ghost.serialization.sample

import com.ghost.serialization.Ghost
import com.ghost.serialization.ghostAddRegistry
import com.ghost.serialization.generated.GhostModuleRegistry_serialization_sample
import kotlin.js.JsExport
import kotlin.js.JsName

/**
 * JS/Wasm Bridge for the Sample App models.
 */
@OptIn(kotlin.js.ExperimentalJsExport::class)
@JsExport
@JsName("ghostRegisterSampleModels")
fun ghostRegisterSampleModels() {
    ghostAddRegistry(GhostModuleRegistry_serialization_sample.INSTANCE)
}
