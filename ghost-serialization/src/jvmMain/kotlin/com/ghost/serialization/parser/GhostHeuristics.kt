package com.ghost.serialization.parser

actual object GhostHeuristics {
    actual val initialCollectionCapacity: Int = 32
    actual val maxStringPoolLength: Int = 512
    actual val maxCollectionSize: Int = 1_000_000
}
