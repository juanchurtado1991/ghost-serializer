package com.ghost.serialization.integration.model

import com.ghost.serialization.annotations.GhostFlatten
import com.ghost.serialization.annotations.GhostIgnore
import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import com.ghost.serialization.annotations.GhostWrap
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class TwitterSpecialTweet(
    val id: Long,

    // 1. Flattening: extracts user screen_name directly from nested user object
    @GhostFlatten("user.screen_name")
    val screenName: String,

    // 2. Flattening 2 levels: extracts metadata result_type
    @GhostFlatten("metadata.result_type")
    val resultType: String,

    // 3. Regular text field (no GhostWrap so we can parse from original JSON)
    val text: String,

    // 4. Ignored field: ignored during serialization
    @GhostIgnore
    val source: String = ""
)
