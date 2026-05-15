package com.ghost.serialization.parser

actual object GhostHeuristics {
    actual val initialCollectionCapacity: Int = 16
    actual val maxStringPoolLength: Int = 64
    actual val maxCollectionSize: Int = 50_000
    actual val maxDiscriminatorPeekDistance: Int = 1024
}
