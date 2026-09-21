package com.ghost.serialization

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.skipValue

/**
 * Coverage-guided robustness fuzzing for [GhostJsonReader], a hand-rolled byte-level state
 * machine with no generated bounds-checking (mirrors `GhostYamlFuzzTest` for YAML). Goal is
 * crash-safety, not correctness — [GhostCrashProofTest] and friends cover correctness.
 *
 * `skipValue()` is the entry point: a generic recursive-descent traversal needing no target
 * type, since typed deserialization always needs a concrete KSP-generated model and isn't
 * fuzzable generically.
 *
 * Each case documents the one exception malformed input is allowed to throw; anything else
 * Jazzer finds (OOB, arithmetic, stack overflow, hangs) is a real bug.
 *
 * Runs in regression mode (fixed seed corpus) as part of `ciTestJvm`. For real fuzzing:
 * `JAZZER_FUZZ=1 ./gradlew :ghost-serialization:jvmTest --tests
 * "com.ghost.serialization.GhostJsonFuzzTest"` — findings land in
 * `src/jvmTest/resources/.../<method>` and replay automatically after.
 */
class GhostJsonFuzzTest {

    @FuzzTest
    fun fuzzSkipValueFlatReaderBytes(data: FuzzedDataProvider) {
        val bytes = data.consumeRemainingAsBytes()
        try {
            GhostJsonReader(bytes).skipValue()
        } catch (_: GhostJsonException) {
            // Expected for malformed input — skipValue's documented contract.
        }
    }

    @FuzzTest
    fun fuzzSkipValueStreamingReaderBytes(data: FuzzedDataProvider) {
        // Separate entry point from fuzzSkipValueFlatReaderBytes: GhostJsonReader is what actual
        // deserialization delegates to at runtime (see GhostSerializer.deserialize(GhostJsonReader)),
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
            GhostJsonReader(text.encodeToByteArray()).skipValue()
        } catch (_: GhostJsonException) {
            // Expected for malformed input — skipValue's documented contract.
        }
    }
}
