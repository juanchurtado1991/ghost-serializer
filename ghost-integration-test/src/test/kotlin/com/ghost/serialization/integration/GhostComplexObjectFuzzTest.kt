package com.ghost.serialization.integration

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.integration.model.ComplexObjectSerializer
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.streaming.GhostJsonReader

/**
 * Fuzzes the *typed* decode path via a real KSP-generated serializer ([ComplexObjectSerializer]),
 * unlike `ghost-serialization`'s own fuzz tests, which only reach the generic untyped
 * skipValue()/readDocument() traversal since no module there has KSP wired over test sources.
 *
 * Goal is crash-safety, not correctness ([GhostRobustnessTest] covers that) — malformed input can
 * legitimately throw more than [GhostJsonException] (e.g. a non-nullable field's constructor
 * null-check), so any [Exception] is accepted.
 *
 * `fuzzComplexObjectDeserializeStringChannel` covers the third, independent `textChannel = true`
 * overload (`CharArray` instead of `ByteArray`) — a different bug class from the other two.
 *
 * Runs in regression mode (fixed corpus) via `ciTestJvm`. For real fuzzing locally:
 * `JAZZER_FUZZ=1 ./gradlew :ghost-integration-test:test --tests
 * "com.ghost.serialization.integration.GhostComplexObjectFuzzTest"`.
 */
class GhostComplexObjectFuzzTest {

    @FuzzTest
    fun fuzzComplexObjectDeserializeBytes(data: FuzzedDataProvider) {
        val bytes = data.consumeRemainingAsBytes()
        try {
            ComplexObjectSerializer.deserialize(GhostJsonReader(bytes))
        } catch (_: Exception) {
            // Expected for malformed/adversarial input — see class KDoc.
        }
    }

    @FuzzTest
    fun fuzzComplexObjectDeserializeUtf8Text(data: FuzzedDataProvider) {
        // Biases the corpus toward well-formed UTF-8 with garbage JSON structure.
        val text = data.consumeRemainingAsString()
        try {
            ComplexObjectSerializer.deserialize(GhostJsonReader(text.encodeToByteArray()))
        } catch (_: Exception) {
            // Expected for malformed/adversarial input — see class KDoc.
        }
    }

    @FuzzTest
    fun fuzzComplexObjectDeserializeStringChannel(data: FuzzedDataProvider) {
        val text = data.consumeRemainingAsString()
        try {
            ComplexObjectSerializer.deserialize(GhostJsonStringReader(text))
        } catch (_: Exception) {
            // Expected for malformed/adversarial input — see class KDoc.
        }
    }
}
