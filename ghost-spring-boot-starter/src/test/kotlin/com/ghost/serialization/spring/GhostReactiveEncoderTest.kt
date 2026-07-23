package com.ghost.serialization.spring

import com.ghost.serialization.spring.fixture.HelloMessage
import org.junit.jupiter.api.Test
import org.springframework.core.ResolvableType
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.MediaType
import org.springframework.util.MimeType
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

data class UnregisteredReactiveMessage(val value: Int)

/**
 * Direct unit tests for [GhostReactiveEncoder] — no Spring context, so these run fast and
 * target the specific canEncode/encode branches precisely. End-to-end wiring through a real
 * WebFlux app is covered separately by [GhostSpringWebFluxIntegrationTest].
 */
class GhostReactiveEncoderTest {

    private val encoder = GhostReactiveEncoder()
    private val bufferFactory = DefaultDataBufferFactory()

    private fun bufferText(buffer: org.springframework.core.io.buffer.DataBuffer): String {
        val bytes = ByteArray(buffer.readableByteCount())
        buffer.read(bytes)
        return bytes.decodeToString()
    }

    @Test
    fun canEncode_trueForAnnotatedTypeWithSupportedMimeType() {
        assertTrue(
            encoder.canEncode(ResolvableType.forClass(HelloMessage::class.java), MediaType.APPLICATION_JSON)
        )
    }

    @Test
    fun canEncode_falseForUnregisteredType() {
        assertFalse(
            encoder.canEncode(
                ResolvableType.forClass(UnregisteredReactiveMessage::class.java),
                MediaType.APPLICATION_JSON
            )
        )
    }

    @Test
    fun canEncode_falseForUnsupportedMimeType() {
        assertFalse(
            encoder.canEncode(ResolvableType.forClass(HelloMessage::class.java), MediaType.APPLICATION_XML)
        )
    }

    @Test
    fun encode_writesGhostJsonBytesForEachElement() {
        val flux = encoder.encode(
            Flux.just(HelloMessage(1, "ghost")),
            bufferFactory,
            ResolvableType.forClass(HelloMessage::class.java),
            MediaType.APPLICATION_JSON,
            null
        )

        StepVerifier.create(flux)
            .assertNext { buffer -> assertEquals("""{"id":1,"name":"ghost"}""", bufferText(buffer)) }
            .verifyComplete()
    }

    @Test
    fun encode_appendsNewlineFramingForNdjson() {
        val ndjson = MimeType("application", "x-ndjson")
        val flux = encoder.encode(
            Flux.just(HelloMessage(1, "a"), HelloMessage(2, "b")),
            bufferFactory,
            ResolvableType.forClass(HelloMessage::class.java),
            ndjson,
            null
        )

        StepVerifier.create(flux)
            .assertNext { buffer -> assertEquals("""{"id":1,"name":"a"}""" + "\n", bufferText(buffer)) }
            .assertNext { buffer -> assertEquals("""{"id":2,"name":"b"}""" + "\n", bufferText(buffer)) }
            .verifyComplete()
    }

    @Test
    fun encode_doesNotAppendNewlineForPlainJson() {
        val flux = encoder.encode(
            Flux.just(HelloMessage(1, "ghost")),
            bufferFactory,
            ResolvableType.forClass(HelloMessage::class.java),
            MediaType.APPLICATION_JSON,
            null
        )

        StepVerifier.create(flux)
            .assertNext { buffer -> assertFalse(bufferText(buffer).endsWith("\n")) }
            .verifyComplete()
    }

    @Test
    fun encode_errorsWithDescriptiveMessageForUnregisteredType() {
        val flux = encoder.encode(
            Flux.just(UnregisteredReactiveMessage(1)),
            bufferFactory,
            ResolvableType.forClass(UnregisteredReactiveMessage::class.java),
            MediaType.APPLICATION_JSON,
            null
        )

        StepVerifier.create(flux)
            .verifyErrorSatisfies { error ->
                assertTrue(error is IllegalArgumentException)
                assertTrue(error.message!!.contains("UnregisteredReactiveMessage"))
            }
    }
}
