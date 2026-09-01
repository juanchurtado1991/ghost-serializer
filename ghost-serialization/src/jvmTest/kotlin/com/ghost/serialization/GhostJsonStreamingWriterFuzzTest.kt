@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import okio.Buffer

/**
 * Round-trip fuzzing for [GhostJsonWriter] — the Okio-streaming JSON writer, counterpart to
 * `GhostJsonWriterFuzzTest` (which only covers [com.ghost.serialization.writer.bytes.GhostJsonFlatWriter]).
 * A genuinely separate implementation (own `writeStringValueRaw`/`writeEscaped`), previously only
 * covered by a handful of fixed examples in `GhostCrashProofTest`
 * (`roundTripAllEscapeCharacters`, `roundTripControlCharsBelow0x20`) — this generalizes that same
 * round-trip property to arbitrary fuzzer input, mirroring the flat-writer fuzz test.
 *
 * Runs in regression mode (fixed seed corpus, JUnit-speed) as part of `ciTestJvm`. Run actual
 * fuzzing locally with `JAZZER_FUZZ=1 ./gradlew :ghost-serialization:jvmTest --tests
 * "com.ghost.serialization.GhostJsonStreamingWriterFuzzTest"` — findings are written to
 * `.cifuzz-corpus/com.ghost.serialization.GhostJsonStreamingWriterFuzzTest/<method>` and
 * replayed automatically on every future run.
 */
class GhostJsonStreamingWriterFuzzTest {

    private fun canonicalize(raw: String): String = raw.encodeToByteArray().decodeToString()

    @FuzzTest
    fun fuzzJsonStreamingStringValueRoundTrip(data: FuzzedDataProvider) {
        val expected = canonicalize(data.consumeRemainingAsString())

        val buffer = Buffer()
        GhostJsonWriter(buffer).beginObject().name("v").value(expected).endObject().flush()

        val reader = GhostJsonReader(buffer.readByteArray())
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        val decoded = reader.nextString()
        reader.endObject()

        check(decoded == expected) {
            "JSON streaming value round-trip mismatch: ${expected.length} chars -> \"$decoded\""
        }
    }

    @FuzzTest
    fun fuzzJsonStreamingKeyRoundTrip(data: FuzzedDataProvider) {
        val expected = canonicalize(data.consumeRemainingAsString())

        val buffer = Buffer()
        GhostJsonWriter(buffer).beginObject().name(expected).value(1).endObject().flush()

        val reader = GhostJsonReader(buffer.readByteArray())
        reader.beginObject()
        val decoded = reader.nextKey()
        reader.consumeKeySeparator()
        reader.nextInt()
        reader.endObject()

        check(decoded == expected) {
            "JSON streaming key round-trip mismatch: ${expected.length} chars -> \"$decoded\""
        }
    }
}
