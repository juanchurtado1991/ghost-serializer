package com.ghost.serialization

import com.ghost.serialization.parser.common.json.NO_RESULT
import com.ghost.serialization.parser.common.json.eiselLemireToDoubleBits
import okio.Buffer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Bit-for-bit checks of the Eisel–Lemire double path against the JVM's own `String.toDouble()`.
 * The edge cases target what the differential fuzz rarely generates: the largest double and the first
 * overflow, the subnormal boundary and its halfway point, exact ties at 2^53, 19-digit mantissas and
 * literals longer than the mantissa holds. Every edge case goes through the three reader channels.
 */
class GhostEiselLemireTest {

    @Test
    fun edgeCasesParseLikeTheJvmOnEveryChannel() {
        val failures = ArrayList<String>()
        for (literal in EDGE_CASES) {
            val expected = literal.toDouble()
            val text = "[$literal]"
            val channels = listOf(
                runCatching { Ghost.deserialize<List<Double>>(json = text)[0] },
                runCatching { Ghost.deserialize<List<Double>>(bytes = text.encodeToByteArray())[0] },
                runCatching { Ghost.deserializeStreaming<List<Double>>(source = Buffer().writeUtf8(text))[0] },
            )
            channels.forEachIndexed { channel, result ->
                val matches = if (expected.isInfinite()) {
                    result.isFailure
                } else {
                    result.getOrNull()?.toRawBits() == expected.toRawBits()
                }
                if (!matches) {
                    failures.add("$literal channel=$channel expected=$expected got=$result")
                }
            }
        }
        assertEquals(
            expected = emptyList(),
            actual = failures
        )
    }

    @Test
    fun randomMantissasAndExponentsMatchTheJvm() {
        val random = Random(seed = RANDOM_SEED)
        val failures = ArrayList<String>()
        repeat(times = RANDOM_ITERATIONS) {
            val mantissa = randomMantissa(random = random)
            val exponent = random.nextInt(
                from = MIN_RANDOM_EXPONENT,
                until = MAX_RANDOM_EXPONENT
            )
            val bits = eiselLemireToDoubleBits(
                mantissa = mantissa,
                exponent = exponent
            )
            val expectedBits = "${mantissa}e$exponent".toDouble().toRawBits()
            val isWrong = bits != NO_RESULT && bits != expectedBits
            if (isWrong && failures.size < MAX_REPORTED_FAILURES) {
                failures.add("${mantissa}e$exponent expected=$expectedBits got=$bits")
            }
        }
        assertEquals(
            expected = emptyList(),
            actual = failures
        )
    }

    private fun randomMantissa(
        random: Random
    ): Long = when (random.nextInt(until = MANTISSA_SHAPES)) {
        0 -> random.nextLong(
            from = 1,
            until = Long.MAX_VALUE
        )
        1 -> random.nextLong(
            from = SEVENTEEN_DIGITS_MIN,
            until = SEVENTEEN_DIGITS_LIMIT
        )
        else -> random.nextLong(
            from = 1,
            until = SMALL_MANTISSA_LIMIT
        )
    }

    private companion object {
        const val MANTISSA_SHAPES = 3
        const val MAX_RANDOM_EXPONENT = 350
        const val MAX_REPORTED_FAILURES = 10
        const val MIN_RANDOM_EXPONENT = -350
        const val RANDOM_ITERATIONS = 500_000
        const val RANDOM_SEED = 20261020
        const val SEVENTEEN_DIGITS_LIMIT = 100_000_000_000_000_000L
        const val SEVENTEEN_DIGITS_MIN = 10_000_000_000_000_000L
        const val SMALL_MANTISSA_LIMIT = 10_000_000L

        val EDGE_CASES = listOf(
            "-0.0",
            "-65.613616999999977",
            "0.1",
            "0.30000000000000004",
            "0e400",
            "1.00000000000000011102230246251565404236316680908203125",
            "1.448997445238699",
            "1.7976931348623157e308",
            "1.7976931348623158e308",
            "100000000000000000000000e-23",
            "123456789012345678e-20",
            "123e-345",
            "18014398509481985",
            "1e-22",
            "1e-308",
            "1e-400",
            "1e22",
            "1e23",
            "1e308",
            "2.2250738585072011e-308",
            "2.2250738585072014e-308",
            "2.4703282292062327e-324",
            "2.4703282292062328e-324",
            "2.9802322387695312e-8",
            "3.141592653589793238462643383279",
            "4.9e-324",
            "4503599627370497.5",
            "5e-324",
            "7.2057594037927933e16",
            "8.98846567431158e307",
            "9007199254740992",
            "9007199254740993",
            "9007199254740995",
            "9223372036854775807e-10",
        )
    }
}
