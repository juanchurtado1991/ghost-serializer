package com.ghost.protobuf.wkt

import com.ghost.protobuf.GhostProtobuf
import com.ghost.serialization.parser.GhostProtoJsonFlatReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtoWrappersTest {

    init {
        val registry = object : com.ghost.serialization.contract.GhostRegistry {
            private val map = mapOf<kotlin.reflect.KClass<*>, com.ghost.serialization.contract.GhostSerializer<*>>(
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
            override fun <T : Any> getSerializer(clazz: kotlin.reflect.KClass<T>): com.ghost.serialization.contract.GhostSerializer<T>? {
                return map[clazz] as? com.ghost.serialization.contract.GhostSerializer<T>
            }
            override fun getAllSerializers(): Map<kotlin.reflect.KClass<*>, com.ghost.serialization.contract.GhostSerializer<*>> {
                return map
            }
        }
        com.ghost.serialization.Ghost.addRegistry(registry)
    }

    @Test
    fun testBoolValueRoundtrip() {
        val json = "true"
        val parsed = GhostProtobuf.deserialize<ProtoBoolValue>(json)
        assertTrue(parsed.value)
    }

    @Test
    fun testStringValueRoundtrip() {
        val json = "\"hello world\""
        val parsed = GhostProtobuf.deserialize<ProtoStringValue>(json)
        assertEquals("hello world", parsed.value)
    }

    @Test
    fun testBytesValueRoundtrip() {
        // base64 standard representation: "YWJj" for "abc"
        val bytes = "abc".encodeToByteArray()
        val wrapper = ProtoBytesValue(bytes)
        val flatBuffer = com.ghost.serialization.writer.FlatByteArrayWriter(1024)
        val writer = com.ghost.serialization.writer.GhostJsonFlatWriter(flatBuffer)
        ProtoBytesValueSerializer.serialize(writer, wrapper)
        val serializedJson = flatBuffer.toStringUtf8()
        assertEquals("\"YWJj\"", serializedJson)

        val reader = GhostProtoJsonFlatReader(serializedJson.encodeToByteArray())
        val deserialized = ProtoBytesValueSerializer.deserialize(reader)
        assertEquals("abc", deserialized.value.decodeToString())
    }

    @Test
    fun testBytesValueDecodesOnStreamingAndPlainFlatReader() {
        // Regression: ProtoBytesValueSerializer used to throw UnsupportedOperationException
        // unless fed a GhostProtoJsonFlatReader specifically — reachable simply by calling
        // Ghost.deserialize/deserializeStreaming instead of GhostProtobuf.deserialize, even
        // though the same type was registered in the same global registry.
        val streamingReader = com.ghost.serialization.parser.GhostJsonReader(
            "\"YWJj\"".encodeToByteArray()
        )
        val viaStreaming = ProtoBytesValueSerializer.deserialize(streamingReader)
        assertEquals("abc", viaStreaming.value.decodeToString())

        val plainFlatReader = com.ghost.serialization.parser.GhostJsonFlatReader(
            "\"YWJj\"".encodeToByteArray()
        )
        val viaPlainFlat = ProtoBytesValueSerializer.deserialize(plainFlatReader)
        assertEquals("abc", viaPlainFlat.value.decodeToString())
    }

    @Test
    fun testDoubleValueRoundtrip() {
        val parsed = GhostProtobuf.deserialize<ProtoDoubleValue>("42.5")
        assertEquals(42.5, parsed.value)
    }

    @Test
    fun testFloatValueRoundtrip() {
        val parsed = GhostProtobuf.deserialize<ProtoFloatValue>("12.25")
        assertEquals(12.25f, parsed.value)
    }

    @Test
    fun testInt32ValueRoundtrip() {
        val parsed = GhostProtobuf.deserialize<ProtoInt32Value>("123")
        assertEquals(123, parsed.value)
    }

    @Test
    fun testInt64ValueRoundtrip() {
        // int64 can be unquoted or quoted according to proto3 JSON
        val parsed1 = GhostProtobuf.deserialize<ProtoInt64Value>("9223372036854775807")
        assertEquals(9223372036854775807L, parsed1.value)

        val parsed2 = GhostProtobuf.deserialize<ProtoInt64Value>("\"-9223372036854775808\"")
        assertEquals(Long.MIN_VALUE, parsed2.value)
    }

    @Test
    fun testUInt32ValueRoundtrip() {
        val parsed = GhostProtobuf.deserialize<ProtoUInt32Value>("4294967295")
        assertEquals(4294967295L, parsed.value)
    }

    @Test
    fun testUInt64ValueRoundtrip() {
        val parsed = GhostProtobuf.deserialize<ProtoUInt64Value>("\"9223372036854775807\"")
        assertEquals(9223372036854775807UL, parsed.value)
    }

    @Test
    fun testUInt64ValueFullRangeAboveLongMaxValue() {
        // Regression: uint64's max value exceeds Long.MAX_VALUE by more than 2x — the previous
        // Long-backed ProtoUInt64Value could not represent this at all.
        val maxUInt64Json = "\"18446744073709551615\""
        val parsed = GhostProtobuf.deserialize<ProtoUInt64Value>(maxUInt64Json)
        assertEquals(ULong.MAX_VALUE, parsed.value)

        val flatBuffer = com.ghost.serialization.writer.FlatByteArrayWriter(64)
        val writer = com.ghost.serialization.writer.GhostJsonFlatWriter(flatBuffer)
        ProtoUInt64ValueSerializer.serialize(writer, parsed)
        assertEquals(maxUInt64Json, flatBuffer.toStringUtf8())
    }
}
