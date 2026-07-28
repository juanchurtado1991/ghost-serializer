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

/**
 * KSP regression tests for [com.ghost.serialization.compiler.analysis.GhostAnalyzer] validation
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

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
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

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
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

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
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
            kspOutput.any { "StringKeyedMapSerializer.kt" in it },
            "Expected serializer: $kspOutput"
        )
    }

    private fun compile(vararg sources: SourceFile): Pair<KotlinCompilation, JvmCompilationResult> {
        val compilation = KotlinCompilation().apply {
            this.sources = sources.toList()
            inheritClassPath = true
            symbolProcessorProviders = mutableListOf(GhostSerializationProvider())
            kspWithCompilation = true
            languageVersion = "1.9"
            apiVersion = "1.9"
            // kctfork's embedded kotlinc (2.1.0) can't read metadata from our project's own
            // jars once they're compiled with a newer Kotlin (2.4.0 as of this bump) via
            // inheritClassPath — this flag skips that strict metadata-version check.
            kotlincArguments = listOf("-Xskip-metadata-version-check")
            jvmTarget = "17"
        }
        return compilation to compilation.compile()
    }
}
