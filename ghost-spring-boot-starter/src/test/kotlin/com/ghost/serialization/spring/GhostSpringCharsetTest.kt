package com.ghost.serialization.spring

import com.ghost.serialization.spring.fixture.HelloMessage
import org.junit.jupiter.api.Test
import org.springframework.core.ResolvableType
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.MediaType
import org.springframework.mock.http.MockHttpInputMessage
import reactor.core.publisher.Flux
import reactor.test.StepVerifier
import kotlin.test.assertEquals

/** A body declared in a non-UTF-8 charset is decoded with it by both the MVC converter and the WebFlux decoder. */
class GhostSpringCharsetTest {

    private val expected = HelloMessage(id = 7, name = "José Ñandú")
    private val json = "{\"id\":7,\"name\":\"José Ñandú\"}"

    @Test
    fun mvcConverterDecodesIso88591Body() {
        val message = MockHttpInputMessage(json.toByteArray(charset = Charsets.ISO_8859_1))
        message.headers.contentType = MediaType("application", "json", Charsets.ISO_8859_1)
        assertEquals(
            expected = expected,
            actual = GhostHttpMessageConverter().read(HelloMessage::class.java, message)
        )
    }

    @Test
    fun mvcConverterDecodesUtf16Body() {
        val message = MockHttpInputMessage(json.toByteArray(charset = Charsets.UTF_16BE))
        message.headers.contentType = MediaType("application", "json", Charsets.UTF_16BE)
        assertEquals(
            expected = expected,
            actual = GhostHttpMessageConverter().read(HelloMessage::class.java, message)
        )
    }

    @Test
    fun reactiveDecoderDecodesIso88591Body() {
        val buffer = DefaultDataBufferFactory().wrap(json.toByteArray(charset = Charsets.ISO_8859_1))
        val flux = GhostReactiveDecoder().decode(
            inputStream = Flux.just(buffer),
            elementType = ResolvableType.forClass(HelloMessage::class.java),
            mimeType = MediaType("application", "json", Charsets.ISO_8859_1),
            hints = null
        )
        StepVerifier.create(flux)
            .assertNext { value -> assertEquals(expected = expected, actual = value) }
            .verifyComplete()
    }
}
