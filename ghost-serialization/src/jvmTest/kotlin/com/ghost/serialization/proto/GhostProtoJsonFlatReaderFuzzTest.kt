package com.ghost.serialization.proto

import com.code_intelligence.jazzer.api.FuzzedDataProvider
import com.code_intelligence.jazzer.junit.FuzzTest
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader

/**
 * Coverage-guided fuzzing for [GhostProtoJsonFlatReader]'s proto3-JSON-specific numeric/bytes/enum
 * decoders (quoted-number and `"NaN"`/`"Infinity"` handling, hand-rolled base64) — none of this is
 * reached by `GhostJsonFuzzTest`'s `skipValue()` fuzzing, which only exercises inherited generic
 * traversal. Same class of hand-rolled parsing that already produced two real bugs elsewhere
 * (see `ProtoWktFuzzTest`'s KDoc).
 *
 * Allows any [Exception], not just [com.ghost.serialization.exception.GhostJsonException]:
 * `readProtoUInt64`'s quoted-string branch calls `nextString().toULong()` directly, so an invalid
 * numeral throws [NumberFormatException] instead — a known minor inconsistency, tracked here
 * rather than silently worked around.
 *
 * Regression mode (fixed seed corpus) runs in `ciTestJvm`. For real fuzzing:
 * `JAZZER_FUZZ=1 ./gradlew :ghost-serialization:jvmTest --tests
 * "com.ghost.serialization.proto.GhostProtoJsonFlatReaderFuzzTest"` — findings land in
 * `.cifuzz-corpus/com.ghost.serialization.proto.GhostProtoJsonFlatReaderFuzzTest/<method>` and
 * replay automatically after.
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
