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
 * Per-model `textChannel` on `@GhostSerialization` (defaults to enabled to avoid silent,
 * expensive string-channel fallback) with transitive propagation and explicit opt-out.
 */
class GhostTextChannelPerModelKspTest {

    private fun readSerializer(serializerName: String, fixture: String): String {
        val (compilation, result) = compileFixture(fixture)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        return compilation.kspSourcesDir.walk()
            .filter { it.name == "$serializerName.kt" }
            .map { it.readText() }
            .first()
    }

    @Test
    fun plainAnnotationDefaultsToStringChannelOnAndPropagatesToNestedTypes() {
        val generatedRoot = readSerializer("MacroRootSerializer", PER_MODEL_FIXTURE)
        val generatedNested = readSerializer("MacroNestedSerializer", PER_MODEL_FIXTURE)

        assertTrue(
            "override fun deserialize(reader: GhostJsonStringReader)" in generatedRoot,
            "Plain @GhostSerialization (no args) must default to textChannel=true",
        )
        assertTrue(
            "override fun deserialize(reader: GhostJsonStringReader)" in generatedNested,
            "Nested Ghost type must inherit the string channel from an enabled root",
        )
    }

    @Test
    fun explicitOptOutOnAnUnreferencedModelSuppressesStringChannel() {
        val generatedLeaf = readSerializer("SmallDtoSerializer", PER_MODEL_FIXTURE)

        assertFalse(
            "override fun deserialize(reader: GhostJsonStringReader)" in generatedLeaf,
            "textChannel=false on a model nothing else references must stay disabled",
        )
    }

    @Test
    fun explicitOptOutIsOverriddenWhenReferencedByAnEnabledParent() {
        // A referencing parent's generated deserialize(reader: GhostJsonStringReader) calls
        // straight into the nested type's own string-reader overload — that overload MUST
        // exist for the parent to compile, regardless of what the nested type itself requested.
        val generatedParent = readSerializer("ParentOfOptOutSerializer", FORCED_ENABLE_FIXTURE)
        val generatedOptedOut =
            readSerializer("OptedOutButReferencedSerializer", FORCED_ENABLE_FIXTURE)

        assertTrue(
            "override fun deserialize(reader: GhostJsonStringReader)" in generatedParent,
        )
        assertTrue(
            "override fun deserialize(reader: GhostJsonStringReader)" in generatedOptedOut,
            "A model's own textChannel=false must be overridden when an enabled parent " +
                    "depends on it, otherwise the parent's generated code wouldn't compile",
        )
    }

    private fun compileFixture(source: String): Pair<KotlinCompilation, JvmCompilationResult> {
        val compilation = KotlinCompilation().apply {
            sources = listOf(SourceFile.kotlin("fixtures.kt", source))
            symbolProcessorProviders = mutableListOf(GhostSerializationProvider())
            inheritClassPath = true
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

    private companion object {
        const val PER_MODEL_FIXTURE = """
            package fixtures

            import com.ghost.serialization.annotations.GhostSerialization

            @GhostSerialization
            data class MacroRoot(val nested: MacroNested)

            @GhostSerialization
            data class MacroNested(val id: Int)

            @GhostSerialization(textChannel = false)
            data class SmallDto(val name: String)
        """

        const val FORCED_ENABLE_FIXTURE = """
            package fixtures

            import com.ghost.serialization.annotations.GhostSerialization

            @GhostSerialization
            data class ParentOfOptOut(val child: OptedOutButReferenced)

            @GhostSerialization(textChannel = false)
            data class OptedOutButReferenced(val id: Int)
        """
    }
}
