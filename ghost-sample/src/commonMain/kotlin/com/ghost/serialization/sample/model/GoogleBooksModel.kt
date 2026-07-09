package com.ghost.serialization.sample.model

import com.ghost.serialization.annotations.GhostProtoSerialization
import kotlinx.serialization.Serializable

/**
 * Root response from the Google Books Volumes API.
 * Annotated with @GhostProtoSerialization so GhostProtobuf can deserialize it
 * as proto3 JSON (handles quoted int64, RFC3339 timestamps, optional fields, etc.)
 */
@Serializable
@GhostProtoSerialization
data class BooksVolumeListResponse(
    val kind: String = "",
    val totalItems: Int = 0,
    val items: List<BookVolume> = emptyList()
)

@Serializable
@GhostProtoSerialization
data class BookVolume(
    val id: String = "",
    val selfLink: String = "",
    val volumeInfo: VolumeInfo = VolumeInfo(),
    val saleInfo: SaleInfo = SaleInfo(),
    val accessInfo: AccessInfo = AccessInfo()
)

@Serializable
@GhostProtoSerialization
data class VolumeInfo(
    val title: String = "",
    val subtitle: String? = null,
    val authors: List<String> = emptyList(),
    val publisher: String? = null,
    val publishedDate: String = "",
    val description: String? = null,
    val pageCount: Int = 0,
    val printType: String = "",
    val categories: List<String> = emptyList(),
    val averageRating: Double = 0.0,
    val ratingsCount: Int = 0,
    val maturityRating: String = "",
    val imageLinks: BookImageLinks? = null,
    val language: String = "",
    val previewLink: String = "",
    val infoLink: String = "",
    val canonicalVolumeLink: String = ""
)

@Serializable
@GhostProtoSerialization
data class BookImageLinks(
    val smallThumbnail: String = "",
    val thumbnail: String = ""
)

@Serializable
@GhostProtoSerialization
data class SaleInfo(
    val country: String = "",
    val saleability: String = "",
    val isEbook: Boolean = false,
    val listPrice: BookPrice? = null,
    val retailPrice: BookPrice? = null,
    val buyLink: String? = null
)

@Serializable
@GhostProtoSerialization
data class BookPrice(
    val amount: Double = 0.0,
    val currencyCode: String = ""
)

@Serializable
@GhostProtoSerialization
data class AccessInfo(
    val country: String = "",
    val viewability: String = "",
    val embeddable: Boolean = false,
    val publicDomain: Boolean = false,
    val textToSpeechPermission: String = "",
    val accessViewStatus: String = "",
    val quoteSharingAllowed: Boolean = false
)
