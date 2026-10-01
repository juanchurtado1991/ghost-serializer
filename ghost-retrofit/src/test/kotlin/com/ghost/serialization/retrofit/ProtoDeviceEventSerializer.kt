@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextLong
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.skipValue
import com.ghost.serialization.writer.bytes.GhostJsonWriter

/**
 * Hand-written stand-in for `@GhostProtoSerialization` + KSP codegen: writes `deviceId` as a
 * quoted decimal string (proto3 int64 mapping) that must read back as bare-or-quoted, exercising
 * [GhostProtoConverterFactory]'s reliance on `GhostProtoJsonFlatReader.nextLong` polymorphism.
 */
@InternalGhostApi
object ProtoDeviceEventSerializer : AbstractGhostSerializer<ProtoDeviceEvent>() {
    override val typeName: String = "com.ghost.serialization.retrofit.ProtoDeviceEvent"

    override fun serialize(writer: GhostJsonWriter, value: ProtoDeviceEvent) {
        writer.beginObject()
        writer.name(key = "deviceId")
        writer.value(value.deviceId.toString())
        writer.name(key = "label")
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
        return ProtoDeviceEvent(deviceId = deviceId, label = label)
    }

    /**
     * Explicit override (not the default interface bridge) so a `GhostProtoJsonFlatReader`
     * dispatches `nextLong` to its proto3-lenient implementation; the default bridge would
     * construct a plain `GhostJsonReader` and lose that leniency.
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
        return ProtoDeviceEvent(deviceId = deviceId, label = label)
    }
}
