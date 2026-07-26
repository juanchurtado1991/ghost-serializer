package com.ghost.serialization.compiler

import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCompilerApi::class)
class DefaultExpressionSingleShotKspTest {

    @Test
    fun nonZeroIntDefaultsUseSingleShotWithoutCopy() {
        val generated = compileAndReadSerializer(
            SourceFile.kotlin(
                "ManyDefaults.kt",
                """
                package test
                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class ManyDefaults(
                    val p1: Int = 1,
                    val p2: Int = 2,
                    val p3: Int = 3,
                    val p4: Int = 4,
                    val p5: Int = 5,
                )
                """.trimIndent()
            ),
            "ManyDefaultsSerializer.kt"
        )
        assertFalse("result.copy(" in generated, generated)
        assertTrue("else 1" in generated, generated)
        assertTrue("else 5" in generated, generated)
        assertTrue("val result = " in generated && "ManyDefaults(" in generated, generated)
    }

    @Test
    fun complexLiteralDefaultsUseSingleShot() {
        val generated = compileAndReadSerializer(
            SourceFile.kotlin(
                "ComplexDefaults.kt",
                """
                package test
                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                enum class Priority { LOW, HIGH }

                @GhostSerialization
                data class ComplexDefaults(
                    val id: Int,
                    val nullableName: String? = null,
                    val defaultRole: String = "viewer",
                    val defaultPriority: Priority = Priority.LOW,
                    val defaultCount: Int = 0,
                    val tags: List<String> = emptyList(),
                )
                """.trimIndent()
            ),
            "ComplexDefaultsSerializer.kt"
        )
        // 5 defaults > MAX_DEFAULT_BRANCH_COUNT(4) → createInstance path
        assertFalse("result.copy(" in generated, generated)
        assertTrue("else \"viewer\"" in generated, generated)
        assertTrue("else Priority.LOW" in generated, generated)
        assertTrue("else emptyList()" in generated, generated)
        // Defaults that match local init (null / 0) omit the mask ternary.
        assertTrue("nullableName = nullableNameValue" in generated, generated)
        assertTrue("defaultCount = defaultCountValue" in generated, generated)
        assertFalse("else null" in generated, generated)
    }

    @Test
    fun dependentDefaultFallsBackToCopy() {
        val generated = compileAndReadSerializer(
            SourceFile.kotlin(
                "DependentDefaults.kt",
                """
                package test
                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class DependentDefaults(
                    val a: Int = 1,
                    val b: Int = a + 1,
                    val c: Int = 3,
                    val d: Int = 4,
                    val e: Int = 5,
                )
                """.trimIndent()
            ),
            "DependentDefaultsSerializer.kt"
        )
        assertTrue("result.copy(" in generated, generated)
        assertFalse("else a + 1" in generated, generated)
    }

    @Test
    fun annotatedParameterStillExtractsDefault() {
        val generated = compileAndReadSerializer(
            SourceFile.kotlin(
                "AnnotatedDefaults.kt",
                """
                package test
                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.annotations.GhostName

                @GhostSerialization
                data class AnnotatedDefaults(
                    @GhostName("n") val name: String = "x",
                    val a: Int = 1,
                    val b: Int = 2,
                    val c: Int = 3,
                    val d: Int = 4,
                )
                """.trimIndent()
            ),
            "AnnotatedDefaultsSerializer.kt"
        )
        assertFalse("result.copy(" in generated, generated)
        assertTrue("else \"x\"" in generated, generated)
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
