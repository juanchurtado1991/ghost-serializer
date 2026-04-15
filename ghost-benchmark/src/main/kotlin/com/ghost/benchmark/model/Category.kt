package com.ghost.benchmark.model

import com.ghost.serialization.annotations.GhostSerialization
import com.squareup.moshi.JsonClass
import kotlinx.serialization.Serializable

@Serializable
@JsonClass(generateAdapter = true)
@GhostSerialization
data class Category(
    val name: String,
    val subCategories: List<Category>? = null
)
