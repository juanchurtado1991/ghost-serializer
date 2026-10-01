package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.ComplexResponse
import com.ghost.serialization.integration.model.ExtremeMetadata
import com.ghost.serialization.integration.model.TwitterResponse
import com.ghost.serialization.integration.model.UserRole
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * Per-model `textChannel` codegen: plain `@GhostSerialization` models (Twitter macro roots and
 * synthetic benchmark models) default to the native string channel unless opted out explicitly.
 */
class GhostTextChannelPerModelIntegrationTest {

    @Test
    fun twitterMacroRoots_generateNativeStringDeserialize() {
        assertGeneratedSourceDeclaresStringDeserialize(serializerFileName = TWITTER_RESPONSE_SERIALIZER)
        assertGeneratedSourceDeclaresStringDeserialize(serializerFileName = TWITTER_SPECIAL_RESPONSE_SERIALIZER)
        assertGeneratedSourceDeclaresStringDeserialize(serializerFileName = TWITTER_WRAPPED_TWEET_SERIALIZER)
    }

    @Test
    fun twitterMacroNestedTypes_inheritTextChannelFromGraph() {
        assertGeneratedSourceDeclaresStringDeserialize(serializerFileName = TWEET_SERIALIZER)
        assertGeneratedSourceDeclaresStringDeserialize(serializerFileName = USER_SERIALIZER)
    }

    @Test
    fun syntheticBenchmarkModels_useNativeStringDeserialize() {
        assertGeneratedSourceDeclaresStringDeserialize(serializerFileName = COMPLEX_RESPONSE_SERIALIZER)
        assertGeneratedSourceDeclaresStringDeserialize(serializerFileName = BENCH_USER_SERIALIZER)
    }

    @Test
    fun complexResponse_deserializeString_roundTripsIncludingIntArrayField() {
        val original = ComplexResponse(
            status = "ok",
            data = emptyList(),
            meta = ExtremeMetadata(
                lastLogin = 0L,
                role = UserRole.VIEWER,
                tags = emptyList(),
                precisionScore = 0.0,
                accessHistory = intArrayOf(),
            ),
        )
        val json = Ghost.encodeToString(original)
        val restored = Ghost.deserialize<ComplexResponse>(json)
        assertEquals(expected = original.status, actual = restored.status)
        assertEquals(expected = original.data, actual = restored.data)
        assertEquals(expected = original.meta.lastLogin, actual = restored.meta.lastLogin)
        assertEquals(expected = original.meta.role, actual = restored.meta.role)
        assertEquals(expected = original.meta.tags, actual = restored.meta.tags)
        assertEquals(expected = original.meta.precisionScore, actual = restored.meta.precisionScore)
        assertTrue(actual = original.meta.accessHistory.contentEquals(restored.meta.accessHistory))
    }

    @Test
    fun twitterResponse_deserializeString_usesNativeStringChannel() {
        val json = """{"statuses":[]}"""
        val restored = Ghost.deserialize<TwitterResponse>(json)
        assertTrue(actual = restored.statuses.isEmpty())
    }

    private fun assertGeneratedSourceDeclaresStringDeserialize(serializerFileName: String) {
        val source = readGeneratedSerializerSource(serializerFileName = serializerFileName)
        assertTrue(
            actual = NATIVE_STRING_DESERIALIZE_SIGNATURE in source,
            message = "$serializerFileName must override deserialize(GhostJsonStringReader)",
        )
    }

    private fun readGeneratedSerializerSource(serializerFileName: String): String {
        val file = File(GENERATED_SERIALIZER_DIR, serializerFileName)
        assertTrue(actual = file.exists(), message = "Missing generated serializer: ${file.absolutePath}")
        return file.readText()
    }

    private companion object {
        private const val NATIVE_STRING_DESERIALIZE_SIGNATURE =
            "override fun deserialize(reader: GhostJsonStringReader)"

        private const val TWITTER_RESPONSE_SERIALIZER = "TwitterResponseSerializer.kt"
        private const val TWITTER_SPECIAL_RESPONSE_SERIALIZER =
            "TwitterSpecialResponseSerializer.kt"
        private const val TWITTER_WRAPPED_TWEET_SERIALIZER = "TwitterWrappedTweetSerializer.kt"
        private const val TWEET_SERIALIZER = "TweetSerializer.kt"
        private const val USER_SERIALIZER = "UserSerializer.kt"
        private const val COMPLEX_RESPONSE_SERIALIZER = "ComplexResponseSerializer.kt"
        private const val BENCH_USER_SERIALIZER = "BenchUserSerializer.kt"

        private val GENERATED_SERIALIZER_DIR = File(
            "build/generated/ksp/main/kotlin/com/ghost/serialization/integration/model",
        )
    }
}
