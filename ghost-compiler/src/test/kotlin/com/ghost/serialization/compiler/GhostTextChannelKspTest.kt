@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.ghost.serialization.compiler

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonStringReader
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * KSP and runtime regression tests for the native string channel (`ghost.textChannel`).
 */
class GhostTextChannelKspTest {

    @Test
    fun textChannelTrueGeneratesThreeDeserializeOverloads() {
        val generated = compileAndReadSerializer(textChannel = true)

        assertTrue(
            "override fun deserialize(reader: GhostJsonReader)" in generated,
            "Expected streaming deserialize overload"
        )
        assertTrue(
            "override fun deserialize(reader: GhostJsonFlatReader)" in generated,
            "Expected flat deserialize overload"
        )
        assertTrue(
            "override fun deserialize(reader: GhostJsonStringReader)" in generated,
            "Expected native string deserialize overload when textChannel=true"
        )
    }

    @Test
    fun textChannelTrueGeneratesThreeSerializeOverloads() {
        val generated = compileAndReadSerializer(textChannel = true)

        assertTrue(
            "override fun serialize(writer: GhostJsonWriter," in generated,
            "Expected streaming serialize overload"
        )
        assertTrue(
            "override fun serialize(writer: GhostJsonFlatWriter," in generated,
            "Expected flat serialize overload"
        )
        assertTrue(
            "override fun serialize(writer: GhostJsonStringWriter," in generated,
            "Expected string serialize overload"
        )
    }

    @Test
    fun textChannelFalseOmitsNativeStringDeserializeOverload() {
        val generated = compileAndReadSerializer(textChannel = false)

        assertTrue(
            "override fun deserialize(reader: GhostJsonReader)" in generated,
            "Expected streaming deserialize overload"
        )
        assertTrue(
            "override fun deserialize(reader: GhostJsonFlatReader)" in generated,
            "Expected flat deserialize overload"
        )
        assertFalse(
            "override fun deserialize(reader: GhostJsonStringReader)" in generated,
            "String deserialize must not be generated when textChannel=false:\n$generated"
        )
    }

    @Test
    fun textChannelFalseDeserializesFromStringViaSerializerBridge() {
        val (_, result) = compile(textChannel = false)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)

        val serializerClass = result.classLoader.loadClass("fixtures.ChannelUserSerializer")
        val instanceField = serializerClass.getDeclaredField("INSTANCE")
        instanceField.trySetAccessible()
        @Suppress("UNCHECKED_CAST")
        val serializer = instanceField.get(null) as GhostSerializer<Any>

        val reader = GhostJsonStringReader("""{"id":42,"name":"bridge"}""")
        val decoded = serializer.deserialize(reader)

        val userClass = result.classLoader.loadClass("fixtures.ChannelUser")
        assertEquals(42, userClass.getMethod("getId").invoke(decoded))
        assertEquals("bridge", userClass.getMethod("getName").invoke(decoded))
    }

    @Test
    fun textChannelTrueGeneratesFeatureCodegenOnAllDeserializePaths() {
        val generated = compileFeatureModel(textChannel = true)

        assertEquals(
            1,
            Regex("override fun deserialize\\(reader: GhostJsonReader\\)").findAll(generated).count()
        )
        assertEquals(
            1,
            Regex("override fun deserialize\\(reader: GhostJsonFlatReader\\)").findAll(generated).count()
        )
        assertEquals(
            1,
            Regex("override fun deserialize\\(reader: GhostJsonStringReader\\)").findAll(generated).count()
        )
        assertEquals(3, "reader.readSet".toRegex().findAll(generated).count())
        assertEquals(3, "reader.captureRawJson()".toRegex().findAll(generated).count())
        assertEquals(3, "reader.nextChar()".toRegex().findAll(generated).count())
        assertTrue(
            "writer.rawValue(value.metadata.storage, value.metadata.storageOffset, value.metadata.storageLength)" in generated,
            "Expected slice rawValue for RawJson field:\n$generated"
        )
    }

    private fun compileFeatureModel(textChannel: Boolean): String {
        val (compilation, result) = compile(
            textChannel = textChannel,
            source = SourceFile.kotlin(
                "FeatureChannelModel.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.types.RawJson

                @GhostSerialization
                data class FeatureChannelModel(
                    val tags: Set<String>,
                    val metadata: RawJson,
                    val letter: Char,
                )
                """.trimIndent()
            )
        )
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        return compilation.kspSourcesDir.walk()
            .filter { it.name == "FeatureChannelModelSerializer.kt" }
            .map { it.readText() }
            .first()
    }

    private fun compileAndReadSerializer(textChannel: Boolean): String {
        val (compilation, result) = compile(textChannel = textChannel)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        return compilation.kspSourcesDir.walk()
            .filter { it.name == "ChannelUserSerializer.kt" }
            .map { it.readText() }
            .first()
    }

    private fun compile(
        textChannel: Boolean,
        source: SourceFile = SourceFile.kotlin(
            "ChannelUser.kt",
            """
            package fixtures

            import com.ghost.serialization.annotations.GhostSerialization

            @GhostSerialization
            data class ChannelUser(val id: Int, val name: String)
            """.trimIndent()
        )
    ): Pair<KotlinCompilation, JvmCompilationResult> {
        val compilation = KotlinCompilation().apply {
            sources = listOf(source)
            inheritClassPath = true
            symbolProcessorProviders = mutableListOf(GhostSerializationProvider())
            kspProcessorOptions = mutableMapOf(
                "ghost.textChannel" to if (textChannel) "true" else "false"
            )
            kspWithCompilation = true
            languageVersion = "1.9"
            apiVersion = "1.9"
            jvmTarget = "17"
        }
        return compilation to compilation.compile()
    }
}
