package compat

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class Device(val id: Long, val name: String, val tags: List<String> = emptyList(), val online: Boolean = false)
