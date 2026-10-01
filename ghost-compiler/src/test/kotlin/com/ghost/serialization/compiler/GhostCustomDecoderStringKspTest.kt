@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.ghost.serialization.compiler

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
import com.ghost.serialization.compiler.GhostEmitterTestConstants as T
import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC
import com.ghost.serialization.compiler.internal.GhostAnalyzerConstants as AC
import com.ghost.serialization.compiler.internal.GhostProcessorConstants as PC
import com.ghost.serialization.compiler.internal.GhostCodegenConstants as CG
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C

/**
 * KSP regression tests for string-native `@GhostDecoder` codegen.
 */
class GhostCustomDecoderStringKspTest {

    @Test
    fun stringNativeDecoderGeneratesDirectCallOnStringChannel() {
        val generated = compileCustomDecoderModel(textChannel = true)
        val stringDeserialize = extractStringDeserializeBlock(generated = generated)

        assertTrue(
            actual = DIRECT_DECODER_CALL in stringDeserialize,
            message = "Expected direct string-native decoder call:\n$stringDeserialize"
        )
        assertFalse(
            actual = "reader.rawData.encodeToByteArray()" in stringDeserialize,
            message = "String-native decoder must not UTF-8 encode the full payload:\n$stringDeserialize"
        )
    }

    @Test
    fun bytesOnlyDecoderKeepsBridgeOnStringChannel() {
        val generated = compileCustomDecoderModel(
            textChannel = true,
            decoderSource = bytesOnlyDecoderUtilsSource(),
        )
        val stringDeserialize = extractStringDeserializeBlock(generated = generated)

        assertFalse(
            actual = "reader.rawData.encodeToByteArray()" in stringDeserialize,
            message = "Legacy bridge must not re-encode rawData on every field:\n$stringDeserialize"
        )
        assertTrue(
            actual = CG.STR_ENSURE_UTF8_BYTES in stringDeserialize,
            message = "Bytes-only decoder should use cached UTF-8 bridge on string channel:\n$stringDeserialize"
        )
    }

    private fun extractStringDeserializeBlock(generated: String): String {
        return generated.substringAfter(T.STR_OVERRIDE_DESERIALIZE_STRING_READER)
            .substringBefore(T.STR_OVERRIDE_SERIALIZE_FN)
    }

    private fun compileCustomDecoderModel(
        textChannel: Boolean,
        decoderSource: String = stringNativeDecoderUtilsSource(),
    ): String {
        val compilation = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin("${T.STR_TEST_DECODER_UTILS}.kt", decoderSource),
                SourceFile.kotlin(
                    "${T.STR_TEST_CUSTOM_FIELD_MODEL}.kt",
                    customFieldModelSource(),
                ),
            )
            inheritClassPath = true
            symbolProcessorProviders = mutableListOf(GhostSerializationProvider())
            kspProcessorOptions = mutableMapOf(
                PC.OPTION_TEXT_CHANNEL to if (textChannel) CC.STR_TRUE else CC.STR_FALSE
            )
            kspWithCompilation = true
            languageVersion = "1.9"
            apiVersion = "1.9"
            // kctfork's kotlinc (2.1.0) can't read metadata from newer-Kotlin (2.4.0) project
            // jars via inheritClassPath — this flag skips the strict metadata-version check.
            kotlincArguments = listOf("-Xskip-metadata-version-check")
            jvmTarget = "17"
        }
        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val serializerFileName =
            "${T.STR_TEST_CUSTOM_FIELD_MODEL}${T.STR_KT_SERIALIZER_FILE_SUFFIX}"
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
        data class ${T.STR_TEST_CUSTOM_FIELD_MODEL}(
            val id: String,
            @GhostDecoder(${T.STR_TEST_DECODER_UTILS}::class, ${T.STR_TEST_CUSTOM_DECODER_FN.quote()})
            val secret: String,
        )
    """.trimIndent()

    private fun stringNativeDecoderUtilsSource(): String = """
        package fixtures

        import ${AC.STR_GHOST_JSON_READER_QUALIFIED}
        import ${AC.STR_GHOST_JSON_STRING_READER_QUALIFIED}

        object ${T.STR_TEST_DECODER_UTILS} {
            fun ${T.STR_TEST_CUSTOM_DECODER_FN}(reader: ${CG.STR_GHOST_JSON_READER}): String = ${T.STR_TEST_BYTES_RESULT.quote()}
            fun ${T.STR_TEST_CUSTOM_DECODER_FN}(reader: ${C.STR_GHOST_JSON_STRING_READER}): String = ${T.STR_TEST_NATIVE_RESULT.quote()}
        }
    """.trimIndent()

    private fun bytesOnlyDecoderUtilsSource(): String = """
        package fixtures

        import ${AC.STR_GHOST_JSON_READER_QUALIFIED}

        object ${T.STR_TEST_DECODER_UTILS} {
            fun ${T.STR_TEST_CUSTOM_DECODER_FN}(reader: ${CG.STR_GHOST_JSON_READER}): String = ${T.STR_TEST_BYTES_RESULT.quote()}
        }
    """.trimIndent()

    private fun String.quote(): String = "\"$this\""

    private companion object {
        const val DIRECT_DECODER_CALL =
            "${T.STR_TEST_DECODER_UTILS}.${T.STR_TEST_CUSTOM_DECODER_FN}(reader)"
    }
}
