@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import com.ghost.serialization.parser.streaming.nextULong as nextStreamingULong
import com.ghost.serialization.parser.strings.nextULong as nextStringULong

/**
 * Integer types decode like kotlinx.serialization on every channel (string, bytes, streaming):
 * exponents are applied exactly, while fractions, non-integral results and out-of-range values
 * throw instead of being truncated, saturated or wrapped. Invalid JSON that kotlinx.serialization
 * tolerates (`01`, `1e`) stays rejected.
 */
class GhostIntegerDecodingTest {

    @Test
    fun byteAndShortRejectOutOfRangeValues() {
        BYTE_OUT_OF_RANGE.forEach { input ->
            assertEveryChannelFails<Byte>(input = input)
        }
        SHORT_OUT_OF_RANGE.forEach { input ->
            assertEveryChannelFails<Short>(input = input)
        }
        assertEveryChannelDecodes(
            input = BYTE_MIN_INPUT,
            expected = Byte.MIN_VALUE
        )
        assertEveryChannelDecodes(
            input = SHORT_MAX_INPUT,
            expected = Short.MAX_VALUE
        )
    }

    @Test
    fun exactExponentsDecodeAsIntegers() {
        EXACT_INT_EXPONENTS.forEach { (input, expected) ->
            assertEveryChannelDecodes(
                input = input,
                expected = expected
            )
            assertEveryChannelDecodes(
                input = input,
                expected = expected.toLong()
            )
        }
    }

    @Test
    fun fractionalValuesAreRejectedForIntegers() {
        FRACTIONAL_INPUTS.forEach { input ->
            assertEveryChannelFails<Int>(input = input)
            assertEveryChannelFails<Long>(input = input)
        }
    }

    @Test
    fun invalidJsonNumbersStayRejected() {
        INVALID_JSON_NUMBERS.forEach { input ->
            assertEveryChannelFails<Int>(input = input)
        }
    }

    @Test
    fun outOfRangeValuesAreRejectedInsteadOfSaturated() {
        INT_OUT_OF_RANGE.forEach { input ->
            assertEveryChannelFails<Int>(input = input)
        }
        LONG_OUT_OF_RANGE.forEach { input ->
            assertEveryChannelFails<Long>(input = input)
        }
    }

    @Test
    fun streamingNumberAtEndOfStreamDecodes() {
        assertEquals(
            expected = INT_AT_END,
            actual = Ghost.deserializeStreaming<Int>(Buffer().writeUtf8(string = INT_AT_END.toString()))
        )
        assertEquals(
            expected = LONG_AT_END,
            actual = Ghost.deserializeStreaming<Long>(Buffer().writeUtf8(string = LONG_AT_END.toString()))
        )
        assertEquals(
            expected = DOUBLE_AT_END,
            actual = Ghost.deserializeStreaming<Double>(Buffer().writeUtf8(string = DOUBLE_AT_END.toString()))
        )
        assertFailsWith<GhostJsonException> {
            Ghost.deserializeStreaming<Map<String, Int>>(Buffer().writeUtf8(string = TRUNCATED_OBJECT))
        }
    }

    @Test
    fun uLongDecodesFullRangeAndRejectsInvalidValues() {
        VALID_ULONGS.forEach { (input, expected) ->
            uLongReaders(input = input).forEach { read ->
                assertEquals(
                    expected = expected,
                    actual = read()
                )
            }
        }
        INVALID_ULONGS.forEach { input ->
            uLongReaders(input = input).forEach { read ->
                assertFailsWith<GhostJsonException>(message = "Accepted invalid ULong: $input") { read() }
            }
        }
    }

    private inline fun <reified T : Any> assertEveryChannelDecodes(
        input: String,
        expected: T
    ) {
        assertEquals(
            expected = expected,
            actual = Ghost.deserialize<T>(input)
        )
        assertEquals(
            expected = expected,
            actual = Ghost.deserialize<T>(input.encodeToByteArray())
        )
        assertEquals(
            expected = expected,
            actual = Ghost.deserializeStreaming<T>(Buffer().writeUtf8(string = input))
        )
    }

    private inline fun <reified T : Any> assertEveryChannelFails(
        input: String
    ) {
        assertFailsWith<GhostJsonException>(message = "String channel accepted $input") {
            Ghost.deserialize<T>(input)
        }
        assertFailsWith<GhostJsonException>(message = "Bytes channel accepted $input") {
            Ghost.deserialize<T>(input.encodeToByteArray())
        }
        assertFailsWith<GhostJsonException>(message = "Streaming channel accepted $input") {
            Ghost.deserializeStreaming<T>(Buffer().writeUtf8(string = input))
        }
    }

    private fun uLongReaders(
        input: String
    ): List<() -> ULong> = listOf(
        { GhostJsonFlatReader(rawData = input.encodeToByteArray()).nextULong() },
        { GhostJsonStringReader(rawData = input).nextStringULong() },
        { GhostJsonReader(okioSource = Buffer().writeUtf8(string = input)).nextStreamingULong() }
    )

    companion object {
        private const val BYTE_MIN_INPUT = "-128"
        private const val DOUBLE_AT_END = 1.5
        private const val INT_AT_END = 123
        private const val LONG_AT_END = -5L
        private const val SHORT_MAX_INPUT = "32767"
        private const val TRUNCATED_OBJECT = "{\"k\":123"
        private val BYTE_OUT_OF_RANGE = listOf("128", "-129", "300")
        private val EXACT_INT_EXPONENTS = listOf(
            "1e2" to 100,
            "1E+2" to 100,
            "100e-2" to 1,
            "-1e2" to -100,
            "0e5" to 0,
            "-0" to 0,
            "21474836470e-1" to Int.MAX_VALUE
        )
        private val FRACTIONAL_INPUTS = listOf("1.5", "-1.5", "1.0", "0.5", "0.0", "1e-2", "15e-1", "2.5e0")
        private val INT_OUT_OF_RANGE = listOf("2147483648", "-2147483649", "1e10")
        private val INVALID_JSON_NUMBERS = listOf("01", "00", "1e", "1e+", "-", "e5")
        private val INVALID_ULONGS = listOf("-1", "18446744073709551616", "1.5", "\"abc\"", "\"-1\"")
        private val LONG_OUT_OF_RANGE = listOf("9223372036854775808", "-9223372036854775809", "1e19")
        private val SHORT_OUT_OF_RANGE = listOf("32768", "-32769", "70000")
        private val VALID_ULONGS = listOf(
            "18446744073709551615" to ULong.MAX_VALUE,
            "\"18446744073709551615\"" to ULong.MAX_VALUE,
            "9223372036854775808" to 9223372036854775808uL,
            "1e19" to 10000000000000000000uL,
            "-0" to 0uL
        )
    }
}
