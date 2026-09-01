@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.serializers.BooleanArraySerializer
import com.ghost.serialization.serializers.DoubleArraySerializer
import com.ghost.serialization.serializers.FloatArraySerializer
import kotlin.test.Test
import kotlin.test.assertContentEquals

/**
 * Covers the compact-array fast path in GhostFastPrimitiveArrayHelpers.kt
 * (tryFastDecimalArrayCore / tryFastBooleanArrayCore) for Double/Float/Boolean arrays across
 * all three reader channels, plus every condition under which it must bail cleanly to the
 * general element-by-element loop.
 */
class DecimalAndBooleanArrayFastPathTest {

    @Test
    fun testDoubleArrayFastPathAllChannelsAgree() {
        val json = "[1.5,-2.25,0.0,42,-999999.999,1.5e10,-2E-5]"
        // Compare against the general loop's own parsing (forced via a leading-space element
        // that the fast path can't match) rather than a hardcoded literal — the fast path
        // delegates numeric conversion to the exact same nextDouble(), so this only needs to
        // prove the two code paths agree with each other, not with an independently-rounded
        // Kotlin double literal.
        val slowPathJson = "[1.5, -2.25, 0.0, 42, -999999.999, 1.5e10, -2E-5]"
        val expected = DoubleArraySerializer.deserialize(GhostJsonFlatReader(slowPathJson.encodeToByteArray()))
        assertContentEquals(expected, DoubleArraySerializer.deserialize(GhostJsonFlatReader(json.encodeToByteArray())))
        assertContentEquals(expected, DoubleArraySerializer.deserialize(GhostJsonReader(json.encodeToByteArray())))
        assertContentEquals(expected, DoubleArraySerializer.deserialize(GhostJsonStringReader(json)))
    }

    @Test
    fun testDoubleArrayFastPathLargeCompactArrayMatchesSlowPath() {
        val values = DoubleArray(1000) { it * 0.5 - 250.25 }
        val json = values.joinToString(",", "[", "]")
        val spacedJson = values.joinToString(", ", "[", "]")
        val expected = DoubleArraySerializer.deserialize(GhostJsonFlatReader(spacedJson.encodeToByteArray()))
        assertContentEquals(expected, DoubleArraySerializer.deserialize(GhostJsonFlatReader(json.encodeToByteArray())))
        assertContentEquals(expected, DoubleArraySerializer.deserialize(GhostJsonReader(json.encodeToByteArray())))
        assertContentEquals(expected, DoubleArraySerializer.deserialize(GhostJsonStringReader(json)))
    }

    @Test
    fun testDoubleArrayFastPathBailsOnWhitespaceAndPartialListIsDiscarded() {
        // The first two elements would be consumed by the fast path before the embedded
        // whitespace forces a bail; the slow-path retry must not see those elements twice.
        val json = "[1.5,2.5, 3.5]"
        assertContentEquals(
            doubleArrayOf(1.5, 2.5, 3.5),
            DoubleArraySerializer.deserialize(GhostJsonFlatReader(json.encodeToByteArray()))
        )
    }

    @Test
    fun testDoubleArrayFastPathBailsOnQuotedStrings() {
        // A quoted value isn't a bare-number fast-path match — must fall back to the general
        // loop's own string handling (whatever it does: coercion when enabled, error otherwise).
        val reader = GhostJsonFlatReader("[1.0,\"2.5\",3.0]".encodeToByteArray())
            .also { it.coerceStringsToNumbers = true }
        assertContentEquals(doubleArrayOf(1.0, 2.5, 3.0), DoubleArraySerializer.deserialize(reader))
    }

    @Test
    fun testFloatArrayFastPathAllChannelsAgree() {
        val json = "[1.5,-2.25,0.0,42,-999.999,1.5e5,-2E-3]"
        val slowPathJson = "[1.5, -2.25, 0.0, 42, -999.999, 1.5e5, -2E-3]"
        val expected = FloatArraySerializer.deserialize(GhostJsonFlatReader(slowPathJson.encodeToByteArray()))
        assertContentEquals(expected, FloatArraySerializer.deserialize(GhostJsonFlatReader(json.encodeToByteArray())))
        assertContentEquals(expected, FloatArraySerializer.deserialize(GhostJsonReader(json.encodeToByteArray())))
        assertContentEquals(expected, FloatArraySerializer.deserialize(GhostJsonStringReader(json)))
    }

    @Test
    fun testFloatArrayFastPathLargeCompactArrayMatchesSlowPath() {
        val values = FloatArray(1000) { it * 0.25f - 125.125f }
        val json = values.joinToString(",", "[", "]")
        val spacedJson = values.joinToString(", ", "[", "]")
        val expected = FloatArraySerializer.deserialize(GhostJsonFlatReader(spacedJson.encodeToByteArray()))
        assertContentEquals(expected, FloatArraySerializer.deserialize(GhostJsonFlatReader(json.encodeToByteArray())))
        assertContentEquals(expected, FloatArraySerializer.deserialize(GhostJsonReader(json.encodeToByteArray())))
        assertContentEquals(expected, FloatArraySerializer.deserialize(GhostJsonStringReader(json)))
    }

    @Test
    fun testBooleanArrayFastPathAllChannelsAgree() {
        val json = "[true,false,true,true,false]"
        val expected = booleanArrayOf(true, false, true, true, false)
        assertContentEquals(expected, BooleanArraySerializer.deserialize(GhostJsonFlatReader(json.encodeToByteArray())))
        assertContentEquals(expected, BooleanArraySerializer.deserialize(GhostJsonReader(json.encodeToByteArray())))
        assertContentEquals(expected, BooleanArraySerializer.deserialize(GhostJsonStringReader(json)))
    }

    @Test
    fun testBooleanArrayFastPathLargeCompactArrayMatchesSlowPath() {
        val values = BooleanArray(1000) { it % 3 == 0 }
        val json = values.joinToString(",", "[", "]")
        assertContentEquals(values, BooleanArraySerializer.deserialize(GhostJsonFlatReader(json.encodeToByteArray())))
        assertContentEquals(values, BooleanArraySerializer.deserialize(GhostJsonReader(json.encodeToByteArray())))
        assertContentEquals(values, BooleanArraySerializer.deserialize(GhostJsonStringReader(json)))
    }

    @Test
    fun testBooleanArrayFastPathBailsOnWhitespaceAndCoercedValues() {
        assertContentEquals(
            booleanArrayOf(true, false, true),
            BooleanArraySerializer.deserialize(GhostJsonFlatReader("[true, false, true]".encodeToByteArray()))
        )
        // Coerced boolean values (bare 1/0) are not "true"/"false" literals — must fall back
        // to the general loop, which only honors coercion when coerceBooleans is enabled.
        val reader = GhostJsonFlatReader("[1,0,1]".encodeToByteArray()).also { it.coerceBooleans = true }
        assertContentEquals(booleanArrayOf(true, false, true), BooleanArraySerializer.deserialize(reader))
    }
}
