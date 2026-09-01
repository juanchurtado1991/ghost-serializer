@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.yaml

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import okio.Buffer

/**
 * Round-trip fuzzing for [GhostYamlWriter] — the Okio-streaming counterpart to
 * `GhostYamlWriterFuzzTest` (which only covers [com.ghost.serialization.writer.yaml.GhostYamlWriter]).
 * Genuinely separate implementation, not a thin wrapper: `GhostYamlWriter.name(String)` used to
 * have **no** key-quoting check at all (a doc comment on `GhostYamlWriterHelpers` said so
 * explicitly), so every mapping key went out bare/unescaped — `name("a: b")` produced YAML
 * `GhostYamlFlatReader` itself couldn't parse back. Fixed by extracting `keyNeedsQuoting` into
 * the shared `GhostYamlWriterHelpers` both writers now call; this test exists so that gap can't
 * silently reopen for either writer again.
 *
 * Runs in regression mode (fixed seed corpus, JUnit-speed) as part of `ciTestJvm`. Run actual
 * fuzzing locally with `JAZZER_FUZZ=1 ./gradlew :ghost-serialization:jvmTest --tests
 * "com.ghost.serialization.yaml.GhostYamlStreamingWriterFuzzTest"` — findings are written to
 * `.cifuzz-corpus/com.ghost.serialization.yaml.GhostYamlStreamingWriterFuzzTest/<method>` and
 * replayed automatically on every future run.
 */
class GhostYamlStreamingWriterFuzzTest {

    private fun canonicalize(raw: String): String = raw.encodeToByteArray().decodeToString()

    @FuzzTest
    fun fuzzYamlStreamingStringValueRoundTrip(data: FuzzedDataProvider) {
        val expected = canonicalize(data.consumeRemainingAsString())

        val buffer = Buffer()
        val writer = GhostYamlWriter(buffer)
        writer.beginObject().name("v").value(expected).endObject()
        writer.flush()

        val decoded = GhostYamlFlatReader(buffer.readByteArray()).readDocument()
        check(decoded is Map<*, *> && decoded["v"] == expected) {
            "YAML streaming value round-trip mismatch: ${expected.length} chars -> $decoded"
        }
    }

    @FuzzTest
    fun fuzzYamlStreamingMappingKeyRoundTrip(data: FuzzedDataProvider) {
        val expected = canonicalize(data.consumeRemainingAsString())

        val buffer = Buffer()
        val writer = GhostYamlWriter(buffer)
        writer.beginObject().name(expected).value(1).endObject()
        writer.flush()

        val decoded = GhostYamlFlatReader(buffer.readByteArray()).readDocument()
        check(decoded is Map<*, *> && decoded.keys.singleOrNull() == expected) {
            "YAML streaming key round-trip mismatch: ${expected.length} chars -> $decoded"
        }
    }
}
