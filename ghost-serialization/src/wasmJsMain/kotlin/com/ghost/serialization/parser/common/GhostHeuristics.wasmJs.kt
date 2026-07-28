@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package com.ghost.serialization.parser.common

import com.ghost.serialization.parser.common.*
import com.ghost.serialization.parser.bytes.*
import com.ghost.serialization.parser.strings.*
import com.ghost.serialization.parser.streaming.*
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
actual object GhostHeuristics {
    actual val initialCollectionCapacity: Int = 10
    actual val maxStringPoolLength: Int = 64
    actual val maxCollectionSize: Int = 500_000
    actual val maxDiscriminatorPeekDistance: Int = 1024
    actual val maxWarmWriteBufferCapacity: Int = 1024 * 1024
    actual val maxWarmCharWriteBufferCapacity: Int = 512 * 1024
}
