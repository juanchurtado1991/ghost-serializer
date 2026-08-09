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

@JsonClass(generateAdapter = true)
@Serializable
@GhostSerialization
data class TweetMetadata(
    @Json(name = "result_type")
    @SerialName("result_type") @GhostName("result_type") val resultType: String,
    @Json(name = "iso_language_code")
    @SerialName("iso_language_code") @GhostName("iso_language_code") val isoLanguageCode: String
)
