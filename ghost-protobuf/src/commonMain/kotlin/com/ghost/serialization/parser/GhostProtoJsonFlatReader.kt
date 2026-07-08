@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.JsonReaderOptions
import com.ghost.serialization.parser.GhostHeuristics
import com.ghost.serialization.parser.GhostJsonConstants as C

class GhostProtoJsonFlatReader(
    rawData: ByteArray,
    maxDepth: Int = C.MAX_DEPTH,
    maxCollectionSize: Int = GhostHeuristics.maxCollectionSize
) : GhostJsonFlatReader(rawData, maxDepth = maxDepth, maxCollectionSize = maxCollectionSize) {

    override fun nextFloat(): Float = nextProtoFloat()

    override fun nextDouble(): Double = nextProtoDouble()

    override fun nextInt(): Int = nextProtoInt32()

    override fun nextLong(): Long = nextProtoInt64()

    fun nextProtoUInt32(): Long = readProtoUInt32()

    fun nextProtoUInt64(): Long = nextProtoInt64() // fits standard int64 coercion/quoted rules

    fun nextProtoBytes(): ByteArray = readProtoBytes()

    fun nextProtoEnum(options: JsonReaderOptions): Int = readProtoEnum(options)
}
