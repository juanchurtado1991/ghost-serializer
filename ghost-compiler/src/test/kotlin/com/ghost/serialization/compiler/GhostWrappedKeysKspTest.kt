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
        val source = compilationOutput(fileName = "WrappedKeysFixtureSerializer.kt")
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
        val source = compilationOutput(fileName = "WrappedFixtureSerializer.kt")
        assertTrue("GhostWrappedKeysCapture" in source, source)
        assertTrue("materializeWrappedObject" in source, source)
        assertTrue("captureWrappedKey" in source, source)
    }

    @Test
    fun generatesSealedSubclassSmartCastForOneofStyleWrappedKeys() {
        val (compilation, result) = compile(
            SourceFile.kotlin(
                "OneofFixture.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.annotations.GhostWrappedKeys

                @GhostSerialization(inferred = true)
                sealed class Payload {
                    @GhostSerialization
                    data class Text(val text: String) : Payload()
                    @GhostSerialization
                    data class Code(val code: Int) : Payload()
                }

                @GhostSerialization
                data class OneofFixture(
                    val id: String,
                    @GhostWrappedKeys(keys = ["text", "code"])
                    val payload: Payload,
                )
                """.trimIndent(),
            ),
        )

        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        lastCompilation = compilation
        val source = compilationOutput(fileName = "OneofFixtureSerializer.kt")
        // No fallback warning/silent drop: each wire key resolves to its owning sealed subclass.
        assertTrue("value.payload is Payload.Text" in source, source)
        assertTrue("value.payload is Payload.Code" in source, source)
        assertTrue("(value.payload as Payload.Text).text" in source, source)
        assertTrue("(value.payload as Payload.Code).code" in source, source)
    }

    private lateinit var lastCompilation: KotlinCompilation

    @Test
    fun wrappedKeysInLargeClassFailsWithClearMessage() {
        val (_, result) = compile(
            SourceFile.kotlin(
                "LargeWrapped.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostName
                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.annotations.GhostWrappedKeys

                @GhostSerialization
                data class LargeExtras(
                    @GhostName("extra1") val extra1: String?,
                    @GhostName("extra2") val extra2: String?,
                )

                @GhostSerialization
                data class LargeWrapped(
                    val f1: Int = 0,
                    val f2: Int = 0,
                    val f3: Int = 0,
                    val f4: Int = 0,
                    val f5: Int = 0,
                    val f6: Int = 0,
                    val f7: Int = 0,
                    val f8: Int = 0,
                    val f9: Int = 0,
                    val f10: Int = 0,
                    val f11: Int = 0,
                    val f12: Int = 0,
                    val f13: Int = 0,
                    val f14: Int = 0,
                    val f15: Int = 0,
                    val f16: Int = 0,
                    val f17: Int = 0,
                    val f18: Int = 0,
                    val f19: Int = 0,
                    val f20: Int = 0,
                    val f21: Int = 0,
                    val f22: Int = 0,
                    val f23: Int = 0,
                    val f24: Int = 0,
                    val f25: Int = 0,
                    val f26: Int = 0,
                    val f27: Int = 0,
                    val f28: Int = 0,
                    val f29: Int = 0,
                    val f30: Int = 0,
                    val f31: Int = 0,
                    val f32: Int = 0,
                    val f33: Int = 0,
                    val f34: Int = 0,
                    val f35: Int = 0,
                    val f36: Int = 0,
                    val f37: Int = 0,
                    val f38: Int = 0,
                    val f39: Int = 0,
                    val f40: Int = 0,
                    @GhostWrappedKeys(keys = ["extra1", "extra2"])
                    @GhostName("extras")
                    val extras: LargeExtras,
                )
                """.trimIndent(),
            ),
        )

        assertTrue(result.exitCode != KotlinCompilation.ExitCode.OK, result.messages)
        assertTrue(
            "@GhostWrappedKeys is not supported yet in classes with more than 40 properties" in result.messages,
            result.messages
        )
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
        }
        return compilation to compilation.compile()
    }

    private fun compilationOutput(fileName: String): String {
        val file = lastCompilation.kspSourcesDir.walk()
            .first { it.name == fileName }
        return file.readText()
    }
}
