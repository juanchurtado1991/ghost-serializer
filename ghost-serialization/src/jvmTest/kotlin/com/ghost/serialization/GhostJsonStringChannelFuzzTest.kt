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
 * Fuzzing for the JSON *string* channel — [GhostJsonStringReader]/[GhostJsonStringWriter], the
 * default `textChannel = true` path over a UTF-16 [String]. Independent from the flat-bytes and
 * Okio-streaming JSON reader/writer implementations, with its own string-pool/escape-decode hot
 * path (`readQuotedStringSlow`); previously unfuzzed.
 *
 * Regression mode (fixed seed corpus) runs in `ciTestJvm`. For real fuzzing:
 * `JAZZER_FUZZ=1 ./gradlew :ghost-serialization:jvmTest --tests
 * "com.ghost.serialization.GhostJsonStringChannelFuzzTest"` — findings land in
 * `.cifuzz-corpus/com.ghost.serialization.GhostJsonStringChannelFuzzTest/<method>` and replay
 * automatically after.
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
