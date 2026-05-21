@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.ghost.serialization.parser

actual object GhostHeuristics {
    actual val initialCollectionCapacity: Int = 10
    actual val maxStringPoolLength: Int = 512
    actual val maxCollectionSize: Int = 1_000_000
    actual val maxDiscriminatorPeekDistance: Int = 2048
    actual val maxPayloadBytes: Int = 16 * 1024 * 1024 // 16 MB
}
