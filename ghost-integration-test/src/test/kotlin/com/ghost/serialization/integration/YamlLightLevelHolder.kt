package com.ghost.serialization.integration

import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostYamlSerialization

@GhostSerialization
@GhostYamlSerialization
data class YamlLightLevelHolder(
    val level: YamlLightLevel,
    val history: List<YamlLightLevel>,
    val byRoom: Map<String, YamlLightLevel>,
    val previous: YamlLightLevel? = null,
)
