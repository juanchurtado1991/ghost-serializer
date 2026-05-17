package com.ghost.serialization.sample.model

import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@GhostSerialization
@Serializable
data class PageInfo(
    val count: Int,
    val pages: Int,
    val next: String? = null,
    val prev: String? = null
)
