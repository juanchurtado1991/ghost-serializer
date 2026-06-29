package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

typealias AttributeMap = Map<String, String>
typealias TagList = List<String>
typealias DeviceId = String

@GhostSerialization
data class TypealiasModel(
    val id: DeviceId,
    val attributes: AttributeMap,
    val tags: TagList
)
