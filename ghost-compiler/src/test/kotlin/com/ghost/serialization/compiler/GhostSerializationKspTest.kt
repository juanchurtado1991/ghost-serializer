@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.ghost.serialization.compiler

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GhostSerializationKspTest {

    @Test
    fun generatesSerializerForDataClass() {
        val (compilation, result) = compile(
            SourceFile.kotlin(
                "User.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class User(val id: Int, val name: String)
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val kspOutput = compilation.kspSourcesDir.walk().map { it.path }.toList()
        assertTrue(
            kspOutput.any { "UserSerializer.kt" in it },
            "Expected UserSerializer.kt in ksp output: $kspOutput"
        )
    }

    @Test
    fun generatesModuleRegistry() {
        val (compilation, result) = compile(
            SourceFile.kotlin(
                "Product.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class Product(val sku: String, val price: Double)
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val kspOutput = compilation.kspSourcesDir.walk().map { it.path }.toList()
        assertTrue(kspOutput.any { "GhostModuleRegistry" in it }, "Expected registry: $kspOutput")
    }

    @Test
    fun generatesEnumSerializer() {
        val (compilation, result) = compile(
            SourceFile.kotlin(
                "Status.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                enum class Status { ACTIVE, INACTIVE }
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val kspOutput = compilation.kspSourcesDir.walk().map { it.path }.toList()
        assertTrue(kspOutput.any { "StatusSerializer.kt" in it }, "Expected enum serializer: $kspOutput")
    }

    @Test
    fun failsForNonDataClass() {
        val (_, result) = compile(
            SourceFile.kotlin(
                "Bad.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                class Bad(val id: Int)
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.COMPILATION_ERROR, result.exitCode)
        assertTrue(
            result.messages.contains("data class", ignoreCase = true) ||
                result.messages.contains("GhostSerialization", ignoreCase = true),
            result.messages
        )
    }

    private fun compile(vararg sources: SourceFile): Pair<KotlinCompilation, KotlinCompilation.Result> {
        val compilation = KotlinCompilation().apply {
            this.sources = sources.toList()
            inheritClassPath = true
            kotlincArguments = listOf("-Xskip-metadata-version-check")
            symbolProcessorProviders = listOf(GhostSerializationProvider())
            // KSP-only: generated serializers depend on ghost-serialization runtime (integration-test covers full compile).
            kspWithCompilation = false
        }
        return compilation to compilation.compile()
    }
}
