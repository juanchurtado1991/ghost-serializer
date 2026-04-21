package com.ghost.serialization

import kotlin.js.JsExport
import kotlin.js.JsName
import kotlin.js.ExperimentalJsExport

@OptIn(ExperimentalJsExport::class)
@JsExport
@JsName("ghostRegisterSampleModels")
fun ghostRegisterSampleModels() {
    try {
        // We use full name to avoid unresolved import during KSP generation phase
        Ghost.addRegistry(com.ghost.serialization.benchmark.GhostModuleRegistry_ghost_serialization.INSTANCE)
    } catch (e: Exception) {
        println(">>> [Ghost] JS Registry Error: ${e.message}")
    }
}
