@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.ghost.serialization.parser

private const val PROP_INITIAL_COLLECTION_CAPACITY = "ghost.initialCollectionCapacity"
private const val PROP_MAX_STRING_POOL_LENGTH = "ghost.maxStringPoolLength"
private const val PROP_MAX_COLLECTION_SIZE = "ghost.maxCollectionSize"
private const val PROP_MAX_DISCRIMINATOR_PEEK_DISTANCE = "ghost.maxDiscriminatorPeekDistance"
private const val PROP_MAX_WARM_WRITE_BUFFER_CAPACITY = "ghost.maxWarmWriteBufferCapacity"
private const val PROP_MAX_WARM_CHAR_WRITE_BUFFER_CAPACITY = "ghost.maxWarmCharWriteBufferCapacity"

actual object GhostHeuristics {
    actual val initialCollectionCapacity: Int = 
        System.getProperty(PROP_INITIAL_COLLECTION_CAPACITY)?.toIntOrNull() ?: 10
    actual val maxStringPoolLength: Int = 
        System.getProperty(PROP_MAX_STRING_POOL_LENGTH)?.toIntOrNull() ?: 64
    actual val maxCollectionSize: Int = 
        System.getProperty(PROP_MAX_COLLECTION_SIZE)?.toIntOrNull() ?: 1_000_000
    actual val maxDiscriminatorPeekDistance: Int = 
        System.getProperty(PROP_MAX_DISCRIMINATOR_PEEK_DISTANCE)?.toIntOrNull() ?: 2048
    actual val maxWarmWriteBufferCapacity: Int = 
        System.getProperty(PROP_MAX_WARM_WRITE_BUFFER_CAPACITY)?.toIntOrNull() ?: (8 * 1024 * 1024)
    actual val maxWarmCharWriteBufferCapacity: Int = 
        System.getProperty(PROP_MAX_WARM_CHAR_WRITE_BUFFER_CAPACITY)?.toIntOrNull() ?: (2 * 1024 * 1024)
}
