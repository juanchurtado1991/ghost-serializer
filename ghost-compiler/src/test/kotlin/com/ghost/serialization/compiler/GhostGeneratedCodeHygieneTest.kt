@file:OptIn(org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi::class)

package com.ghost.serialization.compiler

import com.ghost.serialization.compiler.GhostEmitterConstants as C
import com.ghost.serialization.compiler.hygiene.GeneratedCodeHygiene
import com.tschuchort.compiletesting.JvmCompilationResult
import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.kspProcessorOptions
import com.tschuchort.compiletesting.kspSourcesDir
import com.tschuchort.compiletesting.kspWithCompilation
import com.tschuchort.compiletesting.symbolProcessorProviders
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression tests ensuring KSP-generated serializers stay lean: no dead imports,
 * no feature-gated parser helpers when the model does not need them.
 */
class GhostGeneratedCodeHygieneTest {

    @Test
    fun diverseBatchProducesHygieneCleanSerializers() {
        val compilation = compileBatch(HYGIENE_BATCH_SOURCE, textChannel = true)
        assertEquals(KotlinCompilation.ExitCode.OK, compilation.second.exitCode, compilation.second.messages)

        val serializers = compilation.first.kspSourcesDir.walk()
            .filter { it.isFile && it.name.endsWith("Serializer.kt") }
            .toList()

        assertTrue(serializers.isNotEmpty(), "Expected generated serializers")

        val violations = serializers.flatMap { file ->
            val source = file.readText()
            val label = file.name
            GeneratedCodeHygiene.analyze(source, label) +
                GeneratedCodeHygiene.analyzeConditionalRules(source, label, textChannel = true) +
                GeneratedCodeHygiene.analyzeSourceQuality(source, label)
        }

        assertTrue(
            violations.isEmpty(),
            violations.joinToString("\n") { "[${it.kind}] ${it.message}" },
        )
    }

    @Test
    fun textChannelFalseOmitsStringChannelSurface() {
        val compilation = compileBatch(MINIMAL_SOURCE, textChannel = false)
        assertEquals(KotlinCompilation.ExitCode.OK, compilation.second.exitCode, compilation.second.messages)

        val generated = readSerializer(compilation.first, "MinimalUser")
        assertTrue("GhostJsonStringReader" !in generated, generated)
        assertTrue(
            C.STR_OVERRIDE_DESERIALIZE_STRING_READER !in generated,
            generated,
        )
        assertTrue(
            "override fun serialize(writer: GhostJsonStringWriter," !in generated,
            generated,
        )

        val violations = GeneratedCodeHygiene.analyzeConditionalRules(
            generated,
            "MinimalUserSerializer.kt",
            textChannel = false,
        )
        assertTrue(violations.isEmpty(), violations.joinToString("\n") { it.message })
    }

    @Test
    fun rawJsonOnlyModelDoesNotImportCaptureRawJsonBytes() {
        assertRawCaptureHygiene("RawJsonOnly")
    }

    @Test
    fun byteArrayOnlyModelImportsCaptureRawJsonBytes() {
        assertRawCaptureHygiene("ByteArrayOnly")
    }

    private fun assertRawCaptureHygiene(modelName: String) {
        val compilation = compileBatch(RAW_CAPTURE_SOURCE, textChannel = true)
        assertEquals(KotlinCompilation.ExitCode.OK, compilation.second.exitCode, compilation.second.messages)

        val generated = readSerializer(compilation.first, modelName)
        val violations = GeneratedCodeHygiene.analyze(generated, "${modelName}Serializer.kt") +
            GeneratedCodeHygiene.analyzeConditionalRules(
                generated,
                "${modelName}Serializer.kt",
                textChannel = true,
            )

        assertTrue(violations.isEmpty(), violations.joinToString("\n") { it.message })
    }

    @Test
    fun nullableOnlyScalarsImportOrNullNotPlainNextX() {
        val compilation = compileBatch(NULLABLE_SCALARS_SOURCE, textChannel = true)
        assertEquals(KotlinCompilation.ExitCode.OK, compilation.second.exitCode, compilation.second.messages)

        val generated = readSerializer(compilation.first, "OnlyNullableScalars")
        assertTrue("nextIntOrNull" in generated, generated)
        assertTrue("nextLongOrNull" in generated, generated)
        assertTrue("nextBooleanOrNull" in generated, generated)
        assertTrue("nextStringOrNull" in generated, generated)
        assertTrue("import com.ghost.serialization.parser.nextInt\n" !in generated, generated)
        assertTrue("import com.ghost.serialization.parser.nextLong\n" !in generated, generated)
        assertTrue("import com.ghost.serialization.parser.nextBoolean\n" !in generated, generated)
        assertTrue("import com.ghost.serialization.parser.nextString\n" !in generated, generated)
        assertTrue("import com.ghost.serialization.parser.consumeNull\n" !in generated, generated)
        assertTrue("import com.ghost.serialization.parser.isNextNullValue\n" !in generated, generated)

        val violations = GeneratedCodeHygiene.analyze(generated, "OnlyNullableScalarsSerializer.kt")
        assertTrue(violations.isEmpty(), violations.joinToString("\n") { it.message })
    }

    @Test
    fun listOnlyModelDoesNotImportReadSet() {
        val compilation = compileBatch(LIST_ONLY_SOURCE, textChannel = true)
        assertEquals(KotlinCompilation.ExitCode.OK, compilation.second.exitCode, compilation.second.messages)

        val generated = readSerializer(compilation.first, "TagsHolder")
        assertTrue("readList" in generated, generated)
        assertTrue("readSet" !in generated, generated)
    }

    @Test
    fun setOnlyModelImportsReadSetNotReadList() {
        val compilation = compileBatch(SET_ONLY_SOURCE, textChannel = true)
        assertEquals(KotlinCompilation.ExitCode.OK, compilation.second.exitCode, compilation.second.messages)

        val generated = readSerializer(compilation.first, "LabelsHolder")
        assertTrue("readSet" in generated, generated)
        assertTrue("readList" !in generated, generated)
    }

    @Test
    fun singleRequiredFieldOmitsMaskRequiredConstant() {
        val compilation = compileBatch(SINGLE_REQUIRED_SOURCE, textChannel = true)
        assertEquals(KotlinCompilation.ExitCode.OK, compilation.second.exitCode, compilation.second.messages)

        val generated = readSerializer(compilation.first, "SingleRequired")
        assertTrue("MASK_ID" in generated, generated)
        assertTrue("MASK_REQUIRED" !in generated, generated)

        val violations = GeneratedCodeHygiene.analyze(generated, "SingleRequiredSerializer.kt") +
            GeneratedCodeHygiene.analyzeSourceQuality(generated, "SingleRequiredSerializer.kt")
        assertTrue(violations.isEmpty(), violations.joinToString("\n") { it.message })
    }

    @Test
    fun multipleRequiredFieldsEmitsAndUsesMaskRequiredConstant() {
        val compilation = compileBatch(MINIMAL_SOURCE, textChannel = true)
        assertEquals(KotlinCompilation.ExitCode.OK, compilation.second.exitCode, compilation.second.messages)

        val generated = readSerializer(compilation.first, "MinimalUser")
        assertTrue("MASK_REQUIRED_0" in generated, generated)
        assertTrue("(mask0 and MASK_REQUIRED_0) != MASK_REQUIRED_0" in generated, generated)

        val violations = GeneratedCodeHygiene.analyzeUnusedMaskConstants(
            generated,
            "MinimalUserSerializer.kt",
        )
        assertTrue(violations.isEmpty(), violations.joinToString("\n") { it.message })
    }

    @Test
    fun nullableOnlyModelOmitsMaskRequiredConstant() {
        val compilation = compileBatch(NULLABLE_SCALARS_SOURCE, textChannel = true)
        assertEquals(KotlinCompilation.ExitCode.OK, compilation.second.exitCode, compilation.second.messages)

        val generated = readSerializer(compilation.first, "OnlyNullableScalars")
        assertTrue("MASK_REQUIRED" !in generated, generated)

        val violations = GeneratedCodeHygiene.analyze(generated, "OnlyNullableScalarsSerializer.kt") +
            GeneratedCodeHygiene.analyzeSourceQuality(generated, "OnlyNullableScalarsSerializer.kt")
        assertTrue(violations.isEmpty(), violations.joinToString("\n") { it.message })
    }

    @Test
    fun underscoredPropertyNamesUseCamelCaseLocals() {
        val compilation = compileBatch(UNDERSCORED_PROP_SOURCE, textChannel = true)
        assertEquals(KotlinCompilation.ExitCode.OK, compilation.second.exitCode, compilation.second.messages)

        val generated = readSerializer(compilation.first, "SnakeCaseModel")
        assertTrue("var idInternalValue:" in generated, generated)
        assertTrue("id_internal = idInternalValue" in generated, generated)
        assertTrue("id_internalValue" !in generated, generated)

        val violations = GeneratedCodeHygiene.analyzeSourceQuality(generated, "SnakeCaseModelSerializer.kt")
        assertTrue(violations.isEmpty(), violations.joinToString("\n") { it.message })
    }

    @Test
    fun wrappedKeysLocalsAreCamelCase() {
        val compilation = compileBatch(WRAPPED_KEYS_SOURCE, textChannel = true)
        assertEquals(KotlinCompilation.ExitCode.OK, compilation.second.exitCode, compilation.second.messages)

        val generated = readSerializer(compilation.first, "WrappedExtras")
        assertTrue("wrappedCaptureExtras" in generated, generated)
        assertTrue("wrappedJsonExtras" in generated, generated)
        assertTrue("wrappedCapture_" !in generated, generated)
        assertTrue("wrappedJson_" !in generated, generated)

        val violations = GeneratedCodeHygiene.analyzeSourceQuality(generated, "WrappedExtrasSerializer.kt")
        assertTrue(violations.isEmpty(), violations.joinToString("\n") { it.message })
    }

    @Test
    fun generatedCallsAreMultilineFormatted() {
        val compilation = compileBatch(WIDE_DEFAULTS_SOURCE, textChannel = true)
        assertEquals(KotlinCompilation.ExitCode.OK, compilation.second.exitCode, compilation.second.messages)

        val generated = readSerializer(compilation.first, "WideDefaults")
        assertTrue("JsonReaderOptions.of(" in generated, generated)
        assertTrue("return createInstance(" in generated, generated)
        // Arguments appear on following lines, not crammed into one mega-call.
        assertTrue(!Regex("""return createInstance\([^)\n]{120,}""").containsMatchIn(generated), generated)
        assertTrue(!Regex("""JsonReaderOptions\.of\([^)\n]{120,}""").containsMatchIn(generated), generated)

        val violations = GeneratedCodeHygiene.analyzeLineLength(generated, "WideDefaultsSerializer.kt")
        assertTrue(violations.isEmpty(), violations.joinToString("\n") { it.message })
    }

    private fun readSerializer(compilation: KotlinCompilation, modelBaseName: String): String {
        return compilation.kspSourcesDir.walk()
            .filter { it.isFile && it.name == "${modelBaseName}Serializer.kt" }
            .single()
            .readText()
    }

    private fun compileBatch(source: String, textChannel: Boolean): Pair<KotlinCompilation, JvmCompilationResult> {
        return compile(textChannel, SourceFile.kotlin("HygieneFixtures.kt", source))
    }

    private fun compile(
        textChannel: Boolean,
        vararg sources: SourceFile,
    ): Pair<KotlinCompilation, JvmCompilationResult> {
        val compilation = KotlinCompilation().apply {
            this.sources = sources.toList()
            inheritClassPath = true
            symbolProcessorProviders = mutableListOf(GhostSerializationProvider())
            kspWithCompilation = true
            kspProcessorOptions = mutableMapOf(
                "ghost.textChannel" to textChannel.toString(),
            )
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
        const val MINIMAL_SOURCE = """
            package fixtures

            import com.ghost.serialization.annotations.GhostSerialization

            @GhostSerialization
            data class MinimalUser(val id: String, val age: Int)
        """

        const val SINGLE_REQUIRED_SOURCE = """
            package fixtures

            import com.ghost.serialization.annotations.GhostSerialization

            @GhostSerialization
            data class SingleRequired(val id: String, val note: String? = null)
        """

        const val UNDERSCORED_PROP_SOURCE = """
            package fixtures

            import com.ghost.serialization.annotations.GhostSerialization

            @GhostSerialization
            data class SnakeCaseModel(val id_internal: Int)
        """

        const val WRAPPED_KEYS_SOURCE = """
            package fixtures

            import com.ghost.serialization.annotations.GhostSerialization
            import com.ghost.serialization.annotations.GhostWrappedKeys

            @GhostSerialization
            data class NestedExtras(val a: Int = 0, val b: Int = 0)

            @GhostSerialization
            data class WrappedExtras(
                @GhostWrappedKeys(keys = ["a", "b"])
                val extras: NestedExtras,
            )
        """

        const val WIDE_DEFAULTS_SOURCE = """
            package fixtures

            import com.ghost.serialization.annotations.GhostSerialization

            @GhostSerialization
            data class WideDefaults(
                val id: String,
                val a: String = "",
                val b: String = "",
                val c: String = "",
                val d: String = "",
                val e: String = "",
            )
        """

        const val NULLABLE_SCALARS_SOURCE = """
            package fixtures

            import com.ghost.serialization.annotations.GhostSerialization

            @GhostSerialization
            data class OnlyNullableScalars(
                val i: Int?,
                val l: Long?,
                val b: Boolean?,
                val s: String?,
            )
        """

        const val LIST_ONLY_SOURCE = """
            package fixtures

            import com.ghost.serialization.annotations.GhostSerialization

            @GhostSerialization
            data class TagsHolder(val tags: List<String>)
        """

        const val SET_ONLY_SOURCE = """
            package fixtures

            import com.ghost.serialization.annotations.GhostSerialization

            @GhostSerialization
            data class LabelsHolder(val labels: Set<String>)
        """

        const val RAW_CAPTURE_SOURCE = """
            package fixtures

            import com.ghost.serialization.annotations.GhostSerialization
            import com.ghost.serialization.types.RawJson

            @GhostSerialization
            data class RawJsonOnly(val payload: RawJson)

            @GhostSerialization
            data class ByteArrayOnly(val payload: ByteArray)
        """

        const val HYGIENE_BATCH_SOURCE = """
            package fixtures

            import com.ghost.serialization.annotations.GhostEnvelopePayload
            import com.ghost.serialization.annotations.GhostFlatten
            import com.ghost.serialization.annotations.GhostJsonEnvelope
            import com.ghost.serialization.annotations.GhostName
            import com.ghost.serialization.annotations.GhostResilient
            import com.ghost.serialization.annotations.GhostSerialization
            import com.ghost.serialization.types.RawJson

            @GhostSerialization
            data class SimpleUser(val id: String, val score: Int)

            @GhostSerialization
            enum class Role { ADMIN, GUEST }

            @GhostSerialization
            @JvmInline
            value class UserId(val value: String)

            @GhostSerialization
            sealed class Event {
                @GhostSerialization
                data class Click(@GhostName("x") val x: Int) : Event()
            }

            @GhostSerialization
            data class NullableBox(val label: String?)

            @GhostSerialization
            data class NullableScalars(
                val i: Int?,
                val l: Long?,
                val b: Boolean?,
                val s: String?,
            )

            @GhostSerialization
            data class MapHolder(val attrs: Map<String, Int>)

            @GhostSerialization
            data class ListHolder(val items: List<String>)

            @GhostSerialization
            data class SetHolder(val ids: Set<Long>)

            @GhostSerialization
            data class RawHolder(val body: RawJson)

            @GhostSerialization
            data class BytesHolder(val body: ByteArray)

            @GhostResilient
            @GhostSerialization
            data class ResilientUser(val id: String, val email: String)

            @GhostSerialization
            data class Flattened(
                @GhostFlatten("profile.name")
                val displayName: String,
            )

            @GhostJsonEnvelope(discriminator = "type", dataField = "data")
            @GhostSerialization
            data class ApiEnvelope(
                val type: String = "",
                val data: RawJson? = null,
            )

            @GhostJsonEnvelope(discriminator = "type")
            @GhostSerialization
            data class TypedEnvelope(
                val type: String = "",
                @GhostEnvelopePayload("ping")
                val ping: RawJson? = null,
            )
        """
    }
}
