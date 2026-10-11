@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.ghost.serialization.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * KSP regression tests for `GhostAnalyzer` validation
 * errors not covered by [GhostSerializationKspTest]: private properties, duplicate JSON names,
 * and non-String map keys.
 */
class GhostAnalyzerValidationKspTest {

    @Test
    fun rejectsPrivateProperty() {
        val (_, result) = compile(
            SourceFile.kotlin(
                "PrivateProp.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class PrivateProp(private val id: Int, val name: String)
                """.trimIndent()
            )
        )

        assertEquals(expected = KotlinCompilation.ExitCode.COMPILATION_ERROR, actual = result.exitCode)
        assertTrue(
            result.messages.contains("cannot be private", ignoreCase = true),
            result.messages
        )
    }

    @Test
    fun rejectsDuplicateJsonName() {
        val (_, result) = compile(
            SourceFile.kotlin(
                "DupName.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.annotations.GhostName

                @GhostSerialization
                data class DupName(
                    @GhostName("value") val first: Int,
                    @GhostName("value") val second: Int,
                )
                """.trimIndent()
            )
        )

        assertEquals(expected = KotlinCompilation.ExitCode.COMPILATION_ERROR, actual = result.exitCode)
        assertTrue(
            result.messages.contains("Duplicate JSON name", ignoreCase = true),
            result.messages
        )
    }

    @Test
    fun rejectsNonStringMapKey() {
        val (_, result) = compile(
            SourceFile.kotlin(
                "IntKeyedMap.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class IntKeyedMap(val counts: Map<Int, String>)
                """.trimIndent()
            )
        )

        assertEquals(expected = KotlinCompilation.ExitCode.COMPILATION_ERROR, actual = result.exitCode)
        assertTrue(
            result.messages.contains("Map key must be a String", ignoreCase = true),
            result.messages
        )
    }

    @Test
    fun acceptsStringKeyedMap() {
        val (compilation, result) = compile(
            SourceFile.kotlin(
                "StringKeyedMap.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class StringKeyedMap(val counts: Map<String, Int>)
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val kspOutput = compilation.kspSourcesDir.walk().map { it.path }.toList()
        assertTrue(
            actual = kspOutput.any { "StringKeyedMapSerializer.kt" in it },
            message = "Expected serializer: $kspOutput"
        )
    }

    @Test
    fun rejectsEnumWithoutGhostSerialization() {
        val (_, result) = compile(
            SourceFile.kotlin(
                "PlainEnumHolder.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                enum class PlainColor { RED, GREEN }

                @GhostSerialization
                data class PlainEnumHolder(val colors: List<PlainColor>)
                """.trimIndent()
            )
        )

        assertEquals(expected = KotlinCompilation.ExitCode.COMPILATION_ERROR, actual = result.exitCode)
        assertTrue(
            result.messages.contains("Enum 'PlainColor' used by property 'colors' must be annotated"),
            result.messages
        )
    }

    @Test
    fun acceptsJsonNamesThatAreNotIdentifiers() {
        val (_, result) = compile(
            SourceFile.kotlin(
                "OddNames.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostName
                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class OddNames(
                    @GhostName("renamed-key") val dashed: String,
                    @GhostName("with space") val spaced: Int,
                    @GhostName("caf\u00e9") val accented: Boolean,
                    @GhostName("renamed_key") val underscored: String,
                    @GhostName("id") val lowerId: Int,
                    @GhostName("ID") val upperId: Int,
                    @GhostName("a.b") val dotted: Int,
                    @GhostName("a_b") val underscoredAb: Int,
                )
                """.trimIndent()
            )
        )

        assertEquals(expected = KotlinCompilation.ExitCode.OK, actual = result.exitCode, message = result.messages)
    }

    private fun compile(vararg sources: SourceFile): Pair<KotlinCompilation, JvmCompilationResult> {
        val compilation = KotlinCompilation().apply {
            this.sources = sources.toList()
            inheritClassPath = true
            useKsp2()
            symbolProcessorProviders = mutableListOf(GhostSerializationProvider())
            kspWithCompilation = true
            languageVersion = "1.9"
            apiVersion = "1.9"
            jvmTarget = "17"
        }
        return compilation to compilation.compile()
    }
}
