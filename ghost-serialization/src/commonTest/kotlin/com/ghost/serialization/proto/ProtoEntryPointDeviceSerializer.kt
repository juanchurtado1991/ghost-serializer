@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
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
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.endObject
import com.ghost.serialization.parser.strings.nextKey
import com.ghost.serialization.parser.strings.nextLong
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.parser.strings.skipValue
import com.ghost.serialization.proto.wkt.ProtoDuration
import com.ghost.serialization.proto.wkt.ProtoDurationSerializer
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter
import kotlin.reflect.KClass

/** Minimal proto-flavored model for entry-point and leniency tests. */
object ProtoEntryPointDeviceSerializer : GhostSerializer<ProtoEntryPointDevice> {
    override val typeName: String = "ProtoEntryPointDevice"

    override fun serialize(writer: GhostJsonWriter, value: ProtoEntryPointDevice) {
        writer.beginObject()
        writer.name("deviceId")
        writer.value(value.deviceId.toString())
        writer.name("label")
        writer.value(value.label)
        writer.endObject()
    }

    override fun serialize(writer: GhostJsonStringWriter, value: ProtoEntryPointDevice) {
        writer.beginObject()
        writer.name("deviceId")
        writer.value(value.deviceId.toString())
        writer.name("label")
        writer.value(value.label)
        writer.endObject()
    }

    override fun deserialize(reader: GhostJsonReader): ProtoEntryPointDevice =
        deserializeImpl(reader)

    override fun deserialize(reader: GhostJsonFlatReader): ProtoEntryPointDevice =
        deserializeImpl(reader)

    override fun deserialize(reader: GhostJsonStringReader): ProtoEntryPointDevice =
        deserializeImpl(reader)

    private fun deserializeImpl(reader: Any): ProtoEntryPointDevice {
        var deviceId = 0L
        var label = ""
        when (reader) {
            is GhostJsonReader -> {
                reader.beginObject()
                while (true) {
                    when (val key = reader.nextKey() ?: break) {
                        "deviceId" -> {
                            reader.consumeKeySeparator()
                            deviceId = reader.nextLong()
                        }

                        "label" -> {
                            reader.consumeKeySeparator()
                            label = reader.nextString()
                        }

                        else -> {
                            reader.consumeKeySeparator()
                            reader.skipValue()
                        }
                    }
                }
                reader.endObject()
            }

            is GhostJsonFlatReader -> {
                reader.beginObject()
                while (true) {
                    when (val key = reader.nextKey() ?: break) {
                        "deviceId" -> {
                            reader.consumeKeySeparator()
                            deviceId = reader.nextLong()
                        }

                        "label" -> {
                            reader.consumeKeySeparator()
                            label = reader.nextString()
                        }

                        else -> {
                            reader.consumeKeySeparator()
                            reader.skipValue()
                        }
                    }
                }
                reader.endObject()
            }

            is GhostJsonStringReader -> {
                reader.beginObject()
                while (true) {
                    when (val key = reader.nextKey() ?: break) {
                        "deviceId" -> {
                            reader.consumeKeySeparator()
                            deviceId = reader.nextLong()
                        }

                        "label" -> {
                            reader.consumeKeySeparator()
                            label = reader.nextString()
                        }

                        else -> {
                            reader.consumeKeySeparator()
                            reader.skipValue()
                        }
                    }
                }
                reader.endObject()
            }

            else -> error("Unsupported reader: $reader")
        }
        return ProtoEntryPointDevice(deviceId, label)
    }
}

/** Registers [ProtoDuration] and [ProtoEntryPointDevice] for proto test suites. */
fun registerProtoTestFixtures() {
    Ghost.addRegistry(
        object : GhostRegistry {
            private val map =
                mapOf<KClass<*>, GhostSerializer<*>>(
                    ProtoDuration::class to ProtoDurationSerializer,
                    ProtoEntryPointDevice::class to ProtoEntryPointDeviceSerializer,
                )

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
                map[clazz] as? GhostSerializer<T>

            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> = map
        },
    )
}

fun protoReaderOf(json: String): GhostProtoJsonFlatReader =
    GhostProtoJsonFlatReader(json.encodeToByteArray())
