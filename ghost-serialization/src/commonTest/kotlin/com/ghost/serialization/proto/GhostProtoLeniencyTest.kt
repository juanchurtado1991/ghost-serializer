@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextFloat
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextLong
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * Documents intentional divergence between [Ghost] (plain JSON reader) and
 * [GhostProto] (proto3 JSON reader) on the same wire payloads.
 */
class GhostProtoLeniencyTest {

    @BeforeTest
    fun setup() {
        registerProtoTestFixtures()
    }

    // ── int32: quoted-or-bare ─────────────────────────────────────────

    @Test
    fun plainFlatReaderRejectsQuotedInt32() {
        val reader = GhostJsonReader("""{"retries":"42"}""".encodeToByteArray())
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextInt() }
    }

    @Test
    fun protoFlatReaderAcceptsQuotedInt32() {
        val reader = protoReaderOf(json = """{"retries":"42"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = reader.nextInt()
        )
        reader.endObject()
    }

    @Test
    fun ghostDeserializeRejectsBareInt32WhenFieldIsQuotedOnlyOnWire() {
        // Ghost path uses GhostJsonReader — bare int32 still works for plain JSON models.
        val reader = GhostJsonReader("""{"retries":42}""".encodeToByteArray())
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = 42,
            actual = reader.nextInt()
        )
        reader.endObject()
    }

    // ── int64: quoted-or-bare on entry points ─────────────────────────

    @Test
    fun ghostDeserializeRejectsQuotedInt64OnPlainFlatReader() {
        val reader = GhostJsonReader("""{"deviceId":"42"}""".encodeToByteArray())
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextLong() }
    }

    @Test
    fun ghostProtoDeserializeAcceptsQuotedInt64() {
        val parsed =
            GhostProto.deserialize<ProtoEntryPointDevice>("""{"deviceId":"42","label":"x"}""")
        assertEquals(
            expected = 42L,
            actual = parsed.deviceId
        )
    }

    @Test
    fun ghostProtoDeserializeAcceptsBareInt64() {
        val parsed =
            GhostProto.deserialize<ProtoEntryPointDevice>("""{"deviceId":42,"label":"x"}""")
        assertEquals(
            expected = 42L,
            actual = parsed.deviceId
        )
    }

    @Test
    fun ghostEntryPointAcceptsBareInt64ForHandWrittenSerializer() {
        val parsed =
            Ghost.deserialize<ProtoEntryPointDevice>("""{"deviceId":42,"label":"x"}""".encodeToByteArray())
        assertEquals(
            expected = 42L,
            actual = parsed.deviceId
        )
    }

    @Test
    fun ghostEntryPointRejectsQuotedInt64ForHandWrittenSerializer() {
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<ProtoEntryPointDevice>("""{"deviceId":"42","label":"x"}""".encodeToByteArray())
        }
    }

    // ── float/double: NaN & Infinity ──────────────────────────────────

    @Test
    fun plainFlatReaderRejectsQuotedNaN() {
        val reader = GhostJsonReader("""{"v":"NaN"}""".encodeToByteArray())
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { reader.nextFloat() }
    }

    @Test
    fun protoFlatReaderAcceptsQuotedNaN() {
        val reader = protoReaderOf(json = """{"v":"NaN"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertTrue(actual = reader.nextFloat().isNaN())
        reader.endObject()
    }

    @Test
    fun protoFlatReaderAcceptsQuotedPositiveInfinity() {
        val reader = protoReaderOf(json = """{"v":"Infinity"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = Float.POSITIVE_INFINITY,
            actual = reader.nextFloat()
        )
        reader.endObject()
    }

    @Test
    fun protoFlatReaderAcceptsQuotedNegativeInfinityAsDouble() {
        val reader = protoReaderOf(json = """{"v":"-Infinity"}""")
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        assertEquals(
            expected = Double.NEGATIVE_INFINITY,
            actual = reader.nextDouble()
        )
        reader.endObject()
    }

    // ── fractional int32 rejection (proto-only) ───────────────────────

    @Test
    fun protoFlatReaderRejectsFractionalInt32() {
        val protoReader = protoReaderOf(json = """{"retries":1.5}""")
        protoReader.beginObject()
        protoReader.nextKey()
        protoReader.consumeKeySeparator()
        assertFailsWith<GhostJsonException> { protoReader.nextInt() }
    }

    @Test
    fun plainReaderRejectsBareFractionalInt() {
        val plainReader = GhostJsonReader("1.5".encodeToByteArray())
        assertFailsWith<GhostJsonException> { plainReader.nextInt() }
    }

    @Test
    fun ghostProtoDeserializeAcceptsTrailingWhitespace() {
        val parsed = GhostProto.deserialize<ProtoEntryPointDevice>("{\"deviceId\":42,\"label\":\"x\"} \n")
        assertEquals(
            expected = 42L,
            actual = parsed.deviceId
        )
    }

    @Test
    fun ghostProtoDeserializeRejectsTrailingContent() {
        assertFailsWith<GhostJsonException> {
            GhostProto.deserialize<ProtoEntryPointDevice>("""{"deviceId":42,"label":"x"} extra""")
        }
        assertFailsWith<GhostJsonException> {
            GhostProto.deserialize<ProtoEntryPointDevice>("""{"deviceId":42,"label":"x"} extra""".encodeToByteArray())
        }
    }

    @Test
    fun ghostProtoInt64AcceptsZeroFractionAndRejectsNonzeroFraction() {
        assertEquals(
            expected = 42L,
            actual = GhostProto.deserialize<ProtoEntryPointDevice>("""{"deviceId":42.0,"label":"x"}""").deviceId
        )
        assertFailsWith<GhostJsonException> {
            GhostProto.deserialize<ProtoEntryPointDevice>("""{"deviceId":42.5,"label":"x"}""")
        }
    }

    @Test
    fun ghostProtoInt64BareNumberIsExactBeyondDoublePrecision() {
        assertEquals(
            expected = 9007199254740993L,
            actual = GhostProto.deserialize<ProtoEntryPointDevice>("""{"deviceId":9007199254740993,"label":"x"}""").deviceId
        )
    }
}
