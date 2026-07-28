@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.ghost.serialization.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** KSP regression tests for `@GhostProtoSerialization` YAML codegen paths. */
class GhostProtoYamlCodegenKspTest {

    @Test
    fun protoModelGeneratesYamlSerializerWithQuotedInt64Write() {
        val generated = compileAndReadSerializer(
            SourceFile.kotlin(
                "ProtoYamlCounter.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostProtoSerialization

                @GhostProtoSerialization
                data class ProtoYamlCounter(val request_id: Long, val retries: Int)
                """.trimIndent()
            ),
            serializerFileName = "ProtoYamlCounterSerializer.kt"
        )

        assertTrue(
            "GhostYamlSerializer<ProtoYamlCounter>" in generated,
            "Expected GhostYamlSerializer superinterface:\n$generated"
        )
        assertTrue(
            "writer.value(value.request_id.toString())" in generated,
            "Expected quoted int64 YAML write for request_id:\n$generated"
        )
        assertTrue(
            "override fun deserialize(reader: GhostYamlFlatReader" in generated,
            "Expected YAML deserialize method:\n$generated"
        )
    }

    @Test
    fun protoByteArrayUsesBase64OnYamlSerializePath() {
        val generated = compileAndReadSerializer(
            SourceFile.kotlin(
                "ProtoYamlBlob.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostProtoSerialization

                @GhostProtoSerialization
                data class ProtoYamlBlob(val payload: ByteArray)
                """.trimIndent()
            ),
            serializerFileName = "ProtoYamlBlobSerializer.kt"
        )

        assertTrue(
            "writer.value(encodeBase64String(value.payload))" in generated,
            "Expected Base64 YAML write for proto ByteArray:\n$generated"
        )
    }

    private fun compileAndReadSerializer(source: SourceFile, serializerFileName: String): String {
        val (compilation, result) = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        return compilation.kspSourcesDir.walk()
            .filter { it.name == serializerFileName }
            .map { it.readText() }
            .first()
    }

    private fun compile(vararg sources: SourceFile): Pair<KotlinCompilation, JvmCompilationResult> {
        val compilation = KotlinCompilation().apply {
            this.sources = sources.toList()
            inheritClassPath = true
            symbolProcessorProviders = mutableListOf(GhostSerializationProvider())
            kspWithCompilation = true
            languageVersion = "1.9"
            apiVersion = "1.9"
            kotlincArguments = listOf("-Xskip-metadata-version-check")
            jvmTarget = "17"
        }
        return compilation to compilation.compile()
    }
}
