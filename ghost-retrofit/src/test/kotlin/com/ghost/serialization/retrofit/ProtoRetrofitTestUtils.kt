@file:OptIn(InternalGhostApi::class)
package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.beginObject
import com.ghost.serialization.parser.consumeKeySeparator
import com.ghost.serialization.parser.endObject
import com.ghost.serialization.parser.nextKey
import com.ghost.serialization.parser.nextLong
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.parser.skipValue
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter
import kotlin.reflect.KClass

/**
 * Hand-written stand-in for what `@GhostProtoSerialization` + KSP would generate for
 * `data class ProtoDeviceEvent(val deviceId: Long, val label: String)` — `deviceId` is written
 * as a quoted decimal string (proto3 int64 mapping) and must be readable back as a bare-or-quoted
 * number, exercising exactly what [GhostProtoConverterFactory] depends on
 * (`GhostProtoJsonFlatReader.nextLong()` polymorphism via `reader.nextLong()`).
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

    // Explicit (not the default interface bridge) so a GhostProtoJsonFlatReader passed in by
    // GhostProtoConverterFactory dispatches reader.nextLong() to its overridden, proto3-lenient
    // implementation via virtual dispatch — the default bridge would construct a plain
    // GhostJsonReader internally and lose that leniency.
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

@InternalGhostApi
object ProtoRetrofitTestRegistry : GhostRegistry {
    override fun prewarm() {}
    override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
        mapOf(ProtoDeviceEvent::class to ProtoDeviceEventSerializer)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
        if (clazz == ProtoDeviceEvent::class) ProtoDeviceEventSerializer as GhostSerializer<T> else null
}

data class ProtoDeviceEvent(val deviceId: Long, val label: String)
