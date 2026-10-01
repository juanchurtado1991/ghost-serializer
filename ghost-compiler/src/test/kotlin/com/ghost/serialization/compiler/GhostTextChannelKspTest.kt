@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.ghost.serialization.compiler

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import com.tschuchort.compiletesting.useKsp2
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


/**
 * KSP and runtime regression tests for the native string channel (`ghost.textChannel`).
 */
class GhostTextChannelKspTest {

    @Test
    fun textChannelTrueGeneratesTwoDeserializeOverloads() {
        val generated = compileAndReadSerializer(textChannel = true)

        assertTrue(
            actual = "override fun deserialize(reader: GhostJsonReader)" in generated,
            message = "Expected the unified in-memory/streaming deserialize overload"
        )
        assertTrue(
            actual = "override fun deserialize(reader: GhostJsonStringReader)" in generated,
            message = "Expected native string deserialize overload when textChannel=true"
        )
    }

    @Test
    fun textChannelTrueGeneratesTwoSerializeOverloads() {
        val generated = compileAndReadSerializer(textChannel = true)

        assertTrue(
            actual = "override fun serialize(writer: GhostJsonWriter," in generated,
            message = "Expected the unified in-memory/streaming serialize overload"
        )
        assertTrue(
            actual = "override fun serialize(writer: GhostJsonStringWriter," in generated,
            message = "Expected string serialize overload"
        )
    }

    @Test
    fun textChannelFalseOmitsNativeStringSerializeOverload() {
        val generated = compileAndReadSerializer(textChannel = false)

        assertFalse(
            actual = "override fun serialize(writer: GhostJsonStringWriter," in generated,
            message = "String serialize must not be generated when textChannel=false:\n$generated"
        )
    }

    @Test
    fun textChannelFalseOmitsNativeStringDeserializeOverload() {
        val generated = compileAndReadSerializer(textChannel = false)

        assertTrue(
            actual = "override fun deserialize(reader: GhostJsonReader)" in generated,
            message = "Expected the unified in-memory/streaming deserialize overload"
        )
        assertFalse(
            actual = "override fun deserialize(reader: GhostJsonStringReader)" in generated,
            message = "String deserialize must not be generated when textChannel=false:\n$generated"
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

        val reader = GhostJsonStringReader(rawData = """{"id":42,"name":"bridge"}""")
        val decoded = serializer.deserialize(reader)

        val userClass = result.classLoader.loadClass("fixtures.ChannelUser")
        assertEquals(expected = 42, actual = userClass.getMethod("getId").invoke(decoded))
        assertEquals(expected = "bridge", actual = userClass.getMethod("getName").invoke(decoded))
    }

    @Test
    fun textChannelTrueGeneratesFeatureCodegenOnAllDeserializePaths() {
        val generated = compileFeatureModel(textChannel = true)

        assertEquals(
            expected = 1,
            actual = Regex("override fun deserialize\\(reader: GhostJsonReader\\)").findAll(generated)
                .count()
        )
        assertEquals(
            expected = 1,
            actual = Regex("override fun deserialize\\(reader: GhostJsonFlatReader\\)").findAll(generated)
                .count()
        )
        assertEquals(
            expected = 1,
            actual = Regex("override fun deserialize\\(reader: GhostJsonStringReader\\)").findAll(generated)
                .count()
        )
        assertEquals(expected = 3, actual = "reader.readSet".toRegex().findAll(generated).count())
        assertEquals(expected = 3, actual = "reader.captureRawJson()".toRegex().findAll(generated).count())
        assertEquals(expected = 3, actual = "reader.nextChar()".toRegex().findAll(generated).count())
        assertTrue(
            actual = "writer.rawValue(value.metadata.storage, value.metadata.storageOffset, value.metadata.storageLength)" in generated,
            message = "Expected slice rawValue for RawJson field:\n$generated"
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
            useKsp2()
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
