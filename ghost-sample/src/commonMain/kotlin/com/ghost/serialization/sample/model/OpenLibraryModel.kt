package com.ghost.serialization.sample.model

import com.ghost.serialization.annotations.GhostProtoSerialization
import kotlinx.serialization.Serializable

@GhostProtoSerialization
@Serializable
data class OpenLibraryResponse(
    val numFound: Int = 0,
    val start: Int = 0,
    val numFoundExact: Boolean = false,
    val docs: List<OpenLibraryBook> = emptyList(),
    val q: String = ""
)

@GhostProtoSerialization
@Serializable
data class OpenLibraryBook(
    val key: String = "",
    val title: String = "Unknown",
    val title_suggest: String = "",
    val has_fulltext: Boolean = false,
    val edition_count: Int = 0,
    val first_publish_year: Int = 0,
    val cover_i: Int = 0,
    val language: List<String> = emptyList(),
    val author_key: List<String> = emptyList(),
    val author_name: List<String> = emptyList(),
    val publisher: List<String> = emptyList(),
    val publish_date: List<String> = emptyList(),
    val publish_place: List<String> = emptyList(),
    val subject: List<String> = emptyList(),
    val seed: List<String> = emptyList(),
    val isbn: List<String> = emptyList()
)
