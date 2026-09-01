package com.ghost.serialization.integration

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.integration.model.ComplexObjectSerializer
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.streaming.GhostJsonReader

/**
 * Fuzzes the *typed* decode path via a real KSP-generated serializer ([ComplexObjectSerializer],
 * same fixture as [GhostRobustnessTest]) — unlike `ghost-serialization`'s own fuzz tests, which
 * only exercise the generic untyped skipValue()/readDocument() traversal, since no module there
 * has KSP wired over its test source sets.
 *
 * Goal is crash-safety, not correctness ([GhostRobustnessTest] covers that). Malformed input can
 * legitimately throw more than [GhostJsonException] (e.g. a null on a non-nullable field surfaces
 * as the constructor's own null-check), so any [Exception] is accepted here.
 *
 * `fuzzComplexObjectDeserializeStringChannel` covers the third, independent `textChannel = true`
 * overload, which walks a `CharArray` instead of a `ByteArray` — a different bug class (see
 * `GhostJsonStringChannelFuzzTest`'s `ArrayIndexOutOfBoundsException` finding in that channel).
 *
 * Runs in regression mode (fixed corpus) as part of `ciTestJvm`. For real fuzzing locally:
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
        // Biases the corpus toward well-formed UTF-8 with garbage JSON structure, complementing
        // the raw-bytes entry point above.
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
