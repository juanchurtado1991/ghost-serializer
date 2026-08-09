@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.consumeNull
import com.ghost.serialization.parser.streaming.isNextNullValue
import com.ghost.serialization.parser.streaming.nextBoolean
import com.ghost.serialization.parser.streaming.nextDouble
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.readList
import com.ghost.serialization.parser.streaming.readMap
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.parser.common.GhostJsonConstants as C


sealed class ProtoValue {
    object Null : ProtoValue()
    data class Number(val value: Double) : ProtoValue()
    data class Str(val value: String) : ProtoValue()
    data class Bool(val value: Boolean) : ProtoValue()
    data class Struct(val value: Map<String, ProtoValue>) : ProtoValue()
    data class List(val value: kotlin.collections.List<ProtoValue>) : ProtoValue()
}
