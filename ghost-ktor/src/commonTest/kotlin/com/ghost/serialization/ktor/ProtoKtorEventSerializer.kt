@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.ktor

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

// --- Mock proto-flavored model & hand-written stand-in for @GhostProtoSerialization codegen ---
object ProtoKtorEventSerializer : GhostSerializer<ProtoKtorEvent> {
    override val typeName: String = "com.ghost.serialization.ktor.ProtoKtorEvent"

    override fun serialize(writer: GhostJsonWriter, value: ProtoKtorEvent) {
        writer.beginObject()
        writer.name("deviceId")
        writer.value(value.deviceId.toString())
        writer.name("label")
        writer.value(value.label)
        writer.endObject()
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ProtoKtorEvent) {
        writer.beginObject()
        writer.name("deviceId")
        writer.value(value.deviceId.toString())
        writer.name("label")
        writer.value(value.label)
        writer.endObject()
    }

    override fun deserialize(reader: GhostJsonReader): ProtoKtorEvent {
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
        return ProtoKtorEvent(deviceId, label)
    }

    // Explicit (not the default interface bridge) so a GhostProtoJsonFlatReader dispatches
    // reader.nextLong() to its overridden, proto3-lenient implementation.
    override fun deserialize(reader: GhostJsonFlatReader): ProtoKtorEvent {
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
        return ProtoKtorEvent(deviceId, label)
    }
}
