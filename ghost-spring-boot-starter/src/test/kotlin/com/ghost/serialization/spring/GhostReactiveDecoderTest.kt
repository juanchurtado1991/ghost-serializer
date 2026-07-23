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
 * Direct unit tests for [GhostReactiveDecoder] — no Spring context, so these run fast and
 * target the specific canDecode/decode/decodeToMono branches precisely (multi-buffer join
 * vs per-buffer ndjson streaming, error wrapping). End-to-end wiring through a real WebFlux
 * app is covered separately by [GhostSpringWebFluxIntegrationTest].
 */
class GhostReactiveDecoderTest {

    private val decoder = GhostReactiveDecoder()
    private val bufferFactory = DefaultDataBufferFactory()

    private fun buffer(json: String): DataBuffer = bufferFactory.wrap(json.encodeToByteArray())

    @Test
    fun canDecode_trueForAnnotatedTypeWithSupportedMimeType() {
        assertTrue(
            decoder.canDecode(ResolvableType.forClass(HelloMessage::class.java), MediaType.APPLICATION_JSON)
        )
    }

    @Test
    fun canDecode_falseForUnregisteredType() {
        assertFalse(
            decoder.canDecode(
                ResolvableType.forClass(UnregisteredReactiveMessage::class.java),
                MediaType.APPLICATION_JSON
            )
        )
    }

    @Test
    fun canDecode_falseForUnsupportedMimeType() {
        assertFalse(
            decoder.canDecode(ResolvableType.forClass(HelloMessage::class.java), MediaType.APPLICATION_XML)
        )
    }

    @Test
    fun decode_nonNdjson_joinsMultipleBuffersIntoSingleObject() {
        val flux = decoder.decode(
            Flux.just(buffer("""{"id":1,"na"""), buffer("""me":"ghost"}""")),
            ResolvableType.forClass(HelloMessage::class.java),
            MediaType.APPLICATION_JSON,
            null
        )

        StepVerifier.create(flux)
            .assertNext { value -> assertEquals(HelloMessage(1, "ghost"), value) }
            .verifyComplete()
    }

    @Test
    fun decode_ndjson_mapsEachNewlineDelimitedBufferToItsOwnObject() {
        val ndjson = MimeType("application", "x-ndjson")
        val flux = decoder.decode(
            Flux.just(buffer("{\"id\":1,\"name\":\"a\"}\n"), buffer("{\"id\":2,\"name\":\"b\"}\n")),
            ResolvableType.forClass(HelloMessage::class.java),
            ndjson,
            null
        )

        StepVerifier.create(flux)
            .assertNext { value -> assertEquals(HelloMessage(1, "a"), value) }
            .assertNext { value -> assertEquals(HelloMessage(2, "b"), value) }
            .verifyComplete()
    }

    @Test
    fun decode_ndjson_splitsMultipleRecordsDeliveredInASingleBuffer() {
        // Regression test: a small multi-line NDJSON body typically arrives as ONE network
        // buffer, not one buffer per line. Naively mapping 1 buffer -> 1 decoded object (the
        // previous implementation) silently dropped every record after the first.
        val ndjson = MimeType("application", "x-ndjson")
        val flux = decoder.decode(
            Flux.just(buffer("{\"id\":1,\"name\":\"a\"}\n{\"id\":2,\"name\":\"b\"}\n")),
            ResolvableType.forClass(HelloMessage::class.java),
            ndjson,
            null
        )

        StepVerifier.create(flux)
            .assertNext { value -> assertEquals(HelloMessage(1, "a"), value) }
            .assertNext { value -> assertEquals(HelloMessage(2, "b"), value) }
            .verifyComplete()
    }

    @Test
    fun decode_ndjson_reassemblesARecordSplitAcrossBufferBoundary() {
        val ndjson = MimeType("application", "x-ndjson")
        val flux = decoder.decode(
            Flux.just(buffer("{\"id\":1,\"na"), buffer("me\":\"a\"}\n{\"id\":2,\"name\":\"b\"}\n")),
            ResolvableType.forClass(HelloMessage::class.java),
            ndjson,
            null
        )

        StepVerifier.create(flux)
            .assertNext { value -> assertEquals(HelloMessage(1, "a"), value) }
            .assertNext { value -> assertEquals(HelloMessage(2, "b"), value) }
            .verifyComplete()
    }

    @Test
    fun decode_ndjson_decodesFinalLineWithoutTrailingNewline() {
        val ndjson = MimeType("application", "x-ndjson")
        val flux = decoder.decode(
            Flux.just(buffer("{\"id\":1,\"name\":\"a\"}\n{\"id\":2,\"name\":\"b\"}")),
            ResolvableType.forClass(HelloMessage::class.java),
            ndjson,
            null
        )

        StepVerifier.create(flux)
            .assertNext { value -> assertEquals(HelloMessage(1, "a"), value) }
            .assertNext { value -> assertEquals(HelloMessage(2, "b"), value) }
            .verifyComplete()
    }

    @Test
    fun decodeToMono_returnsSingleJoinedObject() {
        val mono = decoder.decodeToMono(
            Flux.just(buffer("""{"id":7,"name":"mono"}""")),
            ResolvableType.forClass(HelloMessage::class.java),
            MediaType.APPLICATION_JSON,
            null
        )

        StepVerifier.create(mono)
            .assertNext { value -> assertEquals(HelloMessage(7, "mono"), value) }
            .verifyComplete()
    }

    @Test
    fun decode_malformedJsonWrapsAsGhostJsonException() {
        val flux = decoder.decode(
            Flux.just(buffer("""{"id":1,"name":""")),
            ResolvableType.forClass(HelloMessage::class.java),
            MediaType.APPLICATION_JSON,
            null
        )

        StepVerifier.create(flux)
            .verifyErrorSatisfies { error -> assertTrue(error is GhostJsonException) }
    }

    @Test
    fun decode_unregisteredTypeWrapsAsGhostJsonException() {
        val flux = decoder.decode(
            Flux.just(buffer("""{"value":1}""")),
            ResolvableType.forClass(UnregisteredReactiveMessage::class.java),
            MediaType.APPLICATION_JSON,
            null
        )

        StepVerifier.create(flux)
            .verifyErrorSatisfies { error ->
                assertTrue(error is GhostJsonException)
                assertTrue(error.message!!.contains("UnregisteredReactiveMessage"))
            }
    }
}
