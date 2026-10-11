package compat

import com.ghost.serialization.annotations.GhostFlatten
import com.ghost.serialization.annotations.GhostSerialization

/** Flattened paths whose parent objects may be `null` in the JSON (counted as absent since 1.3.4). */
@GhostSerialization
data class VirtualDevice(
    val id: String,
    @GhostFlatten("virtual.name") val name: String? = null,
    @GhostFlatten("virtual.commandMappings.commands") val commands: List<String>? = null
)
