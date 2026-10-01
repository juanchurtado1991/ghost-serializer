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

/**
 * Unit tests for [GhostReactiveEncoder] without a Spring context; see
 * [GhostSpringWebFluxIntegrationTest] for end-to-end WebFlux coverage.
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
            actual = encoder.canEncode(
                elementType = ResolvableType.forClass(HelloMessage::class.java),
                mimeType = MediaType.APPLICATION_JSON
            )
        )
    }

    @Test
    fun canEncode_falseForUnregisteredType() {
        assertFalse(
            actual = encoder.canEncode(
                elementType = ResolvableType.forClass(UnregisteredReactiveMessage::class.java),
                mimeType = MediaType.APPLICATION_JSON
            )
        )
    }

    @Test
    fun canEncode_falseForUnsupportedMimeType() {
        assertFalse(
            actual = encoder.canEncode(
                elementType = ResolvableType.forClass(HelloMessage::class.java),
                mimeType = MediaType.APPLICATION_XML
            )
        )
    }

    @Test
    fun encode_writesGhostJsonBytesForEachElement() {
        val flux = encoder.encode(
            inputStream = Flux.just(HelloMessage(id = 1, name = "ghost")),
            bufferFactory = bufferFactory,
            elementType = ResolvableType.forClass(HelloMessage::class.java),
            mimeType = MediaType.APPLICATION_JSON,
            hints = null
        )

        StepVerifier.create(flux)
            .assertNext { buffer ->
                assertEquals(
                    expected = """{"id":1,"name":"ghost"}""",
                    actual = bufferText(buffer = buffer)
                )
            }
            .verifyComplete()
    }

    @Test
    fun encode_appendsNewlineFramingForNdjson() {
        val ndjson = MimeType("application", "x-ndjson")
        val flux = encoder.encode(
            inputStream = Flux.just(HelloMessage(id = 1, name = "a"), HelloMessage(id = 2, name = "b")),
            bufferFactory = bufferFactory,
            elementType = ResolvableType.forClass(HelloMessage::class.java),
            mimeType = ndjson,
            hints = null
        )

        StepVerifier.create(flux)
            .assertNext { buffer ->
                assertEquals(
                    expected = """{"id":1,"name":"a"}""" + "\n",
                    actual = bufferText(buffer = buffer)
                )
            }
            .assertNext { buffer ->
                assertEquals(
                    expected = """{"id":2,"name":"b"}""" + "\n",
                    actual = bufferText(buffer = buffer)
                )
            }
            .verifyComplete()
    }

    @Test
    fun encode_doesNotAppendNewlineForPlainJson() {
        val flux = encoder.encode(
            inputStream = Flux.just(HelloMessage(id = 1, name = "ghost")),
            bufferFactory = bufferFactory,
            elementType = ResolvableType.forClass(HelloMessage::class.java),
            mimeType = MediaType.APPLICATION_JSON,
            hints = null
        )

        StepVerifier.create(flux)
            .assertNext { buffer -> assertFalse(actual = bufferText(buffer = buffer).endsWith("\n")) }
            .verifyComplete()
    }

    @Test
    fun encode_errorsWithDescriptiveMessageForUnregisteredType() {
        val flux = encoder.encode(
            inputStream = Flux.just(UnregisteredReactiveMessage(value = 1)),
            bufferFactory = bufferFactory,
            elementType = ResolvableType.forClass(UnregisteredReactiveMessage::class.java),
            mimeType = MediaType.APPLICATION_JSON,
            hints = null
        )

        StepVerifier.create(flux)
            .verifyErrorSatisfies { error ->
                assertTrue(actual = error is IllegalArgumentException)
                assertTrue(actual = error.message!!.contains("UnregisteredReactiveMessage"))
            }
    }
}
