package com.ghost.protobuf.wkt

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
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
        Ghost.addRegistry(object : GhostRegistry {
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
            ProtoDuration(123456L, 789) to Duration.newBuilder().setSeconds(123456L).setNanos(789).build(),
            ProtoDuration(-123L, -450000000) to Duration.newBuilder().setSeconds(-123L).setNanos(-450000000).build(),
            ProtoDuration(0L, 0) to Duration.getDefaultInstance(),
            ProtoDuration(1L, 0) to Duration.newBuilder().setSeconds(1L).build(),
        )
        for ((ghostValue, javaValue) in cases) {
            assertEquals(printer.print(javaValue), Ghost.encodeToString(ghostValue), "seconds=${ghostValue.seconds} nanos=${ghostValue.nanos}")
        }
    }

    // --- Timestamp ---

    @Test
    fun timestampMatchesReferenceImplementation() {
        val cases = listOf(
            ProtoTimestamp(1783515300L, 123456789) to Timestamp.newBuilder().setSeconds(1783515300L).setNanos(123456789).build(),
            ProtoTimestamp(0L, 0) to Timestamp.getDefaultInstance(),
            ProtoTimestamp(1783447200L, 125000000) to Timestamp.newBuilder().setSeconds(1783447200L).setNanos(125000000).build(),
        )
        for ((ghostValue, javaValue) in cases) {
            assertEquals(printer.print(javaValue), Ghost.encodeToString(ghostValue), "seconds=${ghostValue.seconds} nanos=${ghostValue.nanos}")
        }
    }

    // --- Scalar wrapper types ---

    @Test
    fun boolValueMatchesReferenceImplementation() {
        assertEquals(printer.print(BoolValue.of(true)), Ghost.encodeToString(ProtoBoolValue(true)))
        assertEquals(printer.print(BoolValue.of(false)), Ghost.encodeToString(ProtoBoolValue(false)))
    }

    @Test
    fun stringValueMatchesReferenceImplementation() {
        assertEquals(printer.print(StringValue.of("hello world")), Ghost.encodeToString(ProtoStringValue("hello world")))
        assertEquals(printer.print(StringValue.of("")), Ghost.encodeToString(ProtoStringValue("")))
    }

    @Test
    fun doubleValueMatchesReferenceImplementation() {
        assertEquals(printer.print(DoubleValue.of(42.5)), Ghost.encodeToString(ProtoDoubleValue(42.5)))
    }

    @Test
    fun floatValueMatchesReferenceImplementation() {
        assertEquals(printer.print(FloatValue.of(12.25f)), Ghost.encodeToString(ProtoFloatValue(12.25f)))
    }

    @Test
    fun int32ValueMatchesReferenceImplementation() {
        assertEquals(printer.print(Int32Value.of(123)), Ghost.encodeToString(ProtoInt32Value(123)))
        assertEquals(printer.print(Int32Value.of(Int.MIN_VALUE)), Ghost.encodeToString(ProtoInt32Value(Int.MIN_VALUE)))
    }

    @Test
    fun int64ValueMatchesReferenceImplementation() {
        assertEquals(printer.print(Int64Value.of(Long.MAX_VALUE)), Ghost.encodeToString(ProtoInt64Value(Long.MAX_VALUE)))
        assertEquals(printer.print(Int64Value.of(Long.MIN_VALUE)), Ghost.encodeToString(ProtoInt64Value(Long.MIN_VALUE)))
    }

    @Test
    fun uInt32ValueMatchesReferenceImplementation() {
        assertEquals(printer.print(UInt32Value.of(4294967295L.toInt())), Ghost.encodeToString(ProtoUInt32Value(4294967295L)))
    }

    @Test
    fun uInt64ValueMatchesReferenceImplementation() {
        // protobuf-java's UInt64Value.of takes a signed Long whose bit pattern is interpreted as
        // unsigned — Long.MAX_VALUE is within both representations, a safe cross-check value.
        assertEquals(printer.print(UInt64Value.of(Long.MAX_VALUE)), Ghost.encodeToString(ProtoUInt64Value(Long.MAX_VALUE.toULong())))
    }

    @Test
    fun bytesValueMatchesReferenceImplementation() {
        val bytes = "abc+123".encodeToByteArray()
        val flatBuffer = com.ghost.serialization.writer.FlatByteArrayWriter(64)
        val writer = com.ghost.serialization.writer.GhostJsonFlatWriter(flatBuffer)
        ProtoBytesValueSerializer.serialize(writer, ProtoBytesValue(bytes))
        assertEquals(
            printer.print(BytesValue.of(com.google.protobuf.ByteString.copyFrom(bytes))),
            flatBuffer.toStringUtf8(),
        )
    }

    // --- Struct / Value ---

    @Test
    fun structMatchesReferenceImplementation() {
        val ghostStruct: ProtoStruct = mapOf(
            "a" to ProtoValue.Null,
            "b" to ProtoValue.Number(123.45),
            "c" to ProtoValue.Str("hello"),
            "d" to ProtoValue.Bool(true),
            "e" to ProtoValue.Struct(mapOf("x" to ProtoValue.Number(1.0))),
            "f" to ProtoValue.List(listOf(ProtoValue.Number(2.0), ProtoValue.Str("y"))),
        )
        val javaStruct = Struct.newBuilder()
            .putFields("a", Value.newBuilder().setNullValueValue(0).build())
            .putFields("b", Value.newBuilder().setNumberValue(123.45).build())
            .putFields("c", Value.newBuilder().setStringValue("hello").build())
            .putFields("d", Value.newBuilder().setBoolValue(true).build())
            .putFields(
                "e",
                Value.newBuilder().setStructValue(
                    Struct.newBuilder().putFields("x", Value.newBuilder().setNumberValue(1.0).build())
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

        val flatBuffer = com.ghost.serialization.writer.FlatByteArrayWriter(512)
        val writer = com.ghost.serialization.writer.GhostJsonFlatWriter(flatBuffer)
        ProtoStructSerializer.serialize(writer, ghostStruct)
        assertEquals(printer.print(javaStruct), flatBuffer.toStringUtf8())
    }

    // --- Empty ---

    @Test
    fun emptyMatchesReferenceImplementation() {
        val flatBuffer = com.ghost.serialization.writer.FlatByteArrayWriter(16)
        val writer = com.ghost.serialization.writer.GhostJsonFlatWriter(flatBuffer)
        ProtoEmptySerializer.serialize(writer, ProtoEmpty)
        assertEquals(printer.print(com.google.protobuf.Empty.getDefaultInstance()), flatBuffer.toStringUtf8())
    }
}
