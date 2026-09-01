package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostFlatten
import com.ghost.serialization.annotations.GhostIgnore
import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class TwitterSpecialTweet(
    val id: Long,

    @GhostFlatten("user.screen_name")
    val screenName: String,

    @GhostFlatten("metadata.result_type")
    val resultType: String,

    // No GhostWrap: stays parseable from the original JSON
    val text: String,

    @GhostIgnore
    val source: String = ""
)
