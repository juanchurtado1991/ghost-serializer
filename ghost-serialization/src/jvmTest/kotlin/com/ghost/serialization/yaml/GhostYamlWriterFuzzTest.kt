@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.yaml

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.bytes.FlatByteArrayWriter
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter

/**
 * Coverage-guided robustness + round-trip fuzzing for [GhostYamlFlatWriter], the counterpart to
 * `GhostYamlFuzzTest` (which only fuzzes the reader). The writer's hand-rolled scalar escaping
 * (`writeEscaped`) and bare-vs-quoted key heuristic (`keyNeedsQuoting`) are exactly the kind of
 * byte-level, no-bounds-checking code this session's other fuzz tests already found real bugs in
 * (see `ProtoWktFuzzTest`'s KDoc) — but unlike the reader, the writer had zero fuzz coverage
 * before this test.
 *
 * Both cases are round-trip properties, not just crash-safety: any string Ghost's writer accepts
 * must decode back to itself via [GhostYamlFlatReader]. The input string is canonicalized through
 * one UTF-8 encode/decode pass before comparison — `FuzzedDataProvider.consumeString` can emit
 * unpaired UTF-16 surrogates, and lossy repair of those (unrelated to YAML) happens at that
 * boundary on every JVM string, not just Ghost's.
 *
 * Runs in regression mode (fixed seed corpus, JUnit-speed) as part of `ciTestJvm`. Run actual
 * fuzzing locally with `JAZZER_FUZZ=1 ./gradlew :ghost-serialization:jvmTest --tests
 * "com.ghost.serialization.yaml.GhostYamlWriterFuzzTest"` — findings are written to
 * `.cifuzz-corpus/com.ghost.serialization.yaml.GhostYamlWriterFuzzTest/<method>` and replayed
 * automatically on every future run.
 */
class GhostYamlWriterFuzzTest {

    private fun canonicalize(raw: String): String = raw.encodeToByteArray().decodeToString()

    @FuzzTest
    fun fuzzYamlStringValueRoundTrip(data: FuzzedDataProvider) {
        val expected = canonicalize(data.consumeRemainingAsString())

        val byteWriter = FlatByteArrayWriter()
        GhostYamlFlatWriter(byteWriter).beginObject().name("v").value(expected).endObject()

        val decoded = GhostYamlFlatReader(byteWriter.toByteArray()).readDocument()
        check(decoded is Map<*, *> && decoded["v"] == expected) {
            "YAML value round-trip mismatch: ${expected.length} chars -> " +
                "\"${byteWriter.toStringUtf8()}\" -> $decoded"
        }
    }

    @FuzzTest
    fun fuzzYamlMappingKeyRoundTrip(data: FuzzedDataProvider) {
        val expected = canonicalize(data.consumeRemainingAsString())

        val byteWriter = FlatByteArrayWriter()
        GhostYamlFlatWriter(byteWriter).beginObject().name(expected).value(1).endObject()

        val decoded = GhostYamlFlatReader(byteWriter.toByteArray()).readDocument()
        check(decoded is Map<*, *> && decoded.keys.singleOrNull() == expected) {
            "YAML key round-trip mismatch: ${expected.length} chars -> " +
                "\"${byteWriter.toStringUtf8()}\" -> $decoded"
        }
    }
}
