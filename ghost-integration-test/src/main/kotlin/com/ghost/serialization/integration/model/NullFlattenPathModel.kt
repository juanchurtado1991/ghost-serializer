package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostFlatten
import com.ghost.serialization.annotations.GhostSerialization

/**
 * Flattened paths whose intermediate objects may arrive as `null`, like SmartThings' device API sending
 * `"virtual": {"commandMappings": null}`; before 1.3.4 that failed with `Expected '{'`.
 */
@GhostSerialization
data class NullFlattenPathModel(
    val id: Int,
    @GhostFlatten("virtual.name")
    val name: String? = null,
    @GhostFlatten("virtual.driverId")
    val driverId: String = DEFAULT_DRIVER_ID,
    @GhostFlatten("virtual.commandMappings.commands")
    val commands: List<String>? = null
) {
    companion object {
        const val DEFAULT_DRIVER_ID = "none"
    }
}
