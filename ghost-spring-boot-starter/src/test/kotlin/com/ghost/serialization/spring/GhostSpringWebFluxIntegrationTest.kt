package com.ghost.serialization.spring

import com.ghost.serialization.spring.reactivefixture.GhostSpringReactiveTestApplication
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

/**
 * End-to-end proof that [GhostWebFluxAutoConfiguration] actually registers
 * [GhostReactiveDecoder]/[GhostReactiveEncoder] on a real reactive server — unit-level
 * branch coverage for those two classes lives in [GhostReactiveDecoderTest]/
 * [GhostReactiveEncoderTest].
 */
@SpringBootTest(
    classes = [GhostSpringReactiveTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.web-application-type=reactive"]
)
@AutoConfigureWebTestClient
class GhostSpringWebFluxIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun ghostReactiveEncoderSerializesMonoResponseBody() {
        webTestClient.get().uri("/api/reactive/hello")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentType(MediaType.APPLICATION_JSON)
            .expectBody()
            .jsonPath("$.id").isEqualTo(1)
            .jsonPath("$.name").isEqualTo("ghost")
    }

    @Test
    fun ghostReactiveDecoderDeserializesMonoRequestBody() {
        webTestClient.post().uri("/api/reactive/hello")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"id":42,"name":"boot"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.name").isEqualTo("BOOT")
    }

    @Test
    fun ghostReactiveEncoderStreamsNdjsonForFluxResponse() {
        val body = webTestClient.get().uri("/api/reactive/hello-stream")
            .accept(MediaType("application", "x-ndjson"))
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody!!

        val lines = body.trim().split("\n")
        kotlin.test.assertEquals(2, lines.size)
        kotlin.test.assertEquals("""{"id":1,"name":"a"}""", lines[0])
        kotlin.test.assertEquals("""{"id":2,"name":"b"}""", lines[1])
    }

    @Test
    fun ghostReactiveDecoderStreamsNdjsonForFluxRequestBody() {
        val requestBody = """{"id":1,"name":"a"}
{"id":2,"name":"b"}
"""
        val body = webTestClient.post().uri("/api/reactive/hello-stream")
            .contentType(MediaType("application", "x-ndjson"))
            .accept(MediaType("application", "x-ndjson"))
            .bodyValue(requestBody)
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .returnResult()
            .responseBody!!

        val lines = body.trim().split("\n")
        kotlin.test.assertEquals(2, lines.size)
        kotlin.test.assertEquals("""{"id":1,"name":"A"}""", lines[0])
        kotlin.test.assertEquals("""{"id":2,"name":"B"}""", lines[1])
    }
}
