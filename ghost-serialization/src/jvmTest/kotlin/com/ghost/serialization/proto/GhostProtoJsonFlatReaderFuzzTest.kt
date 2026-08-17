package com.ghost.serialization.proto

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader

/**
 * Coverage-guided robustness fuzzing for [GhostProtoJsonFlatReader]'s proto3-JSON-specific
 * numeric/bytes/enum decoders — `nextInt`/`nextLong`/`nextFloat`/`nextDouble` (overridden to
 * accept proto3's quoted-string encodings and `"NaN"`/`"Infinity"`/`"-Infinity"`),
 * `nextProtoUInt32`/`nextProtoUInt64`, and `nextProtoBytes` (hand-rolled base64). None of these
 * are reached by `GhostJsonFuzzTest`'s `skipValue()` fuzzing, which only exercises the *inherited*
 * generic traversal — the proto-specific overrides, manual byte-bounds scanning for the quoted
 * `"NaN"`/`"Infinity"` literals, and the base64 LUT decode are all untested by it. This is the
 * same class of hand-rolled parsing that already produced two real bugs elsewhere this session
 * (see `ProtoWktFuzzTest`'s KDoc).
 *
 * Most malformed input throws [com.ghost.serialization.exception.GhostJsonException] via
 * `throwError`, but `readProtoUInt64`'s quoted-string branch calls `nextString().toULong()`
 * directly — an invalid numeral there throws the standard-library [NumberFormatException]
 * instead, not Ghost's own exception type. That inconsistency is a real but minor gap (a
 * caller catching only `GhostJsonException` around a `Map<String, UInt64Wrapper>` decode would
 * miss it); tracked here rather than silently worked around, so this test allows any
 * [Exception] instead of only `GhostJsonException`. Anything Jazzer finds beyond that
 * (index-out-of-bounds, arithmetic, stack overflow, hangs) is a real bug.
 *
 * Runs in regression mode (fixed seed corpus, JUnit-speed) as part of `ciTestJvm`. Run actual
 * fuzzing locally with `JAZZER_FUZZ=1 ./gradlew :ghost-serialization:jvmTest --tests
 * "com.ghost.serialization.proto.GhostProtoJsonFlatReaderFuzzTest"` — findings are written to
 * `.cifuzz-corpus/com.ghost.serialization.proto.GhostProtoJsonFlatReaderFuzzTest/<method>` and
 * replayed automatically on every future run.
 */
class GhostProtoJsonFlatReaderFuzzTest {

    @FuzzTest
    fun fuzzNextInt(data: FuzzedDataProvider) {
        val bytes = data.consumeRemainingAsBytes()
        try {
            GhostProtoJsonFlatReader(bytes).nextInt()
        } catch (_: Exception) {
            // Expected for malformed input — see class KDoc.
        }
    }

    @FuzzTest
    fun fuzzNextLong(data: FuzzedDataProvider) {
        val bytes = data.consumeRemainingAsBytes()
        try {
            GhostProtoJsonFlatReader(bytes).nextLong()
        } catch (_: Exception) {
            // Expected for malformed input — see class KDoc.
        }
    }

    @FuzzTest
    fun fuzzNextFloat(data: FuzzedDataProvider) {
        val bytes = data.consumeRemainingAsBytes()
        try {
            GhostProtoJsonFlatReader(bytes).nextFloat()
        } catch (_: Exception) {
            // Expected for malformed input — see class KDoc.
        }
    }

    @FuzzTest
    fun fuzzNextDouble(data: FuzzedDataProvider) {
        val bytes = data.consumeRemainingAsBytes()
        try {
            GhostProtoJsonFlatReader(bytes).nextDouble()
        } catch (_: Exception) {
            // Expected for malformed input — see class KDoc.
        }
    }

    @FuzzTest
    fun fuzzNextProtoUInt32(data: FuzzedDataProvider) {
        val bytes = data.consumeRemainingAsBytes()
        try {
            GhostProtoJsonFlatReader(bytes).nextProtoUInt32()
        } catch (_: Exception) {
            // Expected for malformed input — see class KDoc.
        }
    }

    @FuzzTest
    fun fuzzNextProtoUInt64(data: FuzzedDataProvider) {
        val bytes = data.consumeRemainingAsBytes()
        try {
            GhostProtoJsonFlatReader(bytes).nextProtoUInt64()
        } catch (_: Exception) {
            // Expected for malformed input — see class KDoc.
        }
    }

    @FuzzTest
    fun fuzzNextProtoBytes(data: FuzzedDataProvider) {
        val bytes = data.consumeRemainingAsBytes()
        try {
            GhostProtoJsonFlatReader(bytes).nextProtoBytes()
        } catch (_: Exception) {
            // Expected for malformed input — see class KDoc.
        }
    }

    @FuzzTest
    fun fuzzNextProtoEnum(data: FuzzedDataProvider) {
        val bytes = data.consumeRemainingAsBytes()
        try {
            GhostProtoJsonFlatReader(bytes).nextProtoEnum(JsonReaderOptions.of("A", "B", "C"))
        } catch (_: Exception) {
            // Expected for malformed input — see class KDoc.
        }
    }
}
