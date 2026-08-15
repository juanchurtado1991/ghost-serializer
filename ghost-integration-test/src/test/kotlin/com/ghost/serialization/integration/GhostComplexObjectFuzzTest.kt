package com.ghost.serialization.integration

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.integration.model.ComplexObjectSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader

/**
 * Coverage-guided robustness fuzzing for the *typed* decode path — a real KSP-generated
 * serializer ([ComplexObjectSerializer], for the 20-field, deeply-nested [ComplexObject] fixture
 * already used by [GhostRobustnessTest]) hydrating a concrete `data class` field by field.
 *
 * `ghost-serialization`'s own fuzz tests (`GhostJsonFuzzTest`, `GhostYamlFuzzTest`) only exercise
 * the generic, untyped `skipValue()`/`readDocument()` traversal — deliberately, per their own
 * KDoc, because "Ghost's typed deserialization always needs a concrete KSP-generated model, which
 * isn't fuzzable generically". That's true for an *arbitrary* model, but nothing stops fuzzing one
 * *fixed*, real one: `ComplexObjectSerializer.deserialize` is generated code with its own
 * independent hot paths — required-field bookkeeping, enum decode, nested object/list/map
 * construction, `@GhostResilient`-free strict field checks — none of which `skipValue()` ever
 * touches. `ghost-serialization` itself has no module with KSP wired over its test source sets
 * (confirmed: its `@GhostSerialization` test fixtures never get a generated serializer), so this
 * lives here instead, in the one module where KSP genuinely runs over test-visible models.
 *
 * Goal is crash-safety, not correctness — `GhostRobustnessTest` already covers correctness for
 * well-formed and hand-picked adversarial JSON. `assertFailsWith<Exception>` in that file already
 * establishes that malformed input to this exact serializer may legitimately throw more than just
 * [GhostJsonException] (e.g. a `null` on a non-nullable field surfaces as the Kotlin constructor's
 * own null-check), so this test allows any [Exception] — anything Jazzer finds beyond that
 * (index-out-of-bounds, arithmetic, stack overflow, hangs) is a real bug.
 *
 * Runs in regression mode (fixed seed corpus, JUnit-speed) as part of `ciTestJvm`. Run actual
 * fuzzing locally with `JAZZER_FUZZ=1 ./gradlew :ghost-integration-test:test --tests
 * "com.ghost.serialization.integration.GhostComplexObjectFuzzTest"` — findings are written to
 * `.cifuzz-corpus/com.ghost.serialization.integration.GhostComplexObjectFuzzTest/<method>` and
 * replayed automatically on every future run.
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
        // consumeRemainingAsString() biases the corpus toward well-formed UTF-8 with garbage
        // *JSON structure*, complementing the raw-bytes entry point above (mirrors
        // GhostJsonFuzzTest's fuzzSkipValueUtf8Text / fuzzSkipValueFlatReaderBytes split).
        val text = data.consumeRemainingAsString()
        try {
            ComplexObjectSerializer.deserialize(GhostJsonReader(text.encodeToByteArray()))
        } catch (_: Exception) {
            // Expected for malformed/adversarial input — see class KDoc.
        }
    }
}
