package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass
import org.springframework.boot.context.properties.EnableConfigurationProperties

/**
 * Marker auto-configuration for Ghost Serialization in Spring Boot.
 * Web stack wiring: [GhostWebMvcAutoConfiguration], [GhostWebFluxAutoConfiguration].
 */
@AutoConfiguration
@ConditionalOnClass(Ghost::class)
@EnableConfigurationProperties(GhostProperties::class)
class GhostAutoConfiguration(properties: GhostProperties) {
    init {
        properties.initialCollectionCapacity?.let {
            System.setProperty(GhostProperties.PROP_INITIAL_COLLECTION_CAPACITY, it.toString())
        }
        properties.maxStringPoolLength?.let {
            System.setProperty(GhostProperties.PROP_MAX_STRING_POOL_LENGTH, it.toString())
        }
        properties.maxCollectionSize?.let {
            System.setProperty(GhostProperties.PROP_MAX_COLLECTION_SIZE, it.toString())
        }
        properties.maxDiscriminatorPeekDistance?.let {
            System.setProperty(GhostProperties.PROP_MAX_DISCRIMINATOR_PEEK_DISTANCE, it.toString())
        }
        properties.maxWarmWriteBufferCapacity?.let {
            System.setProperty(GhostProperties.PROP_MAX_WARM_WRITE_BUFFER_CAPACITY, it.toString())
        }
        properties.maxWarmCharWriteBufferCapacity?.let {
            System.setProperty(GhostProperties.PROP_MAX_WARM_CHAR_WRITE_BUFFER_CAPACITY, it.toString())
        }
    }
}
