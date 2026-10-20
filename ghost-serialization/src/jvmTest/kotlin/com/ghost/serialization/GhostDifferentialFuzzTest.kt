package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.common.json.JsonEncodingKind
import com.ghost.serialization.parser.common.json.detectJsonByteEncoding
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import okio.Buffer
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Differential fuzzing of the JSON readers against kotlinx.serialization and the JVM's own number parsing.
 * `GHOST_FUZZ_SEED` and `GHOST_FUZZ_ITERATIONS` size the run (the scheduled workflow uses a fresh seed and many
 * more iterations); the defaults keep a normal `jvmTest` quick and reproducible.
 *
 * Asserted, per generated or mutated input:
 * - the string, bytes and streaming channels agree, in lenient and in strict mode;
 * - strict mode never accepts what kotlinx rejects, and equal inputs decode to equal values;
 * - a mutated input fails only with [GhostJsonException];
 * - strings round-trip, and decimals parse bit-for-bit like `Double.parseDouble` / `Float.parseFloat`.
 *
 * Not asserted by design: Ghost rejecting invalid JSON that kotlinx tolerates (`01`, `+1`, `.5`, raw tabs in
 * strings) and lenient mode accepting missing commas between object members.
 *
 * Structural documents whose UTF-8 bytes read as BOM-less UTF-16/32 (e.g. `\u0000[\u0000]` is `00 5B 00 5D`, UTF-16BE
 * `[]`) are skipped: the byte channels decode them per RFC 8259 §8.1 while the string channel and kotlinx see raw
 * NULs, so the channels legitimately disagree.
 */
class GhostDifferentialFuzzTest {

    private val seed = System.getenv("GHOST_FUZZ_SEED")?.toLongOrNull() ?: DEFAULT_SEED
    private val iterations = System.getenv("GHOST_FUZZ_ITERATIONS")?.toIntOrNull() ?: DEFAULT_ITERATIONS
    private val rnd = Random(seed)
    private val failures = ArrayList<String>()

    private fun fail(category: String, detail: String) {
        if (failures.size < MAX_REPORTED) failures.add("[$category] seed=$seed $detail")
    }

    private fun assertNoFailures() = assertEquals(expected = emptyList(), actual = failures)

    // ---- structural documents -------------------------------------------------------------------------------

    private val atoms = listOf(
        "0", "1", "-1", "12", "-0", "0.5", "-0.5", "1e2", "1E-2", "1.5e+3", "123456789012", "9223372036854775807",
        "9223372036854775808", "-9223372036854775808", "2147483648", "3.14159", "1e400", "0e0", "00", "01", "1.", ".5",
        "+1", "1e", "-", "true", "false", "null", "\"\"", "\"a\"", "\"a\\nb\"", "\"\\u0041\"", "\"\\ud83d\\ude00\"",
        "\"\\ud800\"", "\"\\udc00x\"", "\"é\"", "\"😀\"", "\"\\/\"", "\"a\\\"b\"", "\"\\u00e9\"", "\"\\x\"",
        "\"tab\there\"", "[]", "[1]", "[1,2]", "{}", "{\"a\":1}", "{\"a\":1,\"b\":2}", "{\"a\":1,\"a\":2}", "\"\\u0000\"",
        " ", "\n", "\t"
    )
    private val junk = "{}[],:\"\\ -+.eE0123456789tfnul\u0000\u00e9"

    private fun atom() = atoms[rnd.nextInt(atoms.size)]

    private fun generate(): String {
        val sb = StringBuilder()
        when (rnd.nextInt(4)) {
            0 -> sb.append(atom())
            1 -> {
                sb.append('[')
                repeat(rnd.nextInt(5)) { if (it > 0) sb.append(','); sb.append(atom()) }
                sb.append(']')
            }
            2 -> {
                sb.append('{')
                repeat(rnd.nextInt(4)) {
                    if (it > 0) sb.append(',')
                    sb.append('"').append(listOf("a", "b", "", "é", "\\u0061", "k\\n").random(rnd)).append("\":").append(atom())
                }
                sb.append('}')
            }
            else -> {
                sb.append('[')
                repeat(rnd.nextInt(4)) { if (it > 0) sb.append(','); sb.append('[').append(atom()).append(',').append(atom()).append(']') }
                sb.append(']')
            }
        }
        var text = sb.toString()
        repeat(rnd.nextInt(3)) {
            if (text.isEmpty()) return@repeat
            val at = rnd.nextInt(text.length)
            text = when (rnd.nextInt(4)) {
                0 -> text.removeRange(at, at + 1)
                1 -> text.substring(0, at) + junk[rnd.nextInt(junk.length)] + text.substring(at)
                2 -> text.substring(0, at) + junk[rnd.nextInt(junk.length)] + text.substring(at + 1)
                else -> text.substring(0, at)
            }
        }
        return if (rnd.nextInt(6) == 0) " $text " else text
    }

    private fun bytesReadAsWideEncoding(text: String): Boolean {
        val bytes = text.encodeToByteArray()
        val detected = detectJsonByteEncoding(
            bytes = bytes,
            offset = 0,
            length = bytes.size
        )
        return detected.kind != JsonEncodingKind.UTF8
    }

    private fun hasLoneSurrogate(text: String): Boolean = text.indices.any { i ->
        val c = text[i]
        (c.isHighSurrogate() && (i + 1 >= text.length || !text[i + 1].isLowSurrogate())) ||
            (c.isLowSurrogate() && (i == 0 || !text[i - 1].isHighSurrogate()))
    }

    private class Target(
        val name: String,
        val lenient: List<(String) -> Any?>,
        val strict: List<(String) -> Any?>,
        val oracle: (String) -> Any?
    )

    private inline fun <reified T : Any> target(
        name: String,
        noinline oracle: (String) -> Any?
    ) = Target(
        name = name,
        lenient = listOf(
            { s -> Ghost.deserialize<T>(json = s) },
            { s -> Ghost.deserialize<T>(bytes = s.encodeToByteArray()) },
            { s -> Ghost.deserializeStreaming<T>(source = Buffer().writeUtf8(s)) },
        ),
        strict = listOf(
            { s -> Ghost.deserialize<T>(json = s) { it.strictMode = true } },
            { s -> Ghost.deserialize<T>(bytes = s.encodeToByteArray()) { it.strictMode = true } },
            { s -> Ghost.deserialize<T>(source = Buffer().writeUtf8(s)) { it.strictMode = true } },
        ),
        oracle = oracle
    )

    private fun normalize(value: Any?): Any? = when (value) {
        is Double -> if (value.isNaN()) "NaN" else value
        is Map<*, *> -> value.entries.map { it.key to normalize(it.value) }
        is List<*> -> value.map { normalize(it) }
        else -> value
    }

    private fun run(block: () -> Any?): Any? = try {
        normalize(block())
    } catch (e: GhostJsonException) {
        "ERR"
    } catch (e: Throwable) {
        "OTHER ${e::class.simpleName}"
    }

    /** The reference's own failure of any kind is a rejection. */
    private fun runOracle(block: () -> Any?): Any? = try {
        normalize(block())
    } catch (e: Throwable) {
        "ERR"
    }

    @Test
    fun structuralDocumentsAgreeWithKotlinx() {
        val json = Json
        val targets = listOf(
            target<List<Int>>("List<Int>") { json.decodeFromString(ListSerializer(Int.serializer()), it) },
            target<List<Long>>("List<Long>") { json.decodeFromString(ListSerializer(Long.serializer()), it) },
            target<List<Double>>("List<Double>") { json.decodeFromString(ListSerializer(Double.serializer()), it) },
            target<List<String>>("List<String>") { json.decodeFromString(ListSerializer(String.serializer()), it) },
            target<List<Boolean?>>("List<Boolean?>") {
                json.decodeFromString(ListSerializer(Boolean.serializer().nullable), it)
            },
            target<Map<String, Long>>("Map<String,Long>") {
                json.decodeFromString(MapSerializer(String.serializer(), Long.serializer()), it)
            },
            target<List<List<Int>>>("List<List<Int>>") {
                json.decodeFromString(ListSerializer(ListSerializer(Int.serializer())), it)
            },
        )
        repeat(iterations) {
            val text = generate()
            if (hasLoneSurrogate(text) || bytesReadAsWideEncoding(text)) return@repeat
            for (target in targets) {
                val lenient = target.lenient.map { run(it.let { f -> { f(text) } }) }
                val strict = target.strict.map { run(it.let { f -> { f(text) } }) }
                val oracle = runOracle { target.oracle(text) }
                if (lenient.toSet().size > 1) fail("lenient channels differ", "${target.name} $lenient input=${text.take(120)}")
                if (strict.toSet().size > 1) fail("strict channels differ", "${target.name} $strict input=${text.take(120)}")
                val other = (lenient + strict).filter { it is String && it.startsWith("OTHER") }
                if (other.isNotEmpty()) fail("non-Ghost exception", "${target.name} $other input=${text.take(120)}")
                val ghost = strict[1]
                // kotlinx rejects some valid exponent forms for integer types (`0e0500`); a generic parse and Python
                // accept them, so those are only flagged when the document is not valid JSON in the first place.
                val genericAccepts = runOracle { Json.parseToJsonElement(text) } != "ERR"
                val exponentForm = EXPONENT_FORM.containsMatchIn(text)
                if (ghost != "ERR" && oracle == "ERR" && !(genericAccepts && exponentForm)) {
                    fail("strict accepts what kotlinx rejects", "${target.name} input=${text.take(120)}")
                }
                if (ghost != "ERR" && oracle != "ERR" && ghost != oracle) fail("value differs", "${target.name} ghost=$ghost kotlinx=$oracle input=${text.take(120)}")
                val leniencyGhost = lenient[1]
                if (leniencyGhost != "ERR" && oracle != "ERR" && leniencyGhost != oracle) {
                    fail("lenient value differs", "${target.name} ghost=$leniencyGhost kotlinx=$oracle input=${text.take(120)}")
                }
            }
        }
        assertNoFailures()
    }

    // ---- strings and numbers ----------------------------------------------------------------------------------

    private fun randomString(): String {
        val sb = StringBuilder()
        val length = when (rnd.nextInt(5)) {
            0 -> rnd.nextInt(4)
            1 -> rnd.nextInt(20)
            2 -> rnd.nextInt(70)
            3 -> 60 + rnd.nextInt(10)
            else -> rnd.nextInt(300)
        }
        repeat(length) {
            when (rnd.nextInt(10)) {
                0 -> sb.append(rnd.nextInt(0x20).toChar())
                1 -> sb.append(listOf('"', '\\', '/', '\u007f', '\u2028', '\u2029')[rnd.nextInt(6)])
                2 -> sb.append((0x80 + rnd.nextInt(0x700)).toChar())
                3 -> sb.append((0x800 + rnd.nextInt(0xD000 - 0x800)).toChar())
                4 -> sb.appendCodePoint(0x10000 + rnd.nextInt(0x100000))
                5 -> sb.append((0xE000 + rnd.nextInt(0x1FFF)).toChar())
                else -> sb.append('a' + rnd.nextInt(26))
            }
        }
        return sb.toString()
    }

    @Test
    fun stringsRoundTripOnEveryChannel() {
        repeat(iterations) {
            val strings = List(1 + rnd.nextInt(4)) { randomString() }
            if (strings.any(::hasLoneSurrogate)) return@repeat
            val encoded = Ghost.serialize(strings)
            val kotlinxEncoded = Json.encodeToString(ListSerializer(String.serializer()), strings)
            if (Json.decodeFromString(ListSerializer(String.serializer()), encoded) != strings) {
                fail("kotlinx cannot read Ghost output", "strings=${strings.map { it.length }}")
            }
            val decoded = listOf(
                run { Ghost.deserialize<List<String>>(json = kotlinxEncoded) },
                run { Ghost.deserialize<List<String>>(bytes = kotlinxEncoded.encodeToByteArray()) },
                run { Ghost.deserializeStreaming<List<String>>(source = Buffer().writeUtf8(kotlinxEncoded)) },
                run { Ghost.deserializeStreaming<List<String>>(source = Buffer().write(Ghost.encodeToBytes(strings))) },
            )
            decoded.forEachIndexed { channel, value ->
                if (value != strings) fail("string round-trip", "channel=$channel lengths=${strings.map { it.length }}")
            }
        }
        assertNoFailures()
    }

    private fun randomDecimal(): String {
        val sb = StringBuilder()
        if (rnd.nextInt(4) == 0) sb.append('-')
        val intDigits = 1 + rnd.nextInt(if (rnd.nextInt(5) == 0) 25 else 8)
        if (intDigits > 1 || rnd.nextBoolean()) {
            sb.append('1' + rnd.nextInt(9))
            repeat(intDigits - 1) { sb.append('0' + rnd.nextInt(10)) }
        } else {
            sb.append('0')
        }
        if (rnd.nextInt(3) != 0) {
            sb.append('.')
            repeat(1 + rnd.nextInt(if (rnd.nextInt(5) == 0) 30 else 10)) { sb.append('0' + rnd.nextInt(10)) }
        }
        if (rnd.nextInt(3) == 0) {
            sb.append(if (rnd.nextBoolean()) 'e' else 'E')
            if (rnd.nextInt(3) == 0) sb.append(if (rnd.nextBoolean()) '+' else '-')
            sb.append(rnd.nextInt(if (rnd.nextInt(10) == 0) 330 else 40))
        }
        return sb.toString()
    }

    @Test
    fun decimalsParseLikeTheJvm() {
        repeat(iterations * 3) {
            val literal = randomDecimal()
            val expectedDouble = literal.toDouble()
            val expectedFloat = literal.toFloat()
            val text = "[$literal]"
            val doubles = listOf(
                run { Ghost.deserialize<List<Double>>(json = text) },
                run { Ghost.deserialize<List<Double>>(bytes = text.encodeToByteArray()) },
                run { Ghost.deserializeStreaming<List<Double>>(source = Buffer().writeUtf8(text)) },
            )
            val floats = listOf(
                run { Ghost.deserialize<List<Float>>(json = text) },
                run { Ghost.deserialize<List<Float>>(bytes = text.encodeToByteArray()) },
                run { Ghost.deserializeStreaming<List<Float>>(source = Buffer().writeUtf8(text)) },
            )
            doubles.forEachIndexed { channel, value ->
                val parsed = (value as? List<*>)?.firstOrNull() as? Double
                val sameBits = parsed != null && parsed.toRawBits() == expectedDouble.toRawBits()
                if (!sameBits && !(expectedDouble.isInfinite() && value == "ERR")) fail("Double parse", "channel=$channel $literal expected=$expectedDouble got=$value")
            }
            floats.forEachIndexed { channel, value ->
                val parsed = (value as? List<*>)?.firstOrNull() as? Float
                val sameBits = parsed != null && parsed.toRawBits() == expectedFloat.toRawBits()
                if (!sameBits && !(expectedFloat.isInfinite() && value == "ERR")) fail("Float parse", "channel=$channel $literal expected=$expectedFloat got=$value")
            }
        }
        assertNoFailures()
    }

    @Test
    fun numbersRoundTripFromRandomBits() {
        repeat(iterations * 3) {
            val double = Double.fromBits(rnd.nextLong()).let { if (it.isNaN() || it.isInfinite()) 1.5 else it }
            val float = Float.fromBits(rnd.nextInt()).let { if (it.isNaN() || it.isInfinite()) 2.5f else it }
            val long = if (rnd.nextBoolean()) rnd.nextLong() else rnd.nextLong() shr rnd.nextInt(64)
            val doubleBack = Ghost.deserialize<List<Double>>(json = Ghost.serialize(listOf(double)))[0]
            val floatBack = Ghost.deserialize<List<Float>>(bytes = Ghost.serialize(listOf(float)).encodeToByteArray())[0]
            val longBack = Ghost.deserialize<List<Long>>(json = Ghost.serialize(listOf(long)))[0]
            if (doubleBack.toRawBits() != double.toRawBits()) fail("Double round-trip", "$double -> $doubleBack")
            if (floatBack.toRawBits() != float.toRawBits()) fail("Float round-trip", "$float -> $floatBack")
            if (longBack != long) fail("Long round-trip", "$long -> $longBack")
            val kotlinxDouble = Json.decodeFromString(ListSerializer(Double.serializer()), Ghost.serialize(listOf(double)))[0]
            if (kotlinxDouble.toRawBits() != double.toRawBits()) fail("kotlinx reads Ghost Double differently", "$double -> $kotlinxDouble")
        }
        assertNoFailures()
    }

    // ---- truncation and deep nesting ------------------------------------------------------------------------------

    @Test
    fun truncatedAndMutatedInputsFailOnlyWithGhostJsonException() {
        val documents = listOf(
            """[{"a":[1.5,-2e3,0.25],"b":[true,null,false]},{"c":["x\n\u00e9\ud83d\ude00","y"],"d":{"e":[1,2,3]}}]""",
            """{"k1":[[1,2],[3]],"k2":[],"k3":[[ ]]}""",
            "[ 1 , 2 ,3 ,  4]",
            "[\"\\u12\"]",
            "[1e5,1E+5,1e-5]",
            "{\"a\":{\"b\":{\"c\":[{}]}}}",
            "[\"a\\\"b\\\\\"]",
        ).map { it.encodeToByteArray() }
        fun check(label: String, input: ByteArray) {
            val text = input.decodeToString()
            listOf<Pair<String, () -> Any?>>(
                "string" to { Ghost.deserialize<List<Map<String, List<Double>>>>(json = text) },
                "bytes" to { Ghost.deserialize<Map<String, List<List<Int>>>>(bytes = input) },
                "stream" to { Ghost.deserializeStreaming<List<String>>(source = Buffer().write(input)) },
                "strict" to { Ghost.deserialize<List<Long>>(bytes = input) { it.strictMode = true } },
            ).forEach { (channel, block) ->
                try { block() } catch (_: GhostJsonException) { } catch (e: Throwable) {
                    fail("non-Ghost exception", "$label $channel ${e::class.simpleName}: ${e.message?.take(60)} input=${text.take(80)}")
                }
            }
        }
        for (document in documents) {
            for (length in 0..document.size) check("prefix$length", document.copyOf(length))
            repeat(iterations / 4 + 1) {
                val mutated = document.copyOf()
                repeat(1 + rnd.nextInt(3)) { mutated[rnd.nextInt(mutated.size)] = rnd.nextInt(256).toByte() }
                check("mutated", mutated)
            }
        }
        for (depth in listOf(500, 5_000, 100_000)) {
            check("deepArray$depth", ("[".repeat(depth) + "]".repeat(depth)).encodeToByteArray())
            check("deepObject$depth", ("{\"a\":".repeat(depth) + "1" + "}".repeat(depth)).encodeToByteArray())
        }
        assertNoFailures()
    }

    private companion object {
        const val DEFAULT_SEED = 20261011L
        const val DEFAULT_ITERATIONS = 3_000
        const val MAX_REPORTED = 20
        val EXPONENT_FORM = Regex("[0-9][eE]")
    }
}
