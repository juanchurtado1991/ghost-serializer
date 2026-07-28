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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** KSP regression tests for opt-in [@GhostYamlSerialization] codegen. */
class GhostYamlCodegenKspTest {

    @Test
    fun generatesYamlSerializeAndDeserializeMethodsWhenYamlAnnotationPresent() {
        val generated = compileAndReadSerializer(
            SourceFile.kotlin(
                "YamlUser.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.annotations.GhostYamlSerialization

                @GhostSerialization
                @GhostYamlSerialization
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
        assertFalse(
            "decodeResilient" in generated,
            "YAML deserialize path must not use decodeResilient:\n$generated"
        )
    }

    @Test
    fun skipsYamlWithoutGhostYamlSerializationAnnotation() {
        val generated = compileKspOnly(
            SourceFile.kotlin(
                "JsonOnlyUser.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class JsonOnlyUser(val id: Int, val name: String)
                """.trimIndent()
            ),
            serializerFileName = "JsonOnlyUserSerializer.kt"
        )

        assertFalse(
            "GhostYamlSerializer" in generated,
            "YAML codegen requires @GhostYamlSerialization:\n$generated"
        )
    }

    @Test
    fun rejectsYamlWhenPropertyUsesCustomEncoder() {
        val (_, result) = compile(
            SourceFile.kotlin(
                "CustomYamlModel.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostEncoder
                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.annotations.GhostYamlSerialization
                import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter

                @GhostSerialization
                @GhostYamlSerialization
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
            )
        )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            result.messages.contains("@GhostDecoder/@GhostEncoder", ignoreCase = true),
            result.messages
        )
    }

    @Test
    fun rejectsResilientCombinedWithGhostYamlSerialization() {
        val (_, result) = compile(
            SourceFile.kotlin(
                "ResilientYamlUser.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostResilient
                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.annotations.GhostYamlSerialization

                @GhostSerialization
                @GhostYamlSerialization
                @GhostResilient
                data class ResilientYamlUser(val id: Int, val name: String)
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            result.messages.contains("@GhostResilient is JSON-only", ignoreCase = true),
            result.messages
        )
    }

    @Test
    fun rejectsOrphanGhostYamlSerialization() {
        val (_, result) = compile(
            SourceFile.kotlin(
                "OrphanYaml.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostYamlSerialization

                @GhostYamlSerialization
                data class OrphanYaml(val id: Int)
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            result.messages.contains("requires @GhostSerialization or @GhostProtoSerialization", ignoreCase = true),
            result.messages
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
                import com.ghost.serialization.annotations.GhostYamlSerialization

                @GhostSerialization
                @GhostYamlSerialization
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

    private fun compileAndReadSerializer(source: SourceFile, serializerFileName: String): String {
        val (compilation, result) = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        return compilation.kspSourcesDir.walk()
            .filter { it.name == serializerFileName }
            .map { it.readText() }
            .first()
    }

    private fun compileKspOnly(source: SourceFile, serializerFileName: String): String {
        val compilation = KotlinCompilation().apply {
            sources = listOf(source)
            inheritClassPath = true
            symbolProcessorProviders = mutableListOf(GhostSerializationProvider())
            kspWithCompilation = false
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
