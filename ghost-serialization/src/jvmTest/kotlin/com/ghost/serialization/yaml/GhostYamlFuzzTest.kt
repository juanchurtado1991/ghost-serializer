package com.ghost.serialization.yaml

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.yaml.exception.GhostYamlException

/**
 * Coverage-guided robustness fuzzing for [GhostYamlFlatReader] — a hand-rolled byte-level state
 * machine (block/flow collections, anchors, tags, block scalars, directives) with no generated
 * bounds-checking, the same class of code that already produced two real bugs in the proto WKT
 * parsers this session (see `ProtoWktFuzzTest`). The goal here
 * is crash-safety, not correctness — `GhostYamlTestSuiteConformanceTest`
 * already covers correctness against the yaml-test-suite reference for well-formed and
 * known-invalid input.
 *
 * Every case documents the one exception type malformed input is allowed to throw; anything else
 * Jazzer finds (index-out-of-bounds, arithmetic, stack overflow, hangs) is a real bug.
 *
 * Runs in regression mode (fixed seed corpus, JUnit-speed) as part of `ciTestJvm`. Run actual
 * fuzzing locally with `JAZZER_FUZZ=1 ./gradlew :ghost-serialization:jvmTest --tests
 * "com.ghost.serialization.yaml.GhostYamlFuzzTest"` — findings are written to
 * `src/jvmTest/resources/.../<method>` and replayed automatically on every future run.
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
        // Separate entry point from fuzzReadDocumentBytes: readAllDocuments() drives the
        // multi-document loop (directives, "---"/"..." markers, stream-level state) that a
        // single readDocument() call never exercises.
        val bytes = data.consumeRemainingAsBytes()
        try {
            GhostYamlFlatReader(bytes).readAllDocuments()
        } catch (_: GhostYamlException) {
            // Expected for malformed input — readAllDocuments's documented contract.
        }
    }

    @FuzzTest
    fun fuzzReadDocumentUtf8Text(data: FuzzedDataProvider) {
        // consumeRemainingAsString() (vs consumeRemainingAsBytes() above) biases the corpus
        // toward well-formed UTF-8 with garbage *YAML structure*, rather than spending fuzz
        // budget on malformed UTF-8 byte sequences the two byte-level methods already cover.
        val text = data.consumeRemainingAsString()
        try {
            GhostYamlFlatReader(text.encodeToByteArray()).readDocument()
        } catch (_: GhostYamlException) {
            // Expected for malformed input — readDocument's documented contract.
        }
    }
}
