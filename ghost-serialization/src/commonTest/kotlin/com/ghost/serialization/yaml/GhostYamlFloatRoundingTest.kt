package com.ghost.serialization.yaml

import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import kotlin.test.Test
import kotlin.test.assertEquals

/** `nextFloat` rounds the scalar text once; going through `Double` first rounded twice on float midpoints. */
class GhostYamlFloatRoundingTest {

    private fun floatOf(yaml: String): Float = GhostYamlFlatReader(rawData = yaml.encodeToByteArray()).nextFloat()

    private fun doubleOf(yaml: String): Double = GhostYamlFlatReader(rawData = yaml.encodeToByteArray()).nextDouble()

    @Test
    fun aboveFloatMidpointRoundsUp() {
        assertEquals(
            expected = 16777218f,
            actual = floatOf(yaml = "16777217.0000000000001")
        )
    }

    @Test
    fun belowFloatMidpointRoundsDown() {
        assertEquals(
            expected = 16777216f,
            actual = floatOf(yaml = "16777216.9999999999999")
        )
    }

    @Test
    fun exactMidpointRoundsToEven() {
        assertEquals(
            expected = 16777216f,
            actual = floatOf(yaml = "16777217.0")
        )
    }

    @Test
    fun largeIntegerRoundsOnceToFloat() {
        assertEquals(
            expected = 9007199254740993L.toFloat(),
            actual = floatOf(yaml = "9007199254740993")
        )
    }

    @Test
    fun doubleReadStillSeesTheSameValue() {
        assertEquals(
            expected = 16777217.0,
            actual = doubleOf(yaml = "16777217.0000000000001")
        )
    }
}
