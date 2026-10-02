@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.ghost.serialization.parser.common

private const val PROP_INITIAL_COLLECTION_CAPACITY = "ghost.initialCollectionCapacity"
private const val PROP_MAX_STRING_POOL_LENGTH = "ghost.maxStringPoolLength"
private const val PROP_MAX_COLLECTION_SIZE = "ghost.maxCollectionSize"
private const val PROP_MAX_DISCRIMINATOR_PEEK_DISTANCE = "ghost.maxDiscriminatorPeekDistance"
private const val PROP_MAX_WARM_WRITE_BUFFER_CAPACITY = "ghost.maxWarmWriteBufferCapacity"
private const val PROP_MAX_WARM_CHAR_WRITE_BUFFER_CAPACITY = "ghost.maxWarmCharWriteBufferCapacity"

/** JVM has headroom the other platforms don't, so it defaults higher than [GhostHeuristicDefaults]. */
private const val JVM_MAX_COLLECTION_SIZE = 1_000_000
private const val JVM_MAX_DISCRIMINATOR_PEEK_DISTANCE = 2 * GhostHeuristicDefaults.BYTES_PER_KIB
private const val JVM_MAX_WARM_WRITE_BUFFER_CAPACITY = 8 * GhostHeuristicDefaults.BYTES_PER_MIB
private const val JVM_MAX_WARM_CHAR_WRITE_BUFFER_CAPACITY = 2 * GhostHeuristicDefaults.BYTES_PER_MIB

actual object GhostHeuristics {
    actual val initialCollectionCapacity: Int = System
        .getProperty(PROP_INITIAL_COLLECTION_CAPACITY)
        ?.toIntOrNull()
        ?: GhostHeuristicDefaults.INITIAL_COLLECTION_CAPACITY

    actual val maxStringPoolLength: Int = System
        .getProperty(PROP_MAX_STRING_POOL_LENGTH)
        ?.toIntOrNull()
        ?: GhostHeuristicDefaults.MAX_STRING_POOL_LENGTH

    actual val maxCollectionSize: Int = System
        .getProperty(PROP_MAX_COLLECTION_SIZE)
        ?.toIntOrNull()
        ?: JVM_MAX_COLLECTION_SIZE

    actual val maxDiscriminatorPeekDistance: Int = System
        .getProperty(PROP_MAX_DISCRIMINATOR_PEEK_DISTANCE)
        ?.toIntOrNull()
        ?: JVM_MAX_DISCRIMINATOR_PEEK_DISTANCE

    actual val maxWarmWriteBufferCapacity: Int = System
        .getProperty(PROP_MAX_WARM_WRITE_BUFFER_CAPACITY)
        ?.toIntOrNull()
        ?: JVM_MAX_WARM_WRITE_BUFFER_CAPACITY

    actual val maxWarmCharWriteBufferCapacity: Int = System
        .getProperty(PROP_MAX_WARM_CHAR_WRITE_BUFFER_CAPACITY)
        ?.toIntOrNull()
        ?: JVM_MAX_WARM_CHAR_WRITE_BUFFER_CAPACITY

    actual val encodeToStringViaUtf8Bytes: Boolean = false
}
