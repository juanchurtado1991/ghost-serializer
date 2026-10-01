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

/** KSP regression tests for `@GhostYamlSerialization` codegen. */
class GhostYamlCodegenKspTest {

    @Test
    fun generatesYamlSerializeAndDeserializeMethodsWhenYamlAnnotationPresent() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
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
            actual = "GhostYamlSerializer<YamlUser>" in generated,
            message = "Expected GhostYamlSerializer superinterface:\n$generated"
        )
        assertTrue(
            actual = "override fun serialize(writer: GhostYamlWriter" in generated,
            message = "Expected YAML serialize method:\n$generated"
        )
        assertTrue(
            actual = "override fun deserialize(reader: GhostYamlFlatReader" in generated,
            message = "Expected YAML flat deserialize method:\n$generated"
        )
        assertFalse(
            actual = "decodeResilient" in generated,
            message = "YAML deserialize path must not use decodeResilient:\n$generated"
        )
    }

    @Test
    fun skipsYamlWithoutGhostYamlSerializationAnnotation() {
        val generated = compileKspOnly(
            source = SourceFile.kotlin(
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
            actual = "GhostYamlSerializer" in generated,
            message = "YAML codegen requires @GhostYamlSerialization:\n$generated"
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
                import com.ghost.serialization.writer.bytes.GhostJsonWriter

                @GhostSerialization
                @GhostYamlSerialization
                data class CustomYamlModel(
                    @GhostEncoder(provider = Encoder::class, functionName = "encode")
                    val token: String,
                )

                object Encoder {
                    fun encode(writer: GhostJsonWriter, value: String) {
                        writer.value(value)
                    }
                }
                """.trimIndent()
            )
        )

        assertEquals(expected = KotlinCompilation.ExitCode.COMPILATION_ERROR, actual = result.exitCode)
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

        assertEquals(expected = KotlinCompilation.ExitCode.COMPILATION_ERROR, actual = result.exitCode)
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

        assertEquals(expected = KotlinCompilation.ExitCode.COMPILATION_ERROR, actual = result.exitCode)
        assertTrue(
            result.messages.contains(
                "requires @GhostSerialization or @GhostProtoSerialization",
                ignoreCase = true
            ),
            result.messages
        )
    }

    @Test
    fun primitiveIntArrayUsesGhostYamlIntArraySerializer() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
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
            actual = "GhostYamlIntArraySerializer.serialize(writer, value.values)" in generated,
            message = "Expected YAML primitive array serializer on write path:\n$generated"
        )
        assertTrue(
            actual = "GhostYamlIntArraySerializer.deserialize(reader)" in generated,
            message = "Expected YAML primitive array serializer on read path:\n$generated"
        )
    }

    @Test
    fun primitiveLongArrayUsesGhostYamlLongArraySerializer() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "YamlLongScores.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.annotations.GhostYamlSerialization

                @GhostSerialization
                @GhostYamlSerialization
                data class YamlLongScores(val values: LongArray)
                """.trimIndent()
            ),
            serializerFileName = "YamlLongScoresSerializer.kt"
        )

        assertTrue(
            actual = "GhostYamlLongArraySerializer.serialize(writer, value.values)" in generated,
            message = "Expected YAML LongArray serializer on write path:\n$generated"
        )
        assertTrue(
            actual = "GhostYamlLongArraySerializer.deserialize(reader)" in generated,
            message = "Expected YAML LongArray serializer on read path:\n$generated"
        )
    }

    @Test
    fun primitiveFloatArrayUsesGhostYamlFloatArraySerializer() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "YamlFloatScores.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.annotations.GhostYamlSerialization

                @GhostSerialization
                @GhostYamlSerialization
                data class YamlFloatScores(val values: FloatArray)
                """.trimIndent()
            ),
            serializerFileName = "YamlFloatScoresSerializer.kt"
        )

        assertTrue(
            actual = "GhostYamlFloatArraySerializer.serialize(writer, value.values)" in generated,
            message = "Expected YAML FloatArray serializer on write path:\n$generated"
        )
        assertTrue(
            actual = "GhostYamlFloatArraySerializer.deserialize(reader)" in generated,
            message = "Expected YAML FloatArray serializer on read path:\n$generated"
        )
    }

    @Test
    fun primitiveDoubleArrayUsesGhostYamlDoubleArraySerializer() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "YamlDoubleScores.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.annotations.GhostYamlSerialization

                @GhostSerialization
                @GhostYamlSerialization
                data class YamlDoubleScores(val values: DoubleArray)
                """.trimIndent()
            ),
            serializerFileName = "YamlDoubleScoresSerializer.kt"
        )

        assertTrue(
            actual = "GhostYamlDoubleArraySerializer.serialize(writer, value.values)" in generated,
            message = "Expected YAML DoubleArray serializer on write path:\n$generated"
        )
        assertTrue(
            actual = "GhostYamlDoubleArraySerializer.deserialize(reader)" in generated,
            message = "Expected YAML DoubleArray serializer on read path:\n$generated"
        )
    }

    @Test
    fun primitiveBooleanArrayUsesGhostYamlBooleanArraySerializer() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "YamlFlags.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.annotations.GhostYamlSerialization

                @GhostSerialization
                @GhostYamlSerialization
                data class YamlFlags(val values: BooleanArray)
                """.trimIndent()
            ),
            serializerFileName = "YamlFlagsSerializer.kt"
        )

        assertTrue(
            actual = "GhostYamlBooleanArraySerializer.serialize(writer, value.values)" in generated,
            message = "Expected YAML BooleanArray serializer on write path:\n$generated"
        )
        assertTrue(
            actual = "GhostYamlBooleanArraySerializer.deserialize(reader)" in generated,
            message = "Expected YAML BooleanArray serializer on read path:\n$generated"
        )
    }

    @Test
    fun plainULongFieldUsesNextULongOnYamlDeserialize() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "YamlShard.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.annotations.GhostYamlSerialization

                @GhostSerialization
                @GhostYamlSerialization
                data class YamlShard(val shard_id: ULong)
                """.trimIndent()
            ),
            serializerFileName = "YamlShardSerializer.kt"
        )

        assertTrue(
            actual = "reader.nextULong()" in generated,
            message = "Expected plain ULong YAML scalar reader:\n$generated"
        )
        assertFalse(
            actual = "ULongSerializer" in generated,
            message = "Plain ULong must not require contextual serializer:\n$generated"
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
