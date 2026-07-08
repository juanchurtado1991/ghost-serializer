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
 * KSP regression tests for `@GhostProtoSerialization` proto3 JSON mapping rules.
 */
class GhostProtoSerializationKspTest {

    @Test
    fun longFieldsAreQuotedOnSerializeAndCoercedOnDeserialize() {
        val generated = compileAndReadSerializer(
            SourceFile.kotlin(
                "ProtoCounter.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostProtoSerialization

                @GhostProtoSerialization
                data class ProtoCounter(val request_id: Long, val retries: Int)
                """.trimIndent()
            ),
            serializerFileName = "ProtoCounterSerializer.kt"
        )

        // Serialize: int64 field must be quoted (proto3 JSON), never the fused unquoted writeField.
        assertTrue(
            "writer.value(value.request_id.toString())" in generated,
            "Expected quoted int64 write for request_id:\n$generated"
        )
        assertFalse(
            "writer.writeField(H_REQUESTID, value.request_id)" in generated,
            "Long field must not use the unquoted fused writeField path under @GhostProtoSerialization:\n$generated"
        )

        // Deserialize: must accept both quoted and bare numeric int64 via coerceStringsToNumbers.
        assertTrue(
            "reader.coerceStringsToNumbers" in generated,
            "Expected quoted-int64 coercion toggle for request_id:\n$generated"
        )

        // int32 (Int) is unaffected — proto3 keeps int32 as a bare JSON number.
        assertTrue(
            "writer.writeField(H_RETRIES, value.retries)" in generated,
            "Int32 field should still use the fast fused writeField path:\n$generated"
        )
    }

    @Test
    fun byteArrayFieldsAreBase64EncodedUnderProto() {
        val generated = compileAndReadSerializer(
            SourceFile.kotlin(
                "ProtoBlob.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostProtoSerialization

                @GhostProtoSerialization
                data class ProtoBlob(val payload: ByteArray)
                """.trimIndent()
            ),
            serializerFileName = "ProtoBlobSerializer.kt"
        )

        assertTrue(
            "writer.value(encodeBase64String(value.payload))" in generated,
            "Expected Base64-encoded write for a proto ByteArray field:\n$generated"
        )
        assertTrue(
            "decodeBase64String(reader.nextString())" in generated,
            "Expected Base64-decoded read for a proto ByteArray field:\n$generated"
        )
        assertFalse(
            "writer.rawValue(value.payload)" in generated,
            "Proto ByteArray fields must not use the raw-JSON-passthrough path:\n$generated"
        )
        assertFalse(
            "captureRawJsonBytes" in generated,
            "Proto ByteArray fields must not use the raw-JSON-passthrough capture:\n$generated"
        )
    }

    @Test
    fun plainByteArrayFieldsStayAsRawJsonPassthrough() {
        val generated = compileAndReadSerializer(
            SourceFile.kotlin(
                "PlainBlob.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class PlainBlob(val payload: ByteArray)
                """.trimIndent()
            ),
            serializerFileName = "PlainBlobSerializer.kt"
        )

        assertTrue(
            "writer.rawValue(value.payload)" in generated,
            "Non-proto ByteArray fields must keep the raw-JSON-passthrough path:\n$generated"
        )
        assertFalse("encodeBase64String" in generated, generated)
    }

    @Test
    fun zeroValueFieldsAreOmittedOnSerializeUnderProto() {
        val generated = compileAndReadSerializer(
            SourceFile.kotlin(
                "ProtoSettings.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostProtoSerialization

                @GhostProtoSerialization
                data class ProtoSettings(val retries: Int, val label: String, val active: Boolean)
                """.trimIndent()
            ),
            serializerFileName = "ProtoSettingsSerializer.kt"
        )

        assertTrue("if (value.retries != 0) {" in generated, "Expected int32 zero-value guard:\n$generated")
        assertTrue(
            "if (value.label.isNotEmpty()) {" in generated,
            "Expected empty-string guard:\n$generated"
        )
        assertTrue("if (value.active) {" in generated, "Expected boolean-false guard:\n$generated")
    }

    @Test
    fun plainGhostSerializationDoesNotOmitZeroValues() {
        val generated = compileAndReadSerializer(
            SourceFile.kotlin(
                "PlainSettings.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class PlainSettings(val retries: Int)
                """.trimIndent()
            ),
            serializerFileName = "PlainSettingsSerializer.kt"
        )

        assertFalse("if (value.retries != 0)" in generated, "Non-proto classes must not omit zero values:\n$generated")
        assertTrue("writer.writeField(H_RETRIES, value.retries)" in generated, generated)
    }

    @Test
    fun plainGhostSerializationLeavesLongUnquoted() {
        val generated = compileAndReadSerializer(
            SourceFile.kotlin(
                "PlainCounter.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class PlainCounter(val requestId: Long)
                """.trimIndent()
            ),
            serializerFileName = "PlainCounterSerializer.kt"
        )

        assertTrue(
            "writer.writeField(H_REQUESTID, value.requestId)" in generated,
            "Non-proto Long fields must keep the fast unquoted fused path:\n$generated"
        )
        assertFalse(".toString())" in generated, "Non-proto Long fields must not be quoted:\n$generated")
    }

    private fun compileAndReadSerializer(source: SourceFile, serializerFileName: String): String {
        val (compilation, result) = compile(source)
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        return compilation.kspSourcesDir.walk()
            .filter { it.name == serializerFileName }
            .map { it.readText() }
            .first()
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
