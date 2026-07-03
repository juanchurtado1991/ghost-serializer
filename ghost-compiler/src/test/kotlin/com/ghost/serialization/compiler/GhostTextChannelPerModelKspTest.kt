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

/**
 * Per-model `textChannel` on `@GhostSerialization` (default false) with transitive propagation.
 */
class GhostTextChannelPerModelKspTest {

    private fun readSerializer(serializerName: String): String {
        val (compilation, result) = compilePerModelFixture()
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        return compilation.kspSourcesDir.walk()
            .filter { it.name == "$serializerName.kt" }
            .map { it.readText() }
            .first()
    }

    @Test
    fun annotationEnablesStringChannelOnlyOnAnnotatedGraph() {
        val generatedRoot = readSerializer("MacroRootSerializer")
        val generatedLeaf = readSerializer("SmallDtoSerializer")
        val generatedNested = readSerializer("MacroNestedSerializer")

        assertTrue(
            "override fun deserialize(reader: GhostJsonStringReader)" in generatedRoot,
            "Root with textChannel=true must generate string deserialize",
        )
        assertTrue(
            "override fun deserialize(reader: GhostJsonStringReader)" in generatedNested,
            "Nested Ghost type must inherit string channel from root",
        )
        assertFalse(
            "override fun deserialize(reader: GhostJsonStringReader)" in generatedLeaf,
            "Unrelated model must not generate string deserialize",
        )
    }

    private fun compilePerModelFixture(): Pair<KotlinCompilation, JvmCompilationResult> {
        val compilation = KotlinCompilation().apply {
            sources = listOf(
                SourceFile.kotlin(
                    "fixtures.kt",
                    """
                    package fixtures

                    import com.ghost.serialization.annotations.GhostSerialization

                    @GhostSerialization(textChannel = true)
                    data class MacroRoot(val nested: MacroNested)

                    @GhostSerialization
                    data class MacroNested(val id: Int)

                    @GhostSerialization
                    data class SmallDto(val name: String)
                    """.trimIndent(),
                ),
            )
            symbolProcessorProviders = mutableListOf(GhostSerializationProvider())
            inheritClassPath = true
            kspWithCompilation = true
            languageVersion = "1.9"
            apiVersion = "1.9"
            jvmTarget = "17"
        }
        return compilation to compilation.compile()
    }
}
