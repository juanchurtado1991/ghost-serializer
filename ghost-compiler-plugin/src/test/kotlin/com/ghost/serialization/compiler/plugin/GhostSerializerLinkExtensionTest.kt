@file:OptIn(ExperimentalCompilerApi::class)

package com.ghost.serialization.compiler.plugin

import com.ghost.serialization.compiler.GhostSerializationProvider
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.PluginOption
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Runs the plugin on JVM compilations against a stand-in `GhostSerializerLink` with runtime
 * retention (the real one only exists on Kotlin/Native and Kotlin/Wasm), so the attached link
 * can be read back with reflection. Runtime resolution itself is covered by ghost-serialization's
 * `nonJvmTest` suite and the compat consumer.
 */
class GhostSerializerLinkExtensionTest {

    @Test
    fun annotatedClass_isLinkedToItsSerializer() {
        val result = compile(
            sources = listOf(
                LINK_STUB,
                SourceFile.kotlin(
                    name = "User.kt",
                    contents = """
                    package fixtures

                    import com.ghost.serialization.annotations.GhostSerialization

                    @GhostSerialization
                    data class User(val id: Int)

                    object UserSerializer
                    """.trimIndent()
                )
            )
        )

        assertCompiled(result = result)
        assertEquals(
            expected = "fixtures.UserSerializer",
            actual = linkedSerializerName(result = result, className = "fixtures.User")
        )
    }

    @Test
    fun disabledOption_leavesClassesUnlinked() {
        val result = compile(
            sources = listOf(
                LINK_STUB,
                SourceFile.kotlin(
                    name = "User.kt",
                    contents = """
                    package fixtures

                    import com.ghost.serialization.annotations.GhostSerialization

                    @GhostSerialization
                    data class User(val id: Int)

                    object UserSerializer
                    """.trimIndent()
                )
            ),
            isEnabled = false
        )

        assertCompiled(result = result)
        assertNull(actual = linkedSerializerName(result = result, className = "fixtures.User"))
    }

    @Test
    fun kspGeneratedSerializer_isFoundByNamingConvention() {
        val compilation = KotlinCompilation().apply {
            sources = listOf(
                LINK_STUB,
                SourceFile.kotlin(
                    name = "Outer.kt",
                    contents = """
                    package fixtures

                    import com.ghost.serialization.annotations.GhostSerialization

                    class Outer {
                        @GhostSerialization
                        data class Inner(val id: Int, val name: String)
                    }
                    """.trimIndent()
                )
            )
            inheritClassPath = true
            useKsp2()
            symbolProcessorProviders = mutableListOf(GhostSerializationProvider())
            kspWithCompilation = true
            compilerPluginRegistrars = listOf(GhostCompilerPluginRegistrar())
            commandLineProcessors = listOf(GhostCommandLineProcessor())
        }
        val result = compilation.compile()

        assertCompiled(result = result)
        assertEquals(
            expected = "fixtures.Outer_InnerSerializer",
            actual = linkedSerializerName(result = result, className = "fixtures.Outer\$Inner")
        )
    }

    @Test
    fun missingSerializer_warnsAndLeavesClassUnlinked() {
        val result = compile(
            sources = listOf(
                LINK_STUB,
                SourceFile.kotlin(
                    name = "Orphan.kt",
                    contents = """
                    package fixtures

                    import com.ghost.serialization.annotations.GhostSerialization

                    @GhostSerialization
                    data class Orphan(val id: Int)
                    """.trimIndent()
                )
            )
        )

        assertCompiled(result = result)
        assertNull(actual = linkedSerializerName(result = result, className = "fixtures.Orphan"))
        assertTrue(
            actual = result.messages.contains(other = "fixtures.OrphanSerializer"),
            message = result.messages
        )
    }

    @Test
    fun missingLinkAnnotation_compilesWithoutChanges() {
        val result = compile(
            sources = listOf(
                SourceFile.kotlin(
                    name = "User.kt",
                    contents = """
                    package fixtures

                    import com.ghost.serialization.annotations.GhostSerialization

                    @GhostSerialization
                    data class User(val id: Int)

                    object UserSerializer
                    """.trimIndent()
                )
            )
        )

        assertCompiled(result = result)
    }

    @Test
    fun sealedSubclasses_linkToOwnOrParentSerializer() {
        val result = compile(
            sources = listOf(
                LINK_STUB,
                SourceFile.kotlin(
                    name = "Shape.kt",
                    contents = """
                    package fixtures

                    import com.ghost.serialization.annotations.GhostSerialization

                    @GhostSerialization
                    sealed class Shape {
                        data class Circle(val radius: Int) : Shape()

                        @GhostSerialization
                        data class Square(val side: Int) : Shape()
                    }

                    object ShapeSerializer
                    object Shape_SquareSerializer
                    """.trimIndent()
                )
            )
        )

        assertCompiled(result = result)
        assertEquals(
            expected = "fixtures.ShapeSerializer",
            actual = linkedSerializerName(result = result, className = "fixtures.Shape\$Circle")
        )
        assertEquals(
            expected = "fixtures.Shape_SquareSerializer",
            actual = linkedSerializerName(result = result, className = "fixtures.Shape\$Square")
        )
    }

    @Test
    fun unannotatedClass_isNotLinked() {
        val result = compile(
            sources = listOf(
                LINK_STUB,
                SourceFile.kotlin(
                    name = "Plain.kt",
                    contents = """
                    package fixtures

                    data class Plain(val id: Int)

                    object PlainSerializer
                    """.trimIndent()
                )
            )
        )

        assertCompiled(result = result)
        assertNull(actual = linkedSerializerName(result = result, className = "fixtures.Plain"))
    }

    private fun assertCompiled(
        result: JvmCompilationResult
    ) {
        assertEquals(
            expected = KotlinCompilation.ExitCode.OK,
            actual = result.exitCode,
            message = result.messages
        )
    }

    private fun compile(
        sources: List<SourceFile>,
        isEnabled: Boolean = true
    ): JvmCompilationResult = KotlinCompilation().apply {
        this.sources = sources
        inheritClassPath = true
        compilerPluginRegistrars = listOf(GhostCompilerPluginRegistrar())
        commandLineProcessors = listOf(GhostCommandLineProcessor())
        pluginOptions = listOf(
            PluginOption(
                pluginId = GhostCompilerPluginConstants.PLUGIN_ID,
                optionName = GhostCompilerPluginConstants.OPTION_ENABLED,
                optionValue = isEnabled.toString()
            )
        )
    }.compile()

    private fun linkedSerializerName(
        result: JvmCompilationResult,
        className: String
    ): String? {
        val linkClass = result.classLoader.loadClass(LINK_CLASS_NAME)
        val annotation = result.classLoader.loadClass(className).annotations
            .firstOrNull { linkClass.isInstance(it) }
            ?: return null
        val serializer = linkClass.getMethod(SERIALIZER_ACCESSOR).invoke(annotation) as Class<*>
        return serializer.name
    }

    private companion object {
        const val LINK_CLASS_NAME = "com.ghost.serialization.contract.GhostSerializerLink"
        const val SERIALIZER_ACCESSOR = "serializer"

        val LINK_STUB = SourceFile.kotlin(
            name = "GhostSerializerLink.kt",
            contents = """
            package com.ghost.serialization.contract

            import kotlin.reflect.KClass

            @Retention(AnnotationRetention.RUNTIME)
            @Target(AnnotationTarget.CLASS)
            annotation class GhostSerializerLink(val serializer: KClass<*>)
            """.trimIndent()
        )
    }
}
