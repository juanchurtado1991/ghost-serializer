package com.ghost.serialization.proto.wkt

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.google.protobuf.BoolValue
import com.google.protobuf.BytesValue
import com.google.protobuf.DoubleValue
import com.google.protobuf.Duration
import com.google.protobuf.FloatValue
import com.google.protobuf.Int32Value
import com.google.protobuf.Int64Value
import com.google.protobuf.ListValue
import com.google.protobuf.StringValue
import com.google.protobuf.Struct
import com.google.protobuf.Timestamp
import com.google.protobuf.UInt32Value
import com.google.protobuf.UInt64Value
import com.google.protobuf.Value
import com.google.protobuf.util.JsonFormat
import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Cross-checks Ghost's proto3 JSON output against `protobuf-java` (Google's own reference
 * implementation) for the Well-Known Types — the authoritative oracle for "is this actually
 * spec-compliant JSON", as opposed to every other test in this module, which only proves Ghost
 * is internally consistent (it reads back what it wrote).
 */
class ProtoJsonConformanceTest {

    private val printer: JsonFormat.Printer = JsonFormat.printer().omittingInsignificantWhitespace()

    @BeforeTest
    fun setup() {
        val map = mapOf<KClass<*>, GhostSerializer<*>>(
            ProtoDuration::class to ProtoDurationSerializer,
            ProtoTimestamp::class to ProtoTimestampSerializer,
            ProtoBoolValue::class to ProtoBoolValueSerializer,
            ProtoStringValue::class to ProtoStringValueSerializer,
            ProtoBytesValue::class to ProtoBytesValueSerializer,
            ProtoDoubleValue::class to ProtoDoubleValueSerializer,
            ProtoFloatValue::class to ProtoFloatValueSerializer,
            ProtoInt32Value::class to ProtoInt32ValueSerializer,
            ProtoInt64Value::class to ProtoInt64ValueSerializer,
            ProtoUInt32Value::class to ProtoUInt32ValueSerializer,
            ProtoUInt64Value::class to ProtoUInt64ValueSerializer,
        )
        Ghost.addRegistry(registry = object : AbstractGhostRegistry() {
            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
                map[clazz] as? GhostSerializer<T>

            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> = map

        })
    }

    // --- Duration ---

    @Test
    fun durationMatchesReferenceImplementation() {
        val cases = listOf(
            ProtoDuration(seconds = 123456L, nanos = 789) to Duration.newBuilder().setSeconds(123456L).setNanos(789)
                .build(),
            ProtoDuration(seconds = -123L, nanos = -450000000) to Duration.newBuilder().setSeconds(-123L)
                .setNanos(-450000000).build(),
            ProtoDuration(seconds = 0L, nanos = 0) to Duration.getDefaultInstance(),
            ProtoDuration(seconds = 1L, nanos = 0) to Duration.newBuilder().setSeconds(1L).build(),
        )
        for ((ghostValue, javaValue) in cases) {
            assertEquals(
                expected = printer.print(javaValue),
                actual = Ghost.encodeToString(ghostValue),
                message = "seconds=${ghostValue.seconds} nanos=${ghostValue.nanos}"
            )
        }
    }

    // --- Timestamp ---

    @Test
    fun timestampMatchesReferenceImplementation() {
        val cases = listOf(
            ProtoTimestamp(seconds = 1783515300L, nanos = 123456789) to Timestamp.newBuilder().setSeconds(1783515300L)
                .setNanos(123456789).build(),
            ProtoTimestamp(seconds = 0L, nanos = 0) to Timestamp.getDefaultInstance(),
            ProtoTimestamp(seconds = 1783447200L, nanos = 125000000) to Timestamp.newBuilder().setSeconds(1783447200L)
                .setNanos(125000000).build(),
        )
        for ((ghostValue, javaValue) in cases) {
            assertEquals(
                expected = printer.print(javaValue),
                actual = Ghost.encodeToString(ghostValue),
                message = "seconds=${ghostValue.seconds} nanos=${ghostValue.nanos}"
            )
        }
    }

    // --- Scalar wrapper types ---

    @Test
    fun boolValueMatchesReferenceImplementation() {
        assertEquals(
            expected = printer.print(BoolValue.of(true)),
            actual = Ghost.encodeToString(ProtoBoolValue(value = true))
        )
        assertEquals(
            expected = printer.print(BoolValue.of(false)),
            actual = Ghost.encodeToString(ProtoBoolValue(value = false))
        )
    }

    @Test
    fun stringValueMatchesReferenceImplementation() {
        assertEquals(
            expected = printer.print(StringValue.of("hello world")),
            actual = Ghost.encodeToString(ProtoStringValue(value = "hello world"))
        )
        assertEquals(
            expected = printer.print(StringValue.of("")),
            actual = Ghost.encodeToString(ProtoStringValue(value = ""))
        )
    }

    @Test
    fun doubleValueMatchesReferenceImplementation() {
        assertEquals(
            expected = printer.print(DoubleValue.of(42.5)),
            actual = Ghost.encodeToString(ProtoDoubleValue(value = 42.5))
        )
    }

    @Test
    fun floatValueMatchesReferenceImplementation() {
        assertEquals(
            expected = printer.print(FloatValue.of(12.25f)),
            actual = Ghost.encodeToString(ProtoFloatValue(value = 12.25f))
        )
    }

    @Test
    fun int32ValueMatchesReferenceImplementation() {
        assertEquals(
            expected = printer.print(Int32Value.of(123)),
            actual = Ghost.encodeToString(ProtoInt32Value(value = 123))
        )
        assertEquals(
            expected = printer.print(Int32Value.of(Int.MIN_VALUE)),
            actual = Ghost.encodeToString(ProtoInt32Value(value = Int.MIN_VALUE))
        )
    }

    @Test
    fun int64ValueMatchesReferenceImplementation() {
        assertEquals(
            expected = printer.print(Int64Value.of(Long.MAX_VALUE)),
            actual = Ghost.encodeToString(ProtoInt64Value(value = Long.MAX_VALUE))
        )
        assertEquals(
            expected = printer.print(Int64Value.of(Long.MIN_VALUE)),
            actual = Ghost.encodeToString(ProtoInt64Value(value = Long.MIN_VALUE))
        )
    }

    @Test
    fun uInt32ValueMatchesReferenceImplementation() {
        assertEquals(
            expected = printer.print(UInt32Value.of(4294967295L.toInt())),
            actual = Ghost.encodeToString(ProtoUInt32Value(value = 4294967295L))
        )
    }

    @Test
    fun uInt64ValueMatchesReferenceImplementation() {
        // protobuf-java's UInt64Value.of takes a signed Long whose bit pattern is interpreted as
        // unsigned — Long.MAX_VALUE is within both representations, a safe cross-check value.
        assertEquals(
            expected = printer.print(UInt64Value.of(Long.MAX_VALUE)),
            actual = Ghost.encodeToString(ProtoUInt64Value(value = Long.MAX_VALUE.toULong()))
        )
    }

    @Test
    fun bytesValueMatchesReferenceImplementation() {
        val bytes = "abc+123".encodeToByteArray()
        val flatBuffer = FlatByteArrayWriter(initialCapacity = 64)
        val writer = GhostJsonWriter(flatBuffer)
        ProtoBytesValueSerializer.serialize(writer, ProtoBytesValue(value = bytes))
        assertEquals(
            expected = printer.print(BytesValue.of(com.google.protobuf.ByteString.copyFrom(bytes))),
            actual = flatBuffer.toStringUtf8()
        )
    }

    // --- Struct / Value ---

    @Test
    fun structMatchesReferenceImplementation() {
        val ghostStruct: ProtoStruct = mapOf(
            "a" to ProtoValue.Null,
            "b" to ProtoValue.Number(value = 123.45),
            "c" to ProtoValue.Str(value = "hello"),
            "d" to ProtoValue.Bool(value = true),
            "e" to ProtoValue.Struct(value = mapOf("x" to ProtoValue.Number(value = 1.0))),
            "f" to ProtoValue.List(value = listOf(ProtoValue.Number(value = 2.0), ProtoValue.Str(value = "y"))),
        )
        val javaStruct = Struct.newBuilder()
            .putFields("a", Value.newBuilder().setNullValueValue(0).build())
            .putFields("b", Value.newBuilder().setNumberValue(123.45).build())
            .putFields("c", Value.newBuilder().setStringValue("hello").build())
            .putFields("d", Value.newBuilder().setBoolValue(true).build())
            .putFields(
                "e",
                Value.newBuilder().setStructValue(
                    Struct.newBuilder()
                        .putFields("x", Value.newBuilder().setNumberValue(1.0).build())
                ).build(),
            )
            .putFields(
                "f",
                Value.newBuilder().setListValue(
                    ListValue.newBuilder()
                        .addValues(Value.newBuilder().setNumberValue(2.0).build())
                        .addValues(Value.newBuilder().setStringValue("y").build())
                ).build(),
            )
            .build()

        val flatBuffer = FlatByteArrayWriter(initialCapacity = 512)
        val writer = GhostJsonWriter(flatBuffer)
        ProtoStructSerializer.serialize(writer, ghostStruct)
        assertEquals(
            expected = printer.print(javaStruct),
            actual = flatBuffer.toStringUtf8()
        )
    }

    // --- Empty ---

    @Test
    fun emptyMatchesReferenceImplementation() {
        val flatBuffer = FlatByteArrayWriter(initialCapacity = 16)
        val writer = GhostJsonWriter(flatBuffer)
        ProtoEmptySerializer.serialize(writer, ProtoEmpty)
        assertEquals(
            expected = printer.print(com.google.protobuf.Empty.getDefaultInstance()),
            actual = flatBuffer.toStringUtf8()
        )
    }
}
