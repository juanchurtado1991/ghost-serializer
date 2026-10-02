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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * KSP regression tests for `@GhostProtoSerialization` proto3 JSON mapping rules.
 */
class GhostProtoSerializationKspTest {

    @Test
    fun longFieldsAreQuotedOnSerializeAndCoercedOnDeserialize() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
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

        assertTrue(
            actual = "writer.value(value.request_id.toString())" in generated,
            message = "Expected quoted int64 write for request_id:\n$generated"
        )
        assertFalse(
            actual = "writer.writeField(H_REQUESTID, value.request_id)" in generated,
            message = "Long field must not use the unquoted fused writeField path under @GhostProtoSerialization:\n$generated"
        )

        assertTrue(
            actual = "reader.coerceStringsToNumbers" in generated,
            message = "Expected quoted-int64 coercion toggle for request_id:\n$generated"
        )

        // int32 stays a bare JSON number under proto3, unlike int64.
        assertTrue(
            actual = "writer.writeField(H_RETRIES, value.retries)" in generated,
            message = "Int32 field should still use the fast fused writeField path:\n$generated"
        )
    }

    @Test
    fun byteArrayFieldsAreBase64EncodedUnderProto() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
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
            actual = "writer.value(encodeBase64String(value.payload))" in generated,
            message = "Expected Base64-encoded write for a proto ByteArray field:\n$generated"
        )
        assertTrue(
            actual = "decodeBase64String(reader.nextString())" in generated,
            message = "Expected Base64-decoded read for a proto ByteArray field:\n$generated"
        )
        assertFalse(
            actual = "writer.rawValue(value.payload)" in generated,
            message = "Proto ByteArray fields must not use the raw-JSON-passthrough path:\n$generated"
        )
        assertFalse(
            actual = "captureRawJsonBytes" in generated,
            message = "Proto ByteArray fields must not use the raw-JSON-passthrough capture:\n$generated"
        )
    }

    @Test
    fun plainByteArrayFieldsStayAsRawJsonPassthrough() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
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
            actual = "writer.rawValue(value.payload)" in generated,
            message = "Non-proto ByteArray fields must keep the raw-JSON-passthrough path:\n$generated"
        )
        assertFalse("encodeBase64String" in generated, generated)
    }

    @Test
    fun zeroValueFieldsAreOmittedOnSerializeUnderProto() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
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

        assertTrue(
            actual = "if (value.retries != 0) {" in generated,
            message = "Expected int32 zero-value guard:\n$generated"
        )
        assertTrue(
            actual = "if (value.label.isNotEmpty()) {" in generated,
            message = "Expected empty-string guard:\n$generated"
        )
        assertTrue(actual = "if (value.active) {" in generated, message = "Expected boolean-false guard:\n$generated")
    }

    @Test
    fun zeroValueLongFieldCombinesOmissionAndQuoting() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "ProtoDeviceStatus.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostProtoSerialization

                @GhostProtoSerialization
                data class ProtoDeviceStatus(val device_id: Long, val retry_count: Int, val label: String)
                """.trimIndent()
            ),
            serializerFileName = "ProtoDeviceStatusSerializer.kt"
        )

        assertTrue(
            actual = "if (value.device_id != 0L) {" in generated,
            message = "Expected zero-value guard combined with quoting for a proto Long field:\n$generated"
        )
        assertTrue("writer.value(value.device_id.toString())" in generated, generated)
    }

    @Test
    fun listOfLongIsQuotedElementwiseUnderProto() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "ProtoIdList.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostProtoSerialization

                @GhostProtoSerialization
                data class ProtoIdList(val ids: List<Long>)
                """.trimIndent()
            ),
            serializerFileName = "ProtoIdListSerializer.kt"
        )

        assertTrue(
            actual = "writer.value(item0.toString())" in generated,
            message = "Expected List<Long> elements to be quoted under proto:\n$generated"
        )
        assertTrue("reader.coerceStringsToNumbers" in generated, generated)
    }

    @Test
    fun mapOfLongValuesIsQuotedUnderProto() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "ProtoCounters.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostProtoSerialization

                @GhostProtoSerialization
                data class ProtoCounters(val counts: Map<String, Long>)
                """.trimIndent()
            ),
            serializerFileName = "ProtoCountersSerializer.kt"
        )

        assertTrue(
            actual = "writer.value(mapVal0.toString())" in generated,
            message = "Expected Map<String, Long> values to be quoted under proto:\n$generated"
        )
    }

    @Test
    fun listOfByteArrayIsBase64EncodedUnderProto() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "ProtoChunks.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostProtoSerialization

                @GhostProtoSerialization
                data class ProtoChunks(val chunks: List<ByteArray>)
                """.trimIndent()
            ),
            serializerFileName = "ProtoChunksSerializer.kt"
        )

        assertTrue(
            actual = "writer.value(encodeBase64String(item0))" in generated,
            message = "Expected List<ByteArray> elements to be Base64-encoded under proto:\n$generated"
        )
        assertTrue("decodeBase64String(reader.nextString())" in generated, generated)
        assertFalse("captureRawJsonBytes" in generated, generated)
    }

    @Test
    fun valueClassWrappedLongIsQuotedUnderProto() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "ProtoAccount.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostProtoSerialization

                @JvmInline
                value class AccountId(val value: Long)

                @GhostProtoSerialization
                data class ProtoAccount(val account_id: AccountId)
                """.trimIndent()
            ),
            serializerFileName = "ProtoAccountSerializer.kt"
        )

        assertTrue(
            actual = "writer.value(value.account_id.`value`.toString())" in generated,
            message = "Expected value-class-wrapped Long to be quoted under proto:\n$generated"
        )
        assertTrue("reader.coerceStringsToNumbers" in generated, generated)
    }

    @Test
    fun plainGhostSerializationDoesNotOmitZeroValues() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
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

        assertFalse(
            actual = "if (value.retries != 0)" in generated,
            message = "Non-proto classes must not omit zero values:\n$generated"
        )
        assertTrue("writer.writeField(H_RETRIES, value.retries)" in generated, generated)
    }

    @Test
    fun protoClassOverridesIsProtoRuntimeFlag() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "ProtoFlagged.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostProtoSerialization

                @GhostProtoSerialization
                data class ProtoFlagged(val id: Int)
                """.trimIndent()
            ),
            serializerFileName = "ProtoFlaggedSerializer.kt"
        )

        assertTrue(
            actual = "override val isProto: Boolean = true" in generated,
            message = "Expected a runtime-checkable isProto flag on the generated serializer (the " +
                    "@GhostProtoSerialization annotation itself is BINARY-retained, not reflectively " +
                    "visible):\n$generated"
        )
    }

    @Test
    fun plainClassOverridesIsProtoFlagAsFalse() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "PlainFlagged.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class PlainFlagged(val id: Int)
                """.trimIndent()
            ),
            serializerFileName = "PlainFlaggedSerializer.kt"
        )

        assertTrue(
            actual = "override val isProto: Boolean = false" in generated,
            message = "Plain @GhostSerialization classes should still override isProto (as false) since " +
                "GhostSerializer no longer provides a default:\n$generated"
        )
    }

    @Test
    fun plainGhostSerializationLeavesLongUnquoted() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
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
            actual = "writer.writeField(H_REQUESTID, value.requestId)" in generated,
            message = "Non-proto Long fields must keep the fast unquoted fused path:\n$generated"
        )
        assertFalse(
            actual = ".toString())" in generated,
            message = "Non-proto Long fields must not be quoted:\n$generated"
        )
    }

    @Test
    fun listOfValueClassWrappedLongIsQuotedUnderProto() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "ProtoAccountIdList.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostProtoSerialization

                @JvmInline
                value class AccountId(val value: Long)

                @GhostProtoSerialization
                data class ProtoAccountIdList(val ids: List<AccountId>)
                """.trimIndent()
            ),
            serializerFileName = "ProtoAccountIdListSerializer.kt"
        )

        assertTrue(
            actual = "writer.value(item0.value.toString())" in generated,
            message = "Expected List<AccountId> value class elements to be unboxed and quoted under proto:\n$generated"
        )
        assertTrue(
            actual = "AccountId(run {" in generated && "reader.coerceStringsToNumbers = true" in generated,
            message = "Expected List<AccountId> elements to be deserialized by instantiating value class with coerced long:\n$generated"
        )
    }

    @Test
    fun mapOfValueClassWrappedLongIsQuotedUnderProto() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "ProtoAccountIdMap.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostProtoSerialization

                @JvmInline
                value class AccountId(val value: Long)

                @GhostProtoSerialization
                data class ProtoAccountIdMap(val accounts: Map<String, AccountId>)
                """.trimIndent()
            ),
            serializerFileName = "ProtoAccountIdMapSerializer.kt"
        )

        assertTrue(
            actual = "writer.value(mapVal0.value.toString())" in generated,
            message = "Expected Map values of AccountId to be unboxed and quoted under proto:\n$generated"
        )
        assertTrue(
            actual = "AccountId(run {" in generated && "reader.coerceStringsToNumbers = true" in generated,
            message = "Expected Map values of AccountId to be deserialized by instantiating value class with coerced long:\n$generated"
        )
    }

    @Test
    fun valueClassWrappingCollectionOmitsEmptyAndQuotesElements() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "ProtoAccountIdsWrap.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostProtoSerialization

                @JvmInline
                value class AccountIds(val value: List<Long>)

                @GhostProtoSerialization
                data class ProtoAccountIdsWrap(val ids: AccountIds)
                """.trimIndent()
            ),
            serializerFileName = "ProtoAccountIdsWrapSerializer.kt"
        )

        assertTrue(
            actual = "if (value.ids.value.isNotEmpty()) {" in generated,
            message = "Expected empty-list guard on value-class-wrapped collection:\n$generated"
        )
        assertTrue(
            actual = "writer.value(item0.toString())" in generated,
            message = "Expected quoted long elements inside wrapped list:\n$generated"
        )
    }

    @Test
    fun uLongFieldIsQuotedAndUsesProtoUInt64Reader() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "ProtoShard.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostProtoSerialization

                @GhostProtoSerialization
                data class ProtoShard(val shard_id: ULong)
                """.trimIndent()
            ),
            serializerFileName = "ProtoShardSerializer.kt"
        )

        assertTrue("writer.value(value.shard_id.toString())" in generated, generated)
        assertTrue("reader.nextProtoUInt64()" in generated, generated)
        assertTrue("if (value.shard_id != 0uL) {" in generated, generated)
    }

    @Test
    fun valueClassWrappingCollectionDeserializesWithCoercionBlock() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "ProtoAccountIdsWrapRead.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostProtoSerialization

                @JvmInline
                value class AccountIds(val value: List<Long>)

                @GhostProtoSerialization
                data class ProtoAccountIdsWrap(val ids: AccountIds)
                """.trimIndent()
            ),
            serializerFileName = "ProtoAccountIdsWrapSerializer.kt"
        )

        assertTrue(
            actual = "readList" in generated,
            message = "Expected list reader for value-class-wrapped collection:\n$generated"
        )
        assertTrue(
            actual = "reader.coerceStringsToNumbers = true" in generated,
            message = "Expected proto int64 coercion inside list elements:\n$generated"
        )
        assertTrue(
            actual = "AccountIds(" in generated,
            message = "Expected value class constructor wrapper:\n$generated"
        )
    }

    @Test
    fun uLongFieldDoesNotUseInternalDataPropertyOnSerialize() {
        val generated = compileAndReadSerializer(
            source = SourceFile.kotlin(
                "ProtoShardClean.kt",
                """
                package fixtures

                import com.ghost.serialization.annotations.GhostProtoSerialization

                @GhostProtoSerialization
                data class ProtoShard(val shard_id: ULong)
                """.trimIndent()
            ),
            serializerFileName = "ProtoShardSerializer.kt"
        )

        assertFalse(
            actual = ".data" in generated,
            message = "ULong must not codegen internal .data access:\n$generated"
        )
        assertTrue("value.shard_id.toString()" in generated, generated)
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
            useKsp2()
            symbolProcessorProviders = mutableListOf(GhostSerializationProvider())
            kspWithCompilation = true
            languageVersion = "1.9"
            apiVersion = "1.9"
            jvmTarget = "17"
        }
        return compilation to compilation.compile()
    }
}
