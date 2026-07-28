@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.proto

import com.ghost.serialization.parser.common.*
import com.ghost.serialization.parser.bytes.*
import com.ghost.serialization.parser.strings.*
import com.ghost.serialization.parser.streaming.*
import com.ghost.serialization.parser.proto.*
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.parser.common.GhostHeuristics
import com.ghost.serialization.parser.common.GhostJsonConstants as C

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

    /**
     * Full `uint64` range (0 to [ULong.MAX_VALUE]) — accepts either the canonical quoted
     * decimal string or a bare JSON number (only safe for values within [Long.MAX_VALUE]).
     */
    fun nextProtoUInt64(): ULong = readProtoUInt64()

    fun nextProtoBytes(): ByteArray = readProtoBytes()

    fun nextProtoEnum(options: JsonReaderOptions): Int = readProtoEnum(options)
}
