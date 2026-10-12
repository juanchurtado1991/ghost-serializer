@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.json.JsonReaderOptions
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.parser.yaml.readDocument
import com.ghost.serialization.yaml.exception.GhostYamlException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * YAML counterpart of `GhostIntegerDecodingTest`: integer reads reject fractions and out-of-range
 * values instead of truncating or wrapping them, a float scalar with no fractional part is still
 * accepted, and a scalar that does not match its explicit `!!int`/`!!float`/`!!bool` tag fails
 * instead of becoming `0`/`false`.
 */
class GhostYamlIntegerDecodingTest {

    @Test
    fun byteAndShortRejectOutOfRangeValues() {
        assertFailsWith<GhostYamlException> { readerAtValue(value = BYTE_OUT_OF_RANGE).nextByte() }
        assertFailsWith<GhostYamlException> { readerAtValue(value = SHORT_OUT_OF_RANGE).nextShort() }
    }

    @Test
    fun intRejectsFractionAndOverflow() {
        INT_REJECTED.forEach { value ->
            assertFailsWith<GhostYamlException>(message = "Accepted $value") { readerAtValue(value = value).nextInt() }
        }
    }

    @Test
    fun intAcceptsWholeFloatScalars() {
        WHOLE_FLOAT_SCALARS.forEach { (value, expected) ->
            assertEquals(
                expected = expected,
                actual = readerAtValue(value = value).nextInt()
            )
        }
    }

    @Test
    fun mismatchedExplicitTagsFail() {
        MISMATCHED_TAGS.forEach { yaml ->
            assertFailsWith<GhostYamlException>(message = "Accepted $yaml") {
                GhostYamlFlatReader(rawData = yaml.encodeToByteArray()).readDocument()
            }
        }
    }

    @Test
    fun uLongRejectsNegativeValues() {
        assertFailsWith<GhostYamlException> { readerAtValue(value = NEGATIVE_VALUE).nextULong() }
        assertEquals(
            expected = ULong.MAX_VALUE,
            actual = readerAtValue(value = ULONG_MAX_QUOTED).nextULong()
        )
    }

    private fun readerAtValue(
        value: String
    ): GhostYamlFlatReader {
        val reader = GhostYamlFlatReader(rawData = "$KEY: $value".encodeToByteArray())
        reader.beginObject()
        reader.selectNameAndConsume(options = JsonReaderOptions.of(KEY))
        return reader
    }

    companion object {
        private const val BYTE_OUT_OF_RANGE = "300"
        private const val KEY = "v"
        private const val NEGATIVE_VALUE = "-1"
        private const val SHORT_OUT_OF_RANGE = "70000"
        private const val ULONG_MAX_QUOTED = "\"18446744073709551615\""
        private val INT_REJECTED = listOf("1.5", "3000000000", "-2147483649", "abc", "\"12x\"")
        private val MISMATCHED_TAGS = listOf("v: !!int abc", "v: !!int 1.5", "v: !!float abc", "v: !!bool xyz")
        private val WHOLE_FLOAT_SCALARS = listOf("1.0" to 1, "1e2" to 100)
    }
}
