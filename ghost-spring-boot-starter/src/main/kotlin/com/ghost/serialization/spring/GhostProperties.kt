package com.ghost.serialization.spring

import org.springframework.boot.context.properties.ConfigurationProperties

/**
 * Configuration properties for tuning Ghost Serialization platform heuristics at startup.
 */
@ConfigurationProperties(prefix = GhostProperties.PREFIX)
class GhostProperties {
    var initialCollectionCapacity: Int? = null
    var maxStringPoolLength: Int? = null
    var maxCollectionSize: Int? = null
    var maxDiscriminatorPeekDistance: Int? = null
    var maxWarmWriteBufferCapacity: Int? = null
    var maxWarmCharWriteBufferCapacity: Int? = null

    companion object {
        const val PREFIX = "ghost"
        const val PROP_INITIAL_COLLECTION_CAPACITY = "ghost.initialCollectionCapacity"
        const val PROP_MAX_STRING_POOL_LENGTH = "ghost.maxStringPoolLength"
        const val PROP_MAX_COLLECTION_SIZE = "ghost.maxCollectionSize"
        const val PROP_MAX_DISCRIMINATOR_PEEK_DISTANCE = "ghost.maxDiscriminatorPeekDistance"
        const val PROP_MAX_WARM_WRITE_BUFFER_CAPACITY = "ghost.maxWarmWriteBufferCapacity"
        const val PROP_MAX_WARM_CHAR_WRITE_BUFFER_CAPACITY = "ghost.maxWarmCharWriteBufferCapacity"
    }
}
