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
 * Compilation-level regression tests for bugs fixed in 1.2.3.
 *
 * Each test represents a pattern that previously either failed to compile,
 * generated broken code, or crashed at class-initialization time.
 */
class GhostBugFixKspTest {

    // Fix 1 + Fix 2: classDeclaration private val + @GhostFallback on sealed subclass
    @Test
    fun compilesGhostFallbackOnSealedSubclass() {
        val (compilation, result) = compile(
            SourceFile.kotlin(
                "Vehicle.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostFallback
                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                sealed class Vehicle {
                    @GhostSerialization
                    data class Car(val brand: String) : Vehicle()
                    @GhostFallback
                    @GhostSerialization
                    data class Unknown(val raw: String = "") : Vehicle()
                }
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val kspOutput = compilation.kspSourcesDir.walk().map { it.path }.toList()
        assertTrue(kspOutput.any { "VehicleSerializer.kt" in it }, "Expected VehicleSerializer.kt: $kspOutput")
    }

    // Fix 3 (auto-UNKNOWN): enum with UNKNOWN constant auto-generates fallback
    @Test
    fun compilesEnumWithAutoUnknownFallback() {
        val (compilation, result) = compile(
            SourceFile.kotlin(
                "ConnectionState.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                enum class ConnectionState { CONNECTED, DISCONNECTED, UNKNOWN }
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val generated = compilation.kspSourcesDir.walk().filter { it.name == "ConnectionStateSerializer.kt" }
            .map { it.readText() }.firstOrNull()
        assertTrue(generated != null, "ConnectionStateSerializer.kt not generated")
        assertTrue(
            "else -> ConnectionState.UNKNOWN" in (generated ?: ""),
            "Expected auto-UNKNOWN else branch in generated code:\n$generated"
        )
    }

    // Fix 3: enum with lowercase 'unknown' also gets auto-fallback
    @Test
    fun compilesEnumWithLowercaseUnknownFallback() {
        val (compilation, result) = compile(
            SourceFile.kotlin(
                "SyncState.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                enum class SyncState { SYNCED, PENDING, unknown }
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val generated = compilation.kspSourcesDir.walk().filter { it.name == "SyncStateSerializer.kt" }
            .map { it.readText() }.firstOrNull()
        assertTrue(generated != null, "SyncStateSerializer.kt not generated")
        assertTrue(
            "else -> SyncState.unknown" in (generated ?: ""),
            "Expected auto-unknown else branch in generated code:\n$generated"
        )
    }

    // Fix 4: ByteArray field compiles and generates isByteArray path
    @Test
    fun compilesModelWithByteArrayField() {
        val (compilation, result) = compile(
            SourceFile.kotlin(
                "RawPayload.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class RawPayload(val id: String, val body: ByteArray)
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val kspOutput = compilation.kspSourcesDir.walk().map { it.path }.toList()
        assertTrue(kspOutput.any { "RawPayloadSerializer.kt" in it }, "Expected RawPayloadSerializer.kt: $kspOutput")
    }

    private fun compile(vararg sources: SourceFile): Pair<KotlinCompilation, JvmCompilationResult> {
        val compilation = KotlinCompilation().apply {
            this.sources = sources.toList()
            inheritClassPath = true
            symbolProcessorProviders = mutableListOf(GhostSerializationProvider())
            kspWithCompilation = true
            languageVersion = "1.9"
            apiVersion = "1.9"
        }
        return compilation to compilation.compile()
    }
}
