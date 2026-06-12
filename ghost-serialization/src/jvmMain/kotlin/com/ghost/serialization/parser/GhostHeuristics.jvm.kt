@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.ghost.serialization.parser

actual object GhostHeuristics {
    actual val initialCollectionCapacity: Int = 10
    actual val maxStringPoolLength: Int = 64
    actual val maxCollectionSize: Int = 1_000_000
    actual val maxDiscriminatorPeekDistance: Int = 2048
    actual val maxWarmWriteBufferCapacity: Int = 8 * 1024 * 1024
    actual val maxWarmCharWriteBufferCapacity: Int = 2 * 1024 * 1024
}
