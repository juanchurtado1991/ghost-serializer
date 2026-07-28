package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostYamlSerialization

@GhostSerialization
@GhostYamlSerialization
data class YamlShardCounter(
    val shard_id: ULong,
)
