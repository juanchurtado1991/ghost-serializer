@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.proto

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.common.constants.GhostJsonNumericLimits as NUM

class GhostProtoJsonFlatReader(
    rawData: ByteArray,
    maxDepth: Int = NUM.MAX_DEPTH,
    maxCollectionSize: Int = GhostHeuristics.maxCollectionSize
) : GhostJsonFlatReader(
    rawData = rawData,
    maxDepth = maxDepth,
    maxCollectionSize = maxCollectionSize
) {

    override fun nextDouble(): Double = nextProtoDouble()

    override fun nextFloat(): Float = nextProtoFloat()

    override fun nextInt(): Int = nextProtoInt32()

    override fun nextLong(): Long = nextProtoInt64()

    fun nextProtoBytes(): ByteArray = readProtoBytes()

    fun nextProtoEnum(
        options: JsonReaderOptions
    ): Int = readProtoEnum(options = options)

    fun nextProtoUInt32(): Long = readProtoUInt32()

    override fun nextProtoUInt64(): ULong = readProtoUInt64()
}
