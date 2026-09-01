package com.ghost.serialization.yaml

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.yaml.exception.GhostYamlException

/**
 * Coverage-guided fuzzing for [GhostYamlFlatReader] — a hand-rolled byte-level state machine
 * (block/flow collections, anchors, tags, block scalars, directives) with no generated
 * bounds-checking, the same class of code that already produced two real bugs in the proto WKT
 * parsers (see `ProtoWktFuzzTest`). Goal is crash-safety; `GhostYamlTestSuiteConformanceTest`
 * covers correctness against the yaml-test-suite reference.
 *
 * Each case documents the one exception type malformed input may throw; anything else Jazzer
 * finds is a real bug.
 *
 * Regression mode (fixed seed corpus) runs in `ciTestJvm`. For real fuzzing:
 * `JAZZER_FUZZ=1 ./gradlew :ghost-serialization:jvmTest --tests
 * "com.ghost.serialization.yaml.GhostYamlFuzzTest"` — findings land in
 * `src/jvmTest/resources/.../<method>` and replay automatically after.
 */
class GhostYamlFuzzTest {

    @FuzzTest
    fun fuzzReadDocumentBytes(data: FuzzedDataProvider) {
        val bytes = data.consumeRemainingAsBytes()
        try {
            GhostYamlFlatReader(bytes).readDocument()
        } catch (_: GhostYamlException) {
            // Expected for malformed input — readDocument's documented contract.
        }
    }

    @FuzzTest
    fun fuzzReadAllDocumentsBytes(data: FuzzedDataProvider) {
        // Distinct from fuzzReadDocumentBytes: exercises the multi-document loop (directives,
        // "---"/"..." markers, stream-level state) that a single readDocument() never reaches.
        val bytes = data.consumeRemainingAsBytes()
        try {
            GhostYamlFlatReader(bytes).readAllDocuments()
        } catch (_: GhostYamlException) {
            // Expected for malformed input — readAllDocuments's documented contract.
        }
    }

    @FuzzTest
    fun fuzzReadDocumentUtf8Text(data: FuzzedDataProvider) {
        // consumeRemainingAsString() biases the corpus toward valid UTF-8 with garbage YAML
        // structure, instead of the malformed-UTF-8 cases the byte-level methods already cover.
        val text = data.consumeRemainingAsString()
        try {
            GhostYamlFlatReader(text.encodeToByteArray()).readDocument()
        } catch (_: GhostYamlException) {
            // Expected for malformed input — readDocument's documented contract.
        }
    }
}
