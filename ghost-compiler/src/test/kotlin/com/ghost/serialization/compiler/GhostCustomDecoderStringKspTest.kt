@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.ghost.serialization.compiler

import com.ghost.serialization.compiler.GhostEmitterConstants as C
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * KSP regression tests for string-native `@GhostDecoder` codegen.
 */
class GhostCustomDecoderStringKspTest {

    @Test
    fun stringNativeDecoderGeneratesDirectCallOnStringChannel() {
        val generated = compileCustomDecoderModel(textChannel = true)
        val stringDeserialize = extractStringDeserializeBlock(generated)

        assertTrue(
            DIRECT_DECODER_CALL in stringDeserialize,
            "Expected direct string-native decoder call:\n$stringDeserialize"
        )
        assertFalse(
            "reader.rawData.encodeToByteArray()" in stringDeserialize,
            "String-native decoder must not UTF-8 encode the full payload:\n$stringDeserialize"
        )
    }

    @Test
    fun bytesOnlyDecoderKeepsBridgeOnStringChannel() {
        val generated = compileCustomDecoderModel(
            textChannel = true,
            decoderSource = bytesOnlyDecoderUtilsSource(),
        )
        val stringDeserialize = extractStringDeserializeBlock(generated)

        assertFalse(
            "reader.rawData.encodeToByteArray()" in stringDeserialize,
            "Legacy bridge must not re-encode rawData on every field:\n$stringDeserialize"
        )
        assertTrue(
            C.STR_ENSURE_UTF8_BYTES in stringDeserialize,
            "Bytes-only decoder should use cached UTF-8 bridge on string channel:\n$stringDeserialize"
        )
    }

    private fun extractStringDeserializeBlock(generated: String): String {
        return generated.substringAfter(C.STR_OVERRIDE_DESERIALIZE_STRING_READER)
            .substringBefore(C.STR_OVERRIDE_SERIALIZE_FN)
    }

    private fun compileCustomDecoderModel(
        textChannel: Boolean,
        decoderSource: String = stringNativeDecoderUtilsSource(),
    ): String {
        val compilation = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin("${C.STR_TEST_DECODER_UTILS}.kt", decoderSource),
                SourceFile.kotlin(
                    "${C.STR_TEST_CUSTOM_FIELD_MODEL}.kt",
                    customFieldModelSource(),
                ),
            )
            inheritClassPath = true
            symbolProcessorProviders = mutableListOf(GhostSerializationProvider())
            kspProcessorOptions = mutableMapOf(
                C.OPTION_TEXT_CHANNEL to if (textChannel) C.STR_TRUE else C.STR_FALSE
            )
            kspWithCompilation = true
            languageVersion = "1.9"
            apiVersion = "1.9"
            jvmTarget = "17"
        }
        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val serializerFileName = "${C.STR_TEST_CUSTOM_FIELD_MODEL}${C.STR_KT_SERIALIZER_FILE_SUFFIX}"
        return compilation.kspSourcesDir.walk()
            .filter { it.name == serializerFileName }
            .map { it.readText() }
            .first()
    }

    private fun customFieldModelSource(): String = """
        package fixtures

        import com.ghost.serialization.annotations.GhostDecoder
        import com.ghost.serialization.annotations.GhostSerialization

        @GhostSerialization
        data class ${C.STR_TEST_CUSTOM_FIELD_MODEL}(
            val id: String,
            @GhostDecoder(${C.STR_TEST_DECODER_UTILS}::class, ${C.STR_TEST_CUSTOM_DECODER_FN.quote()})
            val secret: String,
        )
    """.trimIndent()

    private fun stringNativeDecoderUtilsSource(): String = """
        package fixtures

        import ${C.STR_GHOST_JSON_READER_QUALIFIED}
        import ${C.STR_GHOST_JSON_STRING_READER_QUALIFIED}

        object ${C.STR_TEST_DECODER_UTILS} {
            fun ${C.STR_TEST_CUSTOM_DECODER_FN}(reader: ${C.STR_GHOST_JSON_READER}): String = ${C.STR_TEST_BYTES_RESULT.quote()}
            fun ${C.STR_TEST_CUSTOM_DECODER_FN}(reader: ${C.STR_GHOST_JSON_STRING_READER}): String = ${C.STR_TEST_NATIVE_RESULT.quote()}
        }
    """.trimIndent()

    private fun bytesOnlyDecoderUtilsSource(): String = """
        package fixtures

        import ${C.STR_GHOST_JSON_READER_QUALIFIED}

        object ${C.STR_TEST_DECODER_UTILS} {
            fun ${C.STR_TEST_CUSTOM_DECODER_FN}(reader: ${C.STR_GHOST_JSON_READER}): String = ${C.STR_TEST_BYTES_RESULT.quote()}
        }
    """.trimIndent()

    private fun String.quote(): String = "\"$this\""

    private companion object {
        const val DIRECT_DECODER_CALL =
            "${C.STR_TEST_DECODER_UTILS}.${C.STR_TEST_CUSTOM_DECODER_FN}(reader)"
    }
}
