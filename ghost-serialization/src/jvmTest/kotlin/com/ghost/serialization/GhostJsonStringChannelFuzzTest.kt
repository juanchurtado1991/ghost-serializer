@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.endObject
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.nextKey
import com.ghost.serialization.parser.strings.nextString
import com.ghost.serialization.parser.strings.skipValue
import com.ghost.serialization.writer.strings.FlatCharArrayWriter
import com.ghost.serialization.writer.strings.GhostJsonStringWriter

/**
 * Fuzzing for the JSON *string* channel — [GhostJsonStringReader] / [GhostJsonStringWriter], the
 * default `textChannel = true` in-memory path over a UTF-16 [String] rather than raw bytes. The
 * third of JSON's three independent reader/writer implementations (flat-bytes and Okio-streaming
 * are covered by `GhostJsonFuzzTest` and `GhostJsonWriterFuzzTest`/`GhostJsonStreamingWriterFuzzTest`);
 * this one indexes a `CharArray` and has its own string-pool/escape-decode hot path
 * (`readQuotedStringSlow`), never previously fuzzed.
 *
 * `fuzzSkipValueStringReader` mirrors the other readers' crash-safety entry point.
 * `fuzzJsonStringChannelValueRoundTrip`/`...KeyRoundTrip` mirror the other writers' round-trip
 * property — the string writer always quotes both, so no bare-vs-quoted heuristic to break, but
 * its own `writeEscaped` is independent code from the byte/streaming writers' escaping.
 *
 * Runs in regression mode (fixed seed corpus, JUnit-speed) as part of `ciTestJvm`. Run actual
 * fuzzing locally with `JAZZER_FUZZ=1 ./gradlew :ghost-serialization:jvmTest --tests
 * "com.ghost.serialization.GhostJsonStringChannelFuzzTest"` — findings are written to
 * `.cifuzz-corpus/com.ghost.serialization.GhostJsonStringChannelFuzzTest/<method>` and replayed
 * automatically on every future run.
 */
class GhostJsonStringChannelFuzzTest {

    private fun canonicalize(raw: String): String = raw.encodeToByteArray().decodeToString()

    @FuzzTest
    fun fuzzSkipValueStringReader(data: FuzzedDataProvider) {
        val text = data.consumeRemainingAsString()
        try {
            GhostJsonStringReader(text).skipValue()
        } catch (_: GhostJsonException) {
            // Expected for malformed input — skipValue's documented contract.
        }
    }

    @FuzzTest
    fun fuzzJsonStringChannelValueRoundTrip(data: FuzzedDataProvider) {
        val expected = canonicalize(data.consumeRemainingAsString())

        val charWriter = FlatCharArrayWriter()
        GhostJsonStringWriter(charWriter).beginObject().name("v").value(expected).endObject()

        val reader = GhostJsonStringReader(charWriter.toString())
        reader.beginObject()
        reader.nextKey()
        reader.consumeKeySeparator()
        val decoded = reader.nextString()
        reader.endObject()

        check(decoded == expected) {
            "JSON string-channel value round-trip mismatch: ${expected.length} chars -> \"$decoded\""
        }
    }

    @FuzzTest
    fun fuzzJsonStringChannelKeyRoundTrip(data: FuzzedDataProvider) {
        val expected = canonicalize(data.consumeRemainingAsString())

        val charWriter = FlatCharArrayWriter()
        GhostJsonStringWriter(charWriter).beginObject().name(expected).value(1).endObject()

        val reader = GhostJsonStringReader(charWriter.toString())
        reader.beginObject()
        val decoded = reader.nextKey()
        reader.consumeKeySeparator()
        reader.nextInt()
        reader.endObject()

        check(decoded == expected) {
            "JSON string-channel key round-trip mismatch: ${expected.length} chars -> \"$decoded\""
        }
    }
}
