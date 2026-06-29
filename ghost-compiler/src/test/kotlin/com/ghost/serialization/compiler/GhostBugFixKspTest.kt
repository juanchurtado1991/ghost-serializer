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

    // Fix 1 + Fix 2: classDeclaration private val + @GhostFallback on enum
    @Test
    fun compilesEnumWithGhostFallbackAnnotation() {
        val (compilation, result) = compile(
            SourceFile.kotlin(
                "DeviceStatus.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostFallback
                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                enum class DeviceStatus {
                    ONLINE,
                    OFFLINE,
                    @GhostFallback
                    UNKNOWN_STATUS
                }
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val generated = compilation.kspSourcesDir.walk().filter { it.name == "DeviceStatusSerializer.kt" }
            .map { it.readText() }.firstOrNull()
        assertTrue(generated != null, "DeviceStatusSerializer.kt not generated")
        assertTrue(
            "UNKNOWN_STATUS" in generated,
            "Expected else -> UNKNOWN_STATUS fallback branch in generated code:\n$generated"
        )
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

    // Fix 5 (typealias): typealias = Map compiles without generating broken code
    @Test
    fun compilesModelWithTypealiasMap() {
        val (compilation, result) = compile(
            SourceFile.kotlin(
                "AttributeModel.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                typealias AttributeMap = Map<String, String>

                @GhostSerialization
                data class AttributeModel(val id: String, val attributes: AttributeMap)
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val kspOutput = compilation.kspSourcesDir.walk().map { it.path }.toList()
        assertTrue(kspOutput.any { "AttributeModelSerializer.kt" in it }, "Expected serializer: $kspOutput")
    }

    // Fix 5 (typealias): typealias = List compiles without generating broken code
    @Test
    fun compilesModelWithTypeAliasList() {
        val (compilation, result) = compile(
            SourceFile.kotlin(
                "TaggedModel.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                typealias TagList = List<String>

                @GhostSerialization
                data class TaggedModel(val id: String, val tags: TagList)
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val kspOutput = compilation.kspSourcesDir.walk().map { it.path }.toList()
        assertTrue(kspOutput.any { "TaggedModelSerializer.kt" in it }, "Expected serializer: $kspOutput")
    }

    // Fix 5 (typealias): typealias = String compiles without generating broken code
    @Test
    fun compilesModelWithTypeAliasString() {
        val (compilation, result) = compile(
            SourceFile.kotlin(
                "NamedModel.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                typealias DeviceId = String

                @GhostSerialization
                data class NamedModel(val id: DeviceId, val label: String)
                """.trimIndent()
            )
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        val kspOutput = compilation.kspSourcesDir.walk().map { it.path }.toList()
        assertTrue(kspOutput.any { "NamedModelSerializer.kt" in it }, "Expected serializer: $kspOutput")
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
