package com.ghost.serialization.proto.wkt

import com.ghost.serialization.Ghost
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.proto.GhostProto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * proto3 JSON edge cases of the well-known types, checked against what protobuf-java's
 * `JsonFormat` prints and parses: non-finite `double`/`float` as `"NaN"`/`"Infinity"`, `float`
 * formatted as a `float`, unsigned range checks, `Duration` grammar and range, at most nine
 * fractional digits, and `FieldMask` path conversion. Malformed values throw [GhostJsonException].
 */
class ProtoWktEdgeCaseTest {

    @Test
    fun durationFollowsTheProto3Grammar() {
        assertEquals(
            expected = ProtoDuration(seconds = 1, nanos = 0),
            actual = GhostProto.deserialize<ProtoDuration>(json = "\"+1s\"")
        )
        assertEquals(
            expected = ProtoDuration(seconds = -315_576_000_000, nanos = -999_999_999),
            actual = GhostProto.deserialize<ProtoDuration>(json = "\"-315576000000.999999999s\"")
        )
        INVALID_DURATIONS.forEach { json ->
            assertFailsWith<GhostJsonException>(message = json) {
                GhostProto.deserialize<ProtoDuration>(json = json)
            }
        }
    }

    @Test
    fun fieldMaskSkipsEmptyPathsAndLowersLeadingCapitals() {
        assertEquals(
            expected = ProtoFieldMask(paths = listOf("a", "b")),
            actual = GhostProto.deserialize<ProtoFieldMask>(json = "\"a,,b\"")
        )
        assertEquals(
            expected = ProtoFieldMask(paths = listOf("a", "foo_bar.baz")),
            actual = GhostProto.deserialize<ProtoFieldMask>(json = "\"A,fooBar.Baz\"")
        )
    }

    @Test
    fun floatIsWrittenWithFloatPrecision() {
        assertEquals(
            expected = "1.1",
            actual = Ghost.encodeToString(value = ProtoFloatValue(value = 1.1f))
        )
    }

    @Test
    fun nonFiniteFloatingValuesAreQuotedNames() {
        NON_FINITE_DOUBLES.forEach { (value, json) ->
            assertEquals(
                expected = json,
                actual = Ghost.encodeToString(value = ProtoDoubleValue(value = value))
            )
            assertEquals(
                expected = value,
                actual = GhostProto.deserialize<ProtoDoubleValue>(json = json).value
            )
            assertEquals(
                expected = json,
                actual = Ghost.encodeToString(value = ProtoFloatValue(value = value.toFloat()))
            )
        }
    }

    @Test
    fun timestampRejectsMoreThanNineFractionalDigits() {
        assertFailsWith<GhostJsonException> {
            GhostProto.deserialize<ProtoTimestamp>(json = "\"1970-01-01T00:00:00.1234567891Z\"")
        }
        assertFailsWith<GhostJsonException> {
            GhostProto.deserialize<ProtoTimestamp>(json = "\"1970-01-01 00:00:00Z\"")
        }
    }

    @Test
    fun unsignedWrappersCheckTheirRange() {
        assertEquals(
            expected = 7uL,
            actual = GhostProto.deserialize<ProtoUInt64Value>(json = "7").value
        )
        listOf("-1", "4294967296").forEach { json ->
            assertFailsWith<GhostJsonException>(message = json) {
                GhostProto.deserialize<ProtoUInt32Value>(json = json)
            }
        }
        assertEquals(
            expected = UINT32_MAX,
            actual = GhostProto.deserialize<ProtoUInt32Value>(json = "4294967295").value
        )
    }

    companion object {
        private const val UINT32_MAX = 4_294_967_295L
        private val INVALID_DURATIONS = listOf(
            "\"1e3s\"",
            "\".5s\"",
            "\" 1s\"",
            "\"315576000001s\"",
            "\"1.0000000001s\"",
            "\"1\"",
            "\"1S\"",
            "\"s\""
        )
        private val NON_FINITE_DOUBLES = listOf(
            Double.NaN to "\"NaN\"",
            Double.POSITIVE_INFINITY to "\"Infinity\"",
            Double.NEGATIVE_INFINITY to "\"-Infinity\""
        )
    }
}
