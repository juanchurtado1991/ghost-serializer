package com.ghost.protobuf.wkt

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.ghost.serialization.parser.decodeBase64String

/**
 * Coverage-guided robustness fuzzing for `ghost-protobuf`'s hand-rolled, byte/char-level
 * parsers — [parseDuration], [parseTimestamp], and [decodeBase64String] all do manual digit/char
 * scanning without the bounds-checking a generated parser would get for free, and this exact
 * class of code has already produced two real bugs this session (`Long.MIN_VALUE` sign
 * corruption, non-conformant nanosecond-fraction trimming). The goal here is crash-safety, not
 * correctness — [ProtoJsonConformanceTest] already covers correctness against a reference
 * implementation for well-formed input.
 *
 * Every case documents the one exception type malformed input is allowed to throw; anything
 * else Jazzer finds (index-out-of-bounds, arithmetic, stack overflow, hangs) is a real bug.
 *
 * Runs in regression mode (fixed seed corpus, JUnit-speed) as part of `ciTestJvm`. Run actual
 * fuzzing locally with `JAZZER_FUZZ=1 ./gradlew :ghost-protobuf:jvmTest --tests
 * "com.ghost.protobuf.wkt.ProtoWktFuzzTest"` — findings are written to
 * `src/jvmTest/resources/.../<method>` and replayed automatically on every future run.
 */
class ProtoWktFuzzTest {

    @FuzzTest
    fun fuzzParseDuration(data: FuzzedDataProvider) {
        val input = data.consumeRemainingAsString()
        try {
            parseDuration(input)
        } catch (_: IllegalArgumentException) {
            // Expected for malformed input — parseDuration's documented contract.
        }
    }

    @FuzzTest
    fun fuzzParseTimestamp(data: FuzzedDataProvider) {
        val input = data.consumeRemainingAsString()
        try {
            parseTimestamp(input)
        } catch (_: IllegalArgumentException) {
            // Expected for malformed input — parseTimestamp's documented contract.
        }
    }

    @FuzzTest
    fun fuzzDecodeBase64String(data: FuzzedDataProvider) {
        val input = data.consumeRemainingAsString()
        try {
            decodeBase64String(input)
        } catch (_: IllegalArgumentException) {
            // Expected for malformed input — decodeBase64String's documented contract.
        }
    }

    @FuzzTest
    fun fuzzFormatDurationRoundTrip(data: FuzzedDataProvider) {
        // Round-trip property, not just crash-safety: any (seconds, nanos) pair that satisfies
        // ProtoDuration's own sign-coherence invariant must format and re-parse back to itself.
        val seconds = data.consumeLong()
        val nanos = data.consumeInt(-999_999_999, 999_999_999)
        if ((seconds > 0 && nanos < 0) || (seconds < 0 && nanos > 0)) return
        val duration = ProtoDuration(seconds, nanos)
        val formatted = formatDuration(duration)
        val reparsed = parseDuration(formatted)
        check(reparsed == duration) { "Round-trip mismatch: $duration -> \"$formatted\" -> $reparsed" }
    }
}
