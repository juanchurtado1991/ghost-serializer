package com.ghost.serialization

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.skipValue

/**
 * Coverage-guided robustness fuzzing for Ghost's two core JSON parsers —
 * [GhostJsonFlatReader] (in-memory `ByteArray`) and [GhostJsonReader] (streaming/`BufferedSource`,
 * genuinely separate hot-path implementations, not just a thin wrapper over the flat reader) —
 * mirroring [com.ghost.serialization.yaml.GhostYamlFuzzTest]'s approach for the YAML parser. Both
 * are hand-rolled byte-level state machines with no generated bounds-checking. The goal here is
 * crash-safety, not correctness — [GhostCrashProofTest] and friends already cover correctness
 * against known-good and known-malformed input.
 *
 * `skipValue()` is the entry point: a self-contained, generic recursive-descent traversal of
 * whatever JSON value comes next (object/array/string/number/boolean/null), with no target type
 * required — the same role [GhostYamlFlatReader.readDocument] plays for YAML. Ghost's typed
 * deserialization always needs a concrete, KSP-generated model, which isn't fuzzable generically.
 *
 * Every case documents the one exception type malformed input is allowed to throw; anything else
 * Jazzer finds (index-out-of-bounds, arithmetic, stack overflow, hangs) is a real bug.
 *
 * Runs in regression mode (fixed seed corpus, JUnit-speed) as part of `ciTestJvm`. Run actual
 * fuzzing locally with `JAZZER_FUZZ=1 ./gradlew :ghost-serialization:jvmTest --tests
 * "com.ghost.serialization.GhostJsonFuzzTest"` — findings are written to
 * `src/jvmTest/resources/.../<method>` and replayed automatically on every future run.
 */
class GhostJsonFuzzTest {

    @FuzzTest
    fun fuzzSkipValueFlatReaderBytes(data: FuzzedDataProvider) {
        val bytes = data.consumeRemainingAsBytes()
        try {
            GhostJsonFlatReader(bytes).skipValue()
        } catch (_: GhostJsonException) {
            // Expected for malformed input — skipValue's documented contract.
        }
    }

    @FuzzTest
    fun fuzzSkipValueStreamingReaderBytes(data: FuzzedDataProvider) {
        // Separate entry point from fuzzSkipValueFlatReaderBytes: GhostJsonReader is what actual
        // deserialization delegates to at runtime (see GhostSerializer.deserialize(GhostJsonFlatReader)),
        // and has its own independently-implemented string/number scanning hot paths.
        val bytes = data.consumeRemainingAsBytes()
        try {
            GhostJsonReader(bytes).skipValue()
        } catch (_: GhostJsonException) {
            // Expected for malformed input — skipValue's documented contract.
        }
    }

    @FuzzTest
    fun fuzzSkipValueUtf8Text(data: FuzzedDataProvider) {
        // consumeRemainingAsString() (vs consumeRemainingAsBytes() above) biases the corpus
        // toward well-formed UTF-8 with garbage *JSON structure*, rather than spending fuzz
        // budget on malformed UTF-8 byte sequences the two byte-level methods already cover.
        val text = data.consumeRemainingAsString()
        try {
            GhostJsonFlatReader(text.encodeToByteArray()).skipValue()
        } catch (_: GhostJsonException) {
            // Expected for malformed input — skipValue's documented contract.
        }
    }
}
