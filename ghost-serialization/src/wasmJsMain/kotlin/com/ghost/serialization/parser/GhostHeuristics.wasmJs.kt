package com.ghost.serialization.parser

actual object GhostHeuristics {
    actual val initialCollectionCapacity: Int = 12
    actual val maxStringPoolLength: Int = 48
    actual val maxCollectionSize: Int = 50_000
    actual val maxDiscriminatorPeekDistance: Int = 512
}
