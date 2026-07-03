package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.ComplexResponse
import com.ghost.serialization.integration.model.ExtremeMetadata
import com.ghost.serialization.integration.model.TwitterResponse
import com.ghost.serialization.integration.model.UserRole
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies per-model `textChannel` codegen in this module: Twitter macro roots opt in;
 * synthetic benchmark models stay on the bytes/string-bridge path.
 */
class GhostTextChannelPerModelIntegrationTest {

    @Test
    fun twitterMacroRoots_generateNativeStringDeserialize() {
        assertGeneratedSourceDeclaresStringDeserialize(TWITTER_RESPONSE_SERIALIZER)
        assertGeneratedSourceDeclaresStringDeserialize(TWITTER_SPECIAL_RESPONSE_SERIALIZER)
        assertGeneratedSourceDeclaresStringDeserialize(TWITTER_WRAPPED_TWEET_SERIALIZER)
    }

    @Test
    fun twitterMacroNestedTypes_inheritTextChannelFromGraph() {
        assertGeneratedSourceDeclaresStringDeserialize(TWEET_SERIALIZER)
        assertGeneratedSourceDeclaresStringDeserialize(USER_SERIALIZER)
    }

    @Test
    fun syntheticBenchmarkModels_omitNativeStringDeserialize() {
        assertGeneratedSourceOmitsStringDeserialize(COMPLEX_RESPONSE_SERIALIZER)
        assertGeneratedSourceOmitsStringDeserialize(BENCH_USER_SERIALIZER)
    }

    @Test
    fun complexResponse_deserializeString_stillWorksViaInterfaceBridge() {
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
        assertEquals(original.status, restored.status)
        assertEquals(original.data, restored.data)
        assertEquals(original.meta.lastLogin, restored.meta.lastLogin)
        assertEquals(original.meta.role, restored.meta.role)
        assertEquals(original.meta.tags, restored.meta.tags)
        assertEquals(original.meta.precisionScore, restored.meta.precisionScore)
        assertTrue(original.meta.accessHistory.contentEquals(restored.meta.accessHistory))
    }

    @Test
    fun twitterResponse_deserializeString_usesNativeStringChannel() {
        val json = """{"statuses":[]}"""
        val restored = Ghost.deserialize<TwitterResponse>(json)
        assertTrue(restored.statuses.isEmpty())
    }

    private fun assertGeneratedSourceDeclaresStringDeserialize(serializerFileName: String) {
        val source = readGeneratedSerializerSource(serializerFileName)
        assertTrue(
            NATIVE_STRING_DESERIALIZE_SIGNATURE in source,
            "$serializerFileName must override deserialize(GhostJsonStringReader)",
        )
    }

    private fun assertGeneratedSourceOmitsStringDeserialize(serializerFileName: String) {
        val source = readGeneratedSerializerSource(serializerFileName)
        assertFalse(
            NATIVE_STRING_DESERIALIZE_SIGNATURE in source,
            "$serializerFileName must not declare deserialize(GhostJsonStringReader)",
        )
    }

    private fun readGeneratedSerializerSource(serializerFileName: String): String {
        val file = File(GENERATED_SERIALIZER_DIR, serializerFileName)
        assertTrue(file.exists(), "Missing generated serializer: ${file.absolutePath}")
        return file.readText()
    }

    private companion object {
        private const val NATIVE_STRING_DESERIALIZE_SIGNATURE =
            "override fun deserialize(reader: GhostJsonStringReader)"

        private const val TWITTER_RESPONSE_SERIALIZER = "TwitterResponseSerializer.kt"
        private const val TWITTER_SPECIAL_RESPONSE_SERIALIZER = "TwitterSpecialResponseSerializer.kt"
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
