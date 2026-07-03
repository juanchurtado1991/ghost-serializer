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

class GhostWrappedKeysKspTest {

    @Test
    fun generatesWrappedKeysFixtureParity() {
        val (compilation, result) = compile(
            SourceFile.kotlin(
                "WireExtras.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostName
                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.annotations.GhostWrappedKeys

                @GhostSerialization
                data class WireExtras(
                    @GhostName("extra1") val extra1: String?,
                    @GhostName("extra2") val extra2: String?,
                    @GhostName("extra3") val extra3: String?,
                    @GhostName("extra4") val extra4: String?,
                )

                @GhostSerialization
                data class WrappedKeysFixture(
                    val id: String,
                    @GhostWrappedKeys(keys = ["extra1", "extra2", "extra3", "extra4"])
                    @GhostName("extras")
                    val extras: WireExtras,
                )
                """.trimIndent(),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        lastCompilation = compilation
        val source = compilationOutput("WrappedKeysFixtureSerializer.kt")
        assertTrue("extra1" in source, source)
        assertTrue("captureWrappedKey" in source, source)
        assertTrue("materializeWrappedObject" in source, source)
        assertTrue(!source.contains("WireExtrasSerializer.deserialize(reader)"), source)
    }

    @Test
    fun generatesWrappedKeysCaptureAndMaterialize() {
        val (compilation, result) = compile(
            SourceFile.kotlin(
                "WrappedFixture.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostName
                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.annotations.GhostWrappedKeys

                @GhostSerialization
                data class Inner(
                    @GhostName("extra1") val extra1: String,
                )

                @GhostSerialization
                data class WrappedFixture(
                    val id: String,
                    @GhostWrappedKeys(keys = ["extra1"])
                    @GhostName("extras")
                    val extras: Inner,
                )
                """.trimIndent(),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        lastCompilation = compilation
        val source = compilationOutput("WrappedFixtureSerializer.kt")
        assertTrue("GhostWrappedKeysCapture" in source, source)
        assertTrue("materializeWrappedObject" in source, source)
        assertTrue("captureWrappedKey" in source, source)
    }

    private lateinit var lastCompilation: KotlinCompilation

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

    private fun compilationOutput(fileName: String): String {
        val file = lastCompilation.kspSourcesDir.walk()
            .first { it.name == fileName }
        return file.readText()
    }
}
