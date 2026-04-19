package com.ghost.integration.model

import com.ghostserializer.annotations.GhostSerialization
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
@GhostSerialization
data class LargeStringData(
    val content: String,
    val emojiStress: String
)
