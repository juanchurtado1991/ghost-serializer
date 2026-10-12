package com.ghost.serialization.spring

import com.ghost.serialization.Ghost
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.spring.fixture.HelloMessage
import com.ghost.serialization.spring.fixture.YamlProfileMessage
import com.ghost.serialization.spring.fixture.YamlSpringTestRegistry
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.core.ResolvableType
import org.springframework.core.io.buffer.DefaultDataBufferFactory
import org.springframework.http.MediaType
import org.springframework.mock.http.MockHttpInputMessage
import reactor.core.publisher.Flux
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Empty and multi-megabyte bodies on the MVC converters and the WebFlux decoders, JSON and YAML. */
class GhostSpringBodySizeTest {

    private val bigText = "n".repeat(3_000_000)
    private val yamlMediaType = MediaType.parseMediaType("application/yaml")

    @BeforeEach
    fun setup() {
        Ghost.addRegistry(registry = YamlSpringTestRegistry)
    }

    private fun message(text: String) = MockHttpInputMessage(text.encodeToByteArray())

    private fun decodeJson(text: String): Any? = GhostReactiveDecoder().decodeToMono(
        Flux.just(DefaultDataBufferFactory().wrap(text.encodeToByteArray())),
        ResolvableType.forClass(HelloMessage::class.java),
        MediaType.APPLICATION_JSON,
        null
    ).block()

    private fun decodeYaml(text: String): Any? = GhostYamlReactiveDecoder().decodeToMono(
        Flux.just(DefaultDataBufferFactory().wrap(text.encodeToByteArray())),
        ResolvableType.forClass(YamlProfileMessage::class.java),
        yamlMediaType,
        null
    ).block()

    @Test
    fun mvcJsonEmptyBodyThrowsGhostJsonException() {
        assertFailsWith<GhostJsonException> {
            GhostHttpMessageConverter().read(HelloMessage::class.java, message(text = ""))
        }
    }

    @Test
    fun mvcJsonLargeBodyRoundTrips() {
        val decoded = GhostHttpMessageConverter().read(
            HelloMessage::class.java,
            message(text = "{\"id\":1,\"name\":\"$bigText\"}")
        )
        assertEquals(expected = HelloMessage(id = 1, name = bigText), actual = decoded)
    }

    @Test
    fun mvcYamlLargeBodyRoundTrips() {
        val decoded = GhostYamlHttpMessageConverter().read(
            YamlProfileMessage::class.java,
            message(text = "id: 1\nname: $bigText")
        )
        assertEquals(expected = YamlProfileMessage(id = 1, name = bigText), actual = decoded)
    }

    @Test
    fun webFluxJsonEmptyBodyThrowsGhostJsonException() {
        assertFailsWith<GhostJsonException> { decodeJson(text = "") }
    }

    @Test
    fun webFluxJsonLargeBodyRoundTrips() {
        assertEquals(
            expected = HelloMessage(id = 1, name = bigText),
            actual = decodeJson(text = "{\"id\":1,\"name\":\"$bigText\"}")
        )
    }

    @Test
    fun webFluxYamlLargeBodyRoundTrips() {
        assertEquals(
            expected = YamlProfileMessage(id = 1, name = bigText),
            actual = decodeYaml(text = "id: 1\nname: $bigText")
        )
    }

    @Test
    fun webFluxYamlEmptyBodyReadsAsAnEmptyMapping() {
        assertEquals(
            expected = decodeYaml(text = "{}"),
            actual = decodeYaml(text = "")
        )
    }
}
