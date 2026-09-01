@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.skipValue
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter

/**
 * Coverage-guided round-trip fuzzing for [GhostJsonWriter] — the in-memory encode path every
 * KSP-generated serializer uses (see its own KDoc), and the counterpart to `GhostJsonFuzzTest`
 * (which only fuzzes the reader's generic `skipValue()` traversal, never the writer). Its
 * hand-rolled `writeEscaped`/string-quoting logic is example-tested by `GhostCrashProofTest`
 * (`roundTripAllEscapeCharacters`, `roundTripControlCharsBelow0x20`) but only against a handful
 * of fixed strings — this generalizes the same round-trip property to arbitrary fuzzer input.
 *
 * The input string is canonicalized through one UTF-8 encode/decode pass before comparison —
 * `FuzzedDataProvider.consumeString` can emit unpaired UTF-16 surrogates, and lossy repair of
 * those (unrelated to JSON) happens at that boundary on every JVM string, not just Ghost's.
 *
 * Runs in regression mode (fixed seed corpus, JUnit-speed) as part of `ciTestJvm`. Run actual
 * fuzzing locally with `JAZZER_FUZZ=1 ./gradlew :ghost-serialization:jvmTest --tests
 * "com.ghost.serialization.GhostJsonWriterFuzzTest"` — findings are written to
 * `.cifuzz-corpus/com.ghost.serialization.GhostJsonWriterFuzzTest/<method>` and replayed
 * automatically on every future run.
 */
class GhostJsonWriterFuzzTest {

    private fun canonicalize(raw: String): String = raw.encodeToByteArray().decodeToString()

    @FuzzTest
    fun fuzzJsonStringValueRoundTrip(data: FuzzedDataProvider) {
        val expected = canonicalize(data.consumeRemainingAsString())

        val byteWriter = FlatByteArrayWriter()
        GhostJsonWriter(byteWriter).beginObject().name("v").value(expected).endObject()

        val reader = GhostJsonReader(byteWriter.toByteArray())
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        val decoded = reader.nextString()
        reader.endObject()

        check(decoded == expected) {
            "JSON value round-trip mismatch: ${expected.length} chars -> " +
                "\"${byteWriter.toStringUtf8()}\" -> \"$decoded\""
        }
    }

    @FuzzTest
    fun fuzzJsonKeyRoundTrip(data: FuzzedDataProvider) {
        val expected = canonicalize(data.consumeRemainingAsString())

        val byteWriter = FlatByteArrayWriter()
        GhostJsonWriter(byteWriter).beginObject().name(expected).value(1).endObject()

        val reader = GhostJsonReader(byteWriter.toByteArray())
        reader.beginObject()
        val decoded = reader.nextKey()
        reader.consumeKeySeparator()
        reader.nextInt()
        reader.endObject()

        check(decoded == expected) {
            "JSON key round-trip mismatch: ${expected.length} chars -> " +
                "\"${byteWriter.toStringUtf8()}\" -> \"$decoded\""
        }
    }
}
