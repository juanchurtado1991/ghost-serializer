@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.ghost.serialization.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** KSP regression tests for generated [GhostYamlSerializer] companions. */
class GhostYamlCodegenKspTest {

    @Test
    fun generatesYamlSerializeAndDeserializeMethods() {
        val generated = compileAndReadSerializer(
            SourceFile.kotlin(
                "YamlUser.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class YamlUser(val id: Int, val name: String)
                """.trimIndent()
            ),
            serializerFileName = "YamlUserSerializer.kt"
        )

        assertTrue(
            "GhostYamlSerializer<YamlUser>" in generated,
            "Expected GhostYamlSerializer superinterface:\n$generated"
        )
        assertTrue(
            "override fun serialize(writer: GhostYamlFlatWriter" in generated,
            "Expected YAML flat serialize method:\n$generated"
        )
        assertTrue(
            "override fun deserialize(reader: GhostYamlFlatReader" in generated,
            "Expected YAML flat deserialize method:\n$generated"
        )
    }

    @Test
    fun skipsYamlWhenPropertyUsesCustomEncoder() {
        val generated = compileKspOnly(
            SourceFile.kotlin(
                "CustomYamlModel.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostEncoder
                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter

                @GhostSerialization
                data class CustomYamlModel(
                    @GhostEncoder(provider = Encoder::class, functionName = "encode")
                    val token: String,
                )

                object Encoder {
                    fun encode(writer: GhostJsonFlatWriter, value: String) {
                        writer.value(value)
                    }
                }
                """.trimIndent()
            ),
            serializerFileName = "CustomYamlModelSerializer.kt"
        )

        assertFalse(
            "GhostYamlSerializer" in generated,
            "Custom encoder properties must disable YAML codegen:\n$generated"
        )
    }

    @Test
    fun primitiveIntArrayUsesGhostYamlIntArraySerializer() {
        val generated = compileAndReadSerializer(
            SourceFile.kotlin(
                "YamlScores.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class YamlScores(val values: IntArray)
                """.trimIndent()
            ),
            serializerFileName = "YamlScoresSerializer.kt"
        )

        assertTrue(
            "GhostYamlIntArraySerializer.serialize(writer, value.values)" in generated,
            "Expected YAML primitive array serializer on write path:\n$generated"
        )
        assertTrue(
            "GhostYamlIntArraySerializer.deserialize(reader)" in generated,
            "Expected YAML primitive array serializer on read path:\n$generated"
        )
    }

    @Test
    fun skipsYamlWhenGenerateYamlOptionIsFalse() {
        val generated = compileKspOnly(
            mapOf("ghost.generateYaml" to "false"),
            SourceFile.kotlin(
                "YamlOptOutUser.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class YamlOptOutUser(val id: Int, val name: String)
                """.trimIndent()
            ),
            serializerFileName = "YamlOptOutUserSerializer.kt"
        )

        assertFalse(
            "GhostYamlSerializer" in generated,
            "ghost.generateYaml=false must disable YAML codegen:\n$generated"
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

    private fun compileKspOnly(
        options: Map<String, String>,
        source: SourceFile,
        serializerFileName: String,
    ): String {
        val compilation = KotlinCompilation().apply {
            sources = listOf(source)
            inheritClassPath = true
            symbolProcessorProviders = mutableListOf(GhostSerializationProvider())
            kspWithCompilation = false
            kspProcessorOptions = options.toMutableMap()
            languageVersion = "1.9"
            apiVersion = "1.9"
            kotlincArguments = listOf("-Xskip-metadata-version-check")
            jvmTarget = "17"
        }
        val result = compilation.compile()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        return compilation.kspSourcesDir.walk()
            .filter { it.name == serializerFileName }
            .map { it.readText() }
            .first()
    }

    private fun compileKspOnly(source: SourceFile, serializerFileName: String): String =
        compileKspOnly(emptyMap(), source, serializerFileName)

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
