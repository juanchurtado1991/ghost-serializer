package com.ghost.serialization

import com.ghost.serialization.parser.common.parseDecimalLiteralToDouble
import com.ghost.serialization.parser.common.parseDecimalLiteralToFloat
import okio.Buffer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * `Double`/`Float` decode exactly like a correctly rounded parse on every channel, and encoded
 * values read back bit for bit. Covers the cases the fast paths used to get wrong: leading
 * fraction zeros eating the 17-digit budget, Float scaling done in Float arithmetic, subnormals,
 * literals longer than 17 digits, values exactly halfway between two Floats, and fractions the
 * fixed-decimal writer rounded to `.0`. A literal that overflows the target type is rejected, so
 * its expected decoded value is `null`.
 *
 * The random literals are checked against [parseDecimalLiteralToDouble]/[parseDecimalLiteralToFloat]
 * (the platform `toDouble()`/`toFloat()` outside Wasm, whose own `toDouble()` is not correctly
 * rounded). The hard literals carry expected bits computed in exact rational arithmetic, so the
 * Wasm tie-breaking path is checked independently: Doubles halfway between two Floats, the Float
 * overflow threshold, and the subnormal Float boundary.
 */
class GhostFloatingPointDecodingTest {

    @Test
    fun doubleAndFloatDecodeLikeCorrectlyRoundedParsing() {
        val random = Random(seed = RANDOM_SEED)
        val randomLiterals = List(size = RANDOM_LITERAL_COUNT) { randomLiteral(random = random) }

        (EDGE_LITERALS + randomLiterals).forEach { literal ->
            assertEveryChannelDecodesBits(
                literal = literal,
                expectedDouble = parseDecimalLiteralToDouble(literal = literal),
                expectedFloat = parseDecimalLiteralToFloat(literal = literal)
            )
        }
    }

    @Test
    fun encodedDoubleAndFloatReadBackBitForBit() {
        ROUND_TRIP_DOUBLES.forEach { value ->
            assertEquals(
                expected = value.toRawBits(),
                actual = Ghost.deserialize<Double>(Ghost.encodeToString(value = value)).toRawBits(),
                message = "Double $value"
            )
        }
        ROUND_TRIP_FLOATS.forEach { value ->
            assertEquals(
                expected = value.toRawBits(),
                actual = Ghost.deserialize<Float>(Ghost.encodeToString(value = value)).toRawBits(),
                message = "Float $value"
            )
        }
    }

    @Test
    fun hardLiteralsDecodeToExactBits() {
        HARD_DOUBLE_BITS.forEach { (literal, expectedBits) ->
            assertEveryChannelDecodesBits(
                literal = literal,
                expectedDouble = Double.fromBits(bits = expectedBits),
                expectedFloat = parseDecimalLiteralToFloat(literal = literal)
            )
        }
        HARD_FLOAT_BITS.forEach { (literal, expectedBits) ->
            assertEveryChannelDecodesBits(
                literal = literal,
                expectedDouble = parseDecimalLiteralToDouble(literal = literal),
                expectedFloat = Float.fromBits(bits = expectedBits)
            )
        }
    }

    private fun assertEveryChannelDecodesBits(
        literal: String,
        expectedDouble: Double,
        expectedFloat: Float
    ) {
        val doubles = listOf(
            runCatching { Ghost.deserialize<Double>(literal) },
            runCatching { Ghost.deserialize<Double>(literal.encodeToByteArray()) },
            runCatching { Ghost.deserializeStreaming<Double>(Buffer().writeUtf8(string = literal)) }
        )
        val floats = listOf(
            runCatching { Ghost.deserialize<Float>(literal) },
            runCatching { Ghost.deserialize<Float>(literal.encodeToByteArray()) },
            runCatching { Ghost.deserializeStreaming<Float>(Buffer().writeUtf8(string = literal)) }
        )
        doubles.forEach { decoded ->
            assertEquals(
                expected = expectedDouble.takeIf { it.isFinite() }?.toRawBits(),
                actual = decoded.getOrNull()?.toRawBits(),
                message = "Double $literal"
            )
        }
        floats.forEach { decoded ->
            assertEquals(
                expected = expectedFloat.takeIf { it.isFinite() }?.toRawBits(),
                actual = decoded.getOrNull()?.toRawBits(),
                message = "Float $literal"
            )
        }
    }

    private fun randomLiteral(
        random: Random
    ): String {
        val digits = (1..random.nextInt(from = 1, until = MAX_RANDOM_DIGITS))
            .joinToString(separator = "") { random.nextInt(until = DIGIT_BOUND).toString() }
            .trimStart('0')
            .ifEmpty { "0" }
        val integerLength = random.nextInt(from = 1, until = digits.length + 1)
        val fraction = digits.drop(n = integerLength).let { if (it.isEmpty()) "" else ".$it" }
        val exponent = random.nextInt(from = MIN_RANDOM_EXPONENT, until = MAX_RANDOM_EXPONENT)
        return digits.take(n = integerLength) + fraction + "e" + exponent
    }

    companion object {
        private const val DIGIT_BOUND = 10
        private const val MAX_RANDOM_DIGITS = 26
        private const val MAX_RANDOM_EXPONENT = 30
        private const val MIN_RANDOM_EXPONENT = -60
        private const val RANDOM_LITERAL_COUNT = 2000
        private const val RANDOM_SEED = 7
        private val EDGE_LITERALS = listOf(
            "0.023243451316288046",
            "-0.0014731311026394962",
            "-7.656424E-22",
            "4.422613E-10",
            "1.4E-45",
            "1.17549435E-38",
            "3.4028235E38",
            "4.9e-324",
            "2.2250738585072011e-308",
            "0.1000000000000000055511151231257827",
            "123456789012345678901234567890",
            "1.000000059604644775390625",
            "0.30000000000000004",
            "9007199254740993"
        )
        private val HARD_DOUBLE_BITS = listOf(
            "64.90e-5" to 4559125191723490205L,
            "9007199254740993" to 4845873199050653696L,
            "2.2250738585072012e-308" to 4503599627370496L,
            "1.00000000000000011102230246251565404236316680908203125" to 4607182418800017408L,
            "1.00000000000000011102230246251565404236316680908203126" to 4607182418800017409L,
            "1e23" to 4950912855330343670L,
            "8.98846567431158e307" to 9214364837600034816L
        )
        private val HARD_FLOAT_BITS = listOf(
            "1.000000059604644775390625" to 0x3F800000,
            "1.00000005960464477539062500001" to 0x3F800001,
            "1.000000178813934326171874999" to 0x3F800001,
            "340282356779733661637539395458142568447" to 0x7F7FFFFF,
            "340282356779733661637539395458142568448" to 0x7F800000,
            "7.006492321624085354618647916449580656401309709382578858785341419448955413429303e-46" to 0,
            "7.0064923216240853546186479164495806564013097093825788587853414194489554134293031e-46" to 1
        )
        private val ROUND_TRIP_DOUBLES = listOf(1.0000000004, 0.9999999996, 3.2e-8, 1e-9, 123456.0000000001)
        private val ROUND_TRIP_FLOATS = listOf(3.23844E-8f, -6.2243353E-9f, 1.0000001f, 0.9999999f, 1.4E-45f)
    }
}
