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

/** KSP regression tests for [@GhostJsonEnvelope][com.ghost.serialization.annotations.GhostJsonEnvelope]. */
class GhostJsonEnvelopeKspTest {

    @Test
    fun fatEnvelopeGeneratesRouteAndParsePayload() {
        val generated = compileEnvelope(
            """
            package fixtures

            import com.ghost.serialization.annotations.GhostEnvelopePayload
            import com.ghost.serialization.annotations.GhostJsonEnvelope
            import com.ghost.serialization.annotations.GhostName
            import com.ghost.serialization.annotations.GhostSerialization
            import com.ghost.serialization.types.RawJson

            @GhostJsonEnvelope(discriminator = "eventType", timeField = "eventTime")
            @GhostSerialization
            data class SseEnvelope(
                @GhostName("eventType") val eventType: String = "",
                @GhostName("eventTime") val timeMillis: Long = 0L,
                @GhostEnvelopePayload("DEVICE_EVENT")
                @GhostName("deviceEvent") val deviceEvent: RawJson? = null,
                @GhostEnvelopePayload("MODE_EVENT")
                @GhostName("modeEvent") val modeEvent: RawJson? = null,
            )
            """.trimIndent()
        )

        assertTrue("fun routePayload(envelope: SseEnvelope)" in generated, generated)
        assertTrue("fun parsePayload(bytes: ByteArray)" in generated, generated)
        assertTrue("\"DEVICE_EVENT\" -> envelope.deviceEvent" in generated, generated)
        assertTrue("\"MODE_EVENT\" -> envelope.modeEvent" in generated, generated)
        assertTrue("else -> null" in generated, generated)
        assertTrue(
            "val envelope = deserialize(GhostJsonFlatReader(bytes))" in generated,
            generated
        )
    }

    @Test
    fun genericEnvelopeGeneratesSingleDataRoute() {
        val generated = compileEnvelope(
            """
            package fixtures

            import com.ghost.serialization.annotations.GhostJsonEnvelope
            import com.ghost.serialization.annotations.GhostSerialization
            import com.ghost.serialization.types.RawJson

            @GhostJsonEnvelope(discriminator = "type", dataField = "data")
            @GhostSerialization
            data class WebhookEnvelope(
                val type: String = "",
                val data: RawJson? = null,
            )
            """.trimIndent()
        )

        assertTrue("fun routePayload(envelope: WebhookEnvelope)" in generated, generated)
        assertTrue("else -> envelope.data" in generated, generated)
        assertFalse("fun routeTyped" in generated, "Generic envelope without targets must not emit routeTyped")
    }

    @Test
    fun typedPayloadGeneratesRouteTyped() {
        val generated = compileEnvelope(
            """
            package fixtures

            import com.ghost.serialization.annotations.GhostEnvelopePayload
            import com.ghost.serialization.annotations.GhostJsonEnvelope
            import com.ghost.serialization.annotations.GhostSerialization
            import com.ghost.serialization.types.RawJson

            @GhostSerialization
            data class InvoicePaid(val amount: Long)

            @GhostJsonEnvelope(discriminator = "type", dataField = "data")
            @GhostSerialization
            data class StripeEnvelope(
                val type: String = "",
                @GhostEnvelopePayload("invoice.paid", target = InvoicePaid::class)
                val data: RawJson? = null,
            )
            """.trimIndent()
        )

        assertTrue("fun routeTyped(envelope: StripeEnvelope)" in generated, generated)
        assertTrue("fun parseTyped(bytes: ByteArray)" in generated, generated)
        assertTrue(
            "\"invoice.paid\" -> envelope.data?.let { RawJsonDecode.decode(it, InvoicePaid::class) }" in generated,
            generated
        )
    }

    @Test
    fun fallbackPropertyRoutesUnknownDiscriminator() {
        val generated = compileEnvelope(
            """
            package fixtures

            import com.ghost.serialization.annotations.GhostEnvelopeFallback
            import com.ghost.serialization.annotations.GhostEnvelopePayload
            import com.ghost.serialization.annotations.GhostJsonEnvelope
            import com.ghost.serialization.annotations.GhostSerialization
            import com.ghost.serialization.types.RawJson

            @GhostJsonEnvelope(discriminator = "type")
            @GhostSerialization
            data class EventEnvelope(
                val type: String = "",
                @GhostEnvelopePayload("known.event")
                val known: RawJson? = null,
                @GhostEnvelopeFallback
                val unknown: RawJson? = null,
            )
            """.trimIndent()
        )

        assertTrue("else -> envelope.unknown" in generated, generated)
    }

    private fun compileEnvelope(source: String): String {
        val (compilation, result) = compile(SourceFile.kotlin("Envelope.kt", source))
        assertEquals(KotlinCompilation.ExitCode.OK, result.exitCode, result.messages)
        return compilation.kspSourcesDir.walk()
            .filter { it.name.endsWith("Serializer.kt") && "Envelope" in it.name }
            .joinToString("\n") { it.readText() }
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
