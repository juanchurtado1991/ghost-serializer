package com.ghost.serialization

import com.ghost.serialization.benchmark.GhostModuleRegistry_ghost_serialization
import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.ExperimentalJsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("ghostRegisterSampleModels")
fun ghostRegisterSampleModels() {
    try {
        Ghost.addRegistry(GhostModuleRegistry_ghost_serialization.INSTANCE)
    } catch (e: Exception) {
        println(">>> [Ghost] JS Registry Error: ${e.message}")
    }
}
