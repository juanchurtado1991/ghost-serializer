package com.ghost.serialization.proto.wkt

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.ghost.serialization.parser.common.decodeBase64String


/**
 * Coverage-guided fuzzing for proto WKT hand-rolled byte/char parsers — [parseDuration],
 * [parseTimestamp], [decodeBase64String] — which do manual scanning with no generated
 * bounds-checking. This exact class of code already produced two real bugs (`Long.MIN_VALUE`
 * sign corruption, non-conformant nanosecond-fraction trimming). Goal is crash-safety;
 * [ProtoJsonConformanceTest] covers correctness for well-formed input.
 *
 * Each case documents the one exception type malformed input may throw; anything else Jazzer
 * finds is a real bug.
 *
 * Regression mode (fixed seed corpus) runs in `ciTestJvm`. For real fuzzing:
 * `JAZZER_FUZZ=1 ./gradlew :ghost-serialization:jvmTest --tests
 * "com.ghost.serialization.proto.wkt.ProtoWktFuzzTest"` — findings land in
 * `src/jvmTest/resources/.../<method>` and replay automatically after.
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
        // Round-trip, not crash-safety: any (seconds, nanos) satisfying ProtoDuration's
        // sign-coherence invariant must format and re-parse back to itself.
        val seconds = data.consumeLong()
        val nanos = data.consumeInt(-999_999_999, 999_999_999)
        if ((seconds > 0 && nanos < 0) || (seconds < 0 && nanos > 0)) return
        val duration = ProtoDuration(seconds, nanos)
        val formatted = formatDuration(duration)
        val reparsed = parseDuration(formatted)
        check(reparsed == duration) { "Round-trip mismatch: $duration -> \"$formatted\" -> $reparsed" }
    }
}
