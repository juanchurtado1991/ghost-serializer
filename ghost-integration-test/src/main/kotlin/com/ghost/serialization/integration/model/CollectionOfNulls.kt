package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostSerialization

@GhostSerialization
data class CollectionOfNulls(
    val items: List<String?>
)