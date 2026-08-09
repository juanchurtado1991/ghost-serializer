@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextLong
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.skipValue
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter

/**
 * Hand-written stand-in for what
 * [@GhostProtoSerialization][com.ghost.serialization.annotations.GhostProtoSerialization] + KSP
 * would generate for `data class ProtoDeviceEvent(val deviceId: Long, val label: String)` —
 * `deviceId` is written as a quoted decimal string (proto3 int64 mapping) and must be readable
 * back as a bare-or-quoted number, exercising exactly what [GhostProtoConverterFactory] depends
 * on ([com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader.nextLong] polymorphism via
 * `reader.nextLong()`).
 */
@InternalGhostApi
object ProtoDeviceEventSerializer : GhostSerializer<ProtoDeviceEvent> {
    override val typeName: String = "com.ghost.serialization.retrofit.ProtoDeviceEvent"

    override fun serialize(writer: GhostJsonWriter, value: ProtoDeviceEvent) {
        writer.beginObject()
        writer.name("deviceId")
        writer.value(value.deviceId.toString())
        writer.name("label")
        writer.value(value.label)
        writer.endObject()
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoDeviceEvent) {
        writer.beginObject()
        writer.name("deviceId")
        writer.value(value.deviceId.toString())
        writer.name("label")
        writer.value(value.label)
        writer.endObject()
    }

    override fun deserialize(reader: GhostJsonReader): ProtoDeviceEvent {
        var deviceId = 0L
        var label = ""
        reader.beginObject()
        while (true) {
            val key = reader.nextKey() ?: break
            reader.consumeKeySeparator()
            when (key) {
                "deviceId" -> deviceId = reader.nextLong()
                "label" -> label = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return ProtoDeviceEvent(deviceId, label)
    }

    /**
     * Explicit flat-reader override (not the default interface bridge) so a
     * [com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader] passed in by
     * [GhostProtoConverterFactory] dispatches
     * [nextLong][com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader.nextLong] to its
     * proto3-lenient implementation via virtual dispatch — the default bridge would construct a
     * plain [com.ghost.serialization.parser.streaming.GhostJsonReader] internally and lose that
     * leniency.
     */
    override fun deserialize(reader: GhostJsonFlatReader): ProtoDeviceEvent {
        var deviceId = 0L
        var label = ""
        reader.beginObject()
        while (true) {
            val key = reader.nextKey() ?: break
            reader.consumeKeySeparator()
            when (key) {
                "deviceId" -> deviceId = reader.nextLong()
                "label" -> label = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return ProtoDeviceEvent(deviceId, label)
    }
}
