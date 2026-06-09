package com.ghost.serialization.parser

actual object GhostHeuristics {
    actual val initialCollectionCapacity: Int = 10
    actual val maxStringPoolLength: Int = 48
    actual val maxCollectionSize: Int = 50_000
    actual val maxDiscriminatorPeekDistance: Int = 512
    actual val maxWarmWriteBufferCapacity: Int = 2 * 1024 * 1024
    actual val maxWarmCharWriteBufferCapacity: Int = 128 * 1024
}
