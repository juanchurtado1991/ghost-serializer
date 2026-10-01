package com.ghost.serialization.proto.wkt

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.proto.GhostProto
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtoWrappersTest {

    init {
        val registry = object : AbstractGhostRegistry() {
            private val map =
                mapOf<kotlin.reflect.KClass<*>, GhostSerializer<*>>(
                    ProtoBoolValue::class to ProtoBoolValueSerializer,
                    ProtoStringValue::class to ProtoStringValueSerializer,
                    ProtoBytesValue::class to ProtoBytesValueSerializer,
                    ProtoDoubleValue::class to ProtoDoubleValueSerializer,
                    ProtoFloatValue::class to ProtoFloatValueSerializer,
                    ProtoInt32Value::class to ProtoInt32ValueSerializer,
                    ProtoInt64Value::class to ProtoInt64ValueSerializer,
                    ProtoUInt32Value::class to ProtoUInt32ValueSerializer,
                    ProtoUInt64Value::class to ProtoUInt64ValueSerializer
                )

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: kotlin.reflect.KClass<T>): GhostSerializer<T>? {
                return map[clazz] as? GhostSerializer<T>
            }

            override fun getAllSerializers(): Map<kotlin.reflect.KClass<*>, GhostSerializer<*>> {
                return map
            }

        }
        Ghost.addRegistry(registry = registry)
    }

    @Test
    fun testBoolValueRoundtrip() {
        val json = "true"
        val parsed = GhostProto.deserialize<ProtoBoolValue>(json)
        assertTrue(actual = parsed.value)
    }

    @Test
    fun testStringValueRoundtrip() {
        val json = "\"hello world\""
        val parsed = GhostProto.deserialize<ProtoStringValue>(json)
        assertEquals(
            expected = "hello world",
            actual = parsed.value
        )
    }

    @Test
    fun testBytesValueRoundtrip() {
        // base64 standard representation: "YWJj" for "abc"
        val bytes = "abc".encodeToByteArray()
        val wrapper = ProtoBytesValue(value = bytes)
        val flatBuffer = FlatByteArrayWriter(initialCapacity = 1024)
        val writer = GhostJsonWriter(flatBuffer)
        ProtoBytesValueSerializer.serialize(writer, wrapper)
        val serializedJson = flatBuffer.toStringUtf8()
        assertEquals(
            expected = "\"YWJj\"",
            actual = serializedJson
        )

        val reader = GhostProtoJsonFlatReader(rawData = serializedJson.encodeToByteArray())
        val deserialized = ProtoBytesValueSerializer.deserialize(reader)
        assertEquals(
            expected = "abc",
            actual = deserialized.value.decodeToString()
        )
    }

    @Test
    fun testBytesValueDecodesOnStreamingAndPlainFlatReader() {
        // Regression: ProtoBytesValueSerializer used to throw UnsupportedOperationException
        // unless fed a GhostProtoJsonFlatReader specifically — reachable simply by calling
        // Ghost.deserialize/deserializeStreaming instead of GhostProto.deserialize, even
        // though the same type was registered in the same global registry.
        val streamingReader = GhostJsonReader(
            "\"YWJj\"".encodeToByteArray()
        )
        val viaStreaming = ProtoBytesValueSerializer.deserialize(streamingReader)
        assertEquals(
            expected = "abc",
            actual = viaStreaming.value.decodeToString()
        )

        val plainFlatReader = GhostJsonReader(
            "\"YWJj\"".encodeToByteArray()
        )
        val viaPlainFlat = ProtoBytesValueSerializer.deserialize(plainFlatReader)
        assertEquals(
            expected = "abc",
            actual = viaPlainFlat.value.decodeToString()
        )
    }

    @Test
    fun testDoubleValueRoundtrip() {
        val parsed = GhostProto.deserialize<ProtoDoubleValue>("42.5")
        assertEquals(
            expected = 42.5,
            actual = parsed.value
        )
    }

    @Test
    fun testFloatValueRoundtrip() {
        val parsed = GhostProto.deserialize<ProtoFloatValue>("12.25")
        assertEquals(
            expected = 12.25f,
            actual = parsed.value
        )
    }

    @Test
    fun testInt32ValueRoundtrip() {
        val parsed = GhostProto.deserialize<ProtoInt32Value>("123")
        assertEquals(
            expected = 123,
            actual = parsed.value
        )
    }

    @Test
    fun testInt64ValueRoundtrip() {
        // int64 can be unquoted or quoted according to proto3 JSON
        val parsed1 = GhostProto.deserialize<ProtoInt64Value>("9223372036854775807")
        assertEquals(
            expected = 9223372036854775807L,
            actual = parsed1.value
        )

        val parsed2 = GhostProto.deserialize<ProtoInt64Value>("\"-9223372036854775808\"")
        assertEquals(
            expected = Long.MIN_VALUE,
            actual = parsed2.value
        )
    }

    @Test
    fun testUInt32ValueRoundtrip() {
        val parsed = GhostProto.deserialize<ProtoUInt32Value>("4294967295")
        assertEquals(
            expected = 4294967295L,
            actual = parsed.value
        )
    }

    @Test
    fun testUInt64ValueRoundtrip() {
        val parsed = GhostProto.deserialize<ProtoUInt64Value>("\"9223372036854775807\"")
        assertEquals(
            expected = 9223372036854775807UL,
            actual = parsed.value
        )
    }

    @Test
    fun testUInt64ValueFullRangeAboveLongMaxValue() {
        // Regression: uint64's max value exceeds Long.MAX_VALUE by more than 2x — the previous
        // Long-backed ProtoUInt64Value could not represent this at all.
        val maxUInt64Json = "\"18446744073709551615\""
        val parsed = GhostProto.deserialize<ProtoUInt64Value>(maxUInt64Json)
        assertEquals(
            expected = ULong.MAX_VALUE,
            actual = parsed.value
        )

        val flatBuffer = FlatByteArrayWriter(initialCapacity = 64)
        val writer = GhostJsonWriter(flatBuffer)
        ProtoUInt64ValueSerializer.serialize(writer, parsed)
        assertEquals(
            expected = maxUInt64Json,
            actual = flatBuffer.toStringUtf8()
        )
    }
}
