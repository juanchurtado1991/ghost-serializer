package com.ghost.serialization.spring

import com.ghost.serialization.spring.reactivefixture.GhostSpringReactiveTestApplication
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(
    classes = [GhostSpringReactiveTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.web-application-type=reactive"]
)
@AutoConfigureWebTestClient
class GhostSpringProtoWebFluxIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    @Test
    fun ghostReactiveDecoderParsesQuotedInt64RequestBody() {
        webTestClient.post().uri("/api/reactive/proto/event")
            .contentType(MediaType.APPLICATION_JSON)
            .bodyValue("""{"deviceId":"9223372036854775807","label":"sensor-2"}""")
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.deviceId").isEqualTo(Long.MAX_VALUE.toString())
            .jsonPath("$.label").isEqualTo("sensor-2")
    }

    @Test
    fun ghostReactiveEncoderWritesQuotedInt64ResponseBody() {
        webTestClient.get().uri("/api/reactive/proto/event")
            .accept(MediaType.APPLICATION_JSON)
            .exchange()
            .expectStatus().isOk
            .expectBody()
            .jsonPath("$.deviceId").isEqualTo(Long.MAX_VALUE.toString())
            .jsonPath("$.label").isEqualTo("sensor-1")
    }
}
