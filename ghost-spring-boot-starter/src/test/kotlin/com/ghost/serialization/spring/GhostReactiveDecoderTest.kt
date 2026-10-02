package com.ghost.serialization.spring

import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.spring.fixture.HelloMessage
import org.junit.jupiter.api.Test
import org.springframework.core.ResolvableType
import org.springframework.core.io.buffer.DataBuffer
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.MediaType
import org.springframework.util.MimeType
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Unit tests for [GhostReactiveDecoder] without a Spring context; see
 * [GhostSpringWebFluxIntegrationTest] for end-to-end WebFlux coverage.
 */
class GhostReactiveDecoderTest {

    private val decoder = GhostReactiveDecoder()
    private val bufferFactory = DefaultDataBufferFactory()

    private fun buffer(json: String): DataBuffer = bufferFactory.wrap(json.encodeToByteArray())

    @Test
    fun canDecode_trueForAnnotatedTypeWithSupportedMimeType() {
        assertTrue(
            actual = decoder.canDecode(
                elementType = ResolvableType.forClass(HelloMessage::class.java),
                mimeType = MediaType.APPLICATION_JSON
            )
        )
    }

    @Test
    fun canDecode_falseForUnregisteredType() {
        assertFalse(
            actual = decoder.canDecode(
                elementType = ResolvableType.forClass(UnregisteredReactiveMessage::class.java),
                mimeType = MediaType.APPLICATION_JSON
            )
        )
    }

    @Test
    fun canDecode_falseForUnsupportedMimeType() {
        assertFalse(
            actual = decoder.canDecode(
                elementType = ResolvableType.forClass(HelloMessage::class.java),
                mimeType = MediaType.APPLICATION_XML
            )
        )
    }

    @Test
    fun decode_nonNdjson_joinsMultipleBuffersIntoSingleObject() {
        val flux = decoder.decode(
            inputStream = Flux.just(buffer(json = """{"id":1,"na"""), buffer(json = """me":"ghost"}""")),
            elementType = ResolvableType.forClass(HelloMessage::class.java),
            mimeType = MediaType.APPLICATION_JSON,
            hints = null
        )

        StepVerifier.create(flux)
            .assertNext { value -> assertEquals(expected = HelloMessage(id = 1, name = "ghost"), actual = value) }
            .verifyComplete()
    }

    @Test
    fun decode_ndjson_mapsEachNewlineDelimitedBufferToItsOwnObject() {
        val ndjson = MimeType("application", "x-ndjson")
        val flux = decoder.decode(
            inputStream = Flux.just(
                buffer(json = "{\"id\":1,\"name\":\"a\"}\n"),
                buffer(json = "{\"id\":2,\"name\":\"b\"}\n")
            ),
            elementType = ResolvableType.forClass(HelloMessage::class.java),
            mimeType = ndjson,
            hints = null
        )

        StepVerifier.create(flux)
            .assertNext { value -> assertEquals(expected = HelloMessage(id = 1, name = "a"), actual = value) }
            .assertNext { value -> assertEquals(expected = HelloMessage(id = 2, name = "b"), actual = value) }
            .verifyComplete()
    }

    @Test
    fun decode_ndjson_splitsMultipleRecordsDeliveredInASingleBuffer() {
        // Regression: NDJSON bodies can arrive as one network buffer, not one per line; naive
        // 1 buffer -> 1 object mapping silently dropped every record after the first.
        val ndjson = MimeType("application", "x-ndjson")
        val flux = decoder.decode(
            inputStream = Flux.just(buffer(json = "{\"id\":1,\"name\":\"a\"}\n{\"id\":2,\"name\":\"b\"}\n")),
            elementType = ResolvableType.forClass(HelloMessage::class.java),
            mimeType = ndjson,
            hints = null
        )

        StepVerifier.create(flux)
            .assertNext { value -> assertEquals(expected = HelloMessage(id = 1, name = "a"), actual = value) }
            .assertNext { value -> assertEquals(expected = HelloMessage(id = 2, name = "b"), actual = value) }
            .verifyComplete()
    }

    @Test
    fun decode_ndjson_reassemblesARecordSplitAcrossBufferBoundary() {
        val ndjson = MimeType("application", "x-ndjson")
        val flux = decoder.decode(
            inputStream = Flux.just(
                buffer(json = "{\"id\":1,\"na"),
                buffer(json = "me\":\"a\"}\n{\"id\":2,\"name\":\"b\"}\n")
            ),
            elementType = ResolvableType.forClass(HelloMessage::class.java),
            mimeType = ndjson,
            hints = null
        )

        StepVerifier.create(flux)
            .assertNext { value -> assertEquals(expected = HelloMessage(id = 1, name = "a"), actual = value) }
            .assertNext { value -> assertEquals(expected = HelloMessage(id = 2, name = "b"), actual = value) }
            .verifyComplete()
    }

    @Test
    fun decode_ndjson_decodesFinalLineWithoutTrailingNewline() {
        val ndjson = MimeType("application", "x-ndjson")
        val flux = decoder.decode(
            inputStream = Flux.just(buffer(json = "{\"id\":1,\"name\":\"a\"}\n{\"id\":2,\"name\":\"b\"}")),
            elementType = ResolvableType.forClass(HelloMessage::class.java),
            mimeType = ndjson,
            hints = null
        )

        StepVerifier.create(flux)
            .assertNext { value -> assertEquals(expected = HelloMessage(id = 1, name = "a"), actual = value) }
            .assertNext { value -> assertEquals(expected = HelloMessage(id = 2, name = "b"), actual = value) }
            .verifyComplete()
    }

    @Test
    fun decodeToMono_returnsSingleJoinedObject() {
        val mono = decoder.decodeToMono(
            inputStream = Flux.just(buffer(json = """{"id":7,"name":"mono"}""")),
            elementType = ResolvableType.forClass(HelloMessage::class.java),
            mimeType = MediaType.APPLICATION_JSON,
            hints = null
        )

        StepVerifier.create(mono)
            .assertNext { value -> assertEquals(expected = HelloMessage(id = 7, name = "mono"), actual = value) }
            .verifyComplete()
    }

    @Test
    fun decode_malformedJsonWrapsAsGhostJsonException() {
        val flux = decoder.decode(
            inputStream = Flux.just(buffer(json = """{"id":1,"name":""")),
            elementType = ResolvableType.forClass(HelloMessage::class.java),
            mimeType = MediaType.APPLICATION_JSON,
            hints = null
        )

        StepVerifier.create(flux)
            .verifyErrorSatisfies { error -> assertTrue(actual = error is GhostJsonException) }
    }

    @Test
    fun decode_unregisteredTypeWrapsAsGhostJsonException() {
        val flux = decoder.decode(
            inputStream = Flux.just(buffer(json = """{"value":1}""")),
            elementType = ResolvableType.forClass(UnregisteredReactiveMessage::class.java),
            mimeType = MediaType.APPLICATION_JSON,
            hints = null
        )

        StepVerifier.create(flux)
            .verifyErrorSatisfies { error ->
                assertTrue(actual = error is GhostJsonException)
                assertTrue(actual = error.message!!.contains("UnregisteredReactiveMessage"))
            }
    }
}
