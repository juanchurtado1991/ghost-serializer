@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.ghost.serialization.parser.common

actual object GhostHeuristics {
    actual val initialCollectionCapacity: Int = GhostHeuristicDefaults.INITIAL_COLLECTION_CAPACITY
    actual val maxStringPoolLength: Int = GhostHeuristicDefaults.MAX_STRING_POOL_LENGTH
    actual val maxCollectionSize: Int = GhostHeuristicDefaults.MAX_COLLECTION_SIZE
    actual val maxDiscriminatorPeekDistance: Int = GhostHeuristicDefaults.MAX_DISCRIMINATOR_PEEK_DISTANCE
    actual val maxWarmWriteBufferCapacity: Int = GhostHeuristicDefaults.MAX_WARM_WRITE_BUFFER_CAPACITY
    actual val maxWarmCharWriteBufferCapacity: Int = GhostHeuristicDefaults.MAX_WARM_CHAR_WRITE_BUFFER_CAPACITY
    actual val encodeToStringViaUtf8Bytes: Boolean = false
}
