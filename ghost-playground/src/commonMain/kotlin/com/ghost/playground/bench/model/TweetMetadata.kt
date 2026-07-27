package com.ghost.playground.bench.model

import com.ghost.serialization.annotations.GhostName
import com.ghost.serialization.annotations.GhostSerialization
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@GhostSerialization
data class TweetMetadata(
    @SerialName("result_type") @GhostName("result_type") val resultType: String,
    @SerialName("iso_language_code") @GhostName("iso_language_code") val isoLanguageCode: String,
)
