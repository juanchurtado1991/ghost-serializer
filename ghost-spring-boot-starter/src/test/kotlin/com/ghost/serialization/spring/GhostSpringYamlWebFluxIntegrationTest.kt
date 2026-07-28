package com.ghost.serialization.spring

import com.ghost.serialization.spring.fixture.YamlSpringTestRegistryConfig
import com.ghost.serialization.spring.reactivefixture.GhostSpringReactiveTestApplication
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.reactive.server.WebTestClient

@SpringBootTest(
    classes = [GhostSpringReactiveTestApplication::class],
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = ["spring.main.web-application-type=reactive"]
)
@Import(YamlSpringTestRegistryConfig::class)
@AutoConfigureWebTestClient
class GhostSpringYamlWebFluxIntegrationTest {

    @Autowired
    private lateinit var webTestClient: WebTestClient

    private val yamlMediaType = MediaType("application", "yaml")

    @Test
    fun ghostYamlReactiveEncoderSerializesMonoResponseBody() {
        webTestClient.get().uri("/api/reactive/yaml/profile")
            .accept(yamlMediaType)
            .exchange()
            .expectStatus().isOk
            .expectHeader().contentTypeCompatibleWith(yamlMediaType)
            .expectBody(String::class.java)
            .value { body ->
                kotlin.test.assertTrue(body.contains("id: 1"))
                kotlin.test.assertTrue(
                    body.contains("name: ghost") || body.contains("name: \"ghost\"")
                )
            }
    }

    @Test
    fun ghostYamlReactiveDecoderDeserializesMonoRequestBody() {
        webTestClient.post().uri("/api/reactive/yaml/profile")
            .contentType(yamlMediaType)
            .accept(yamlMediaType)
            .bodyValue(
                """
                id: 42
                name: boot
                """.trimIndent()
            )
            .exchange()
            .expectStatus().isOk
            .expectBody(String::class.java)
            .value { body ->
                kotlin.test.assertTrue(
                    body.contains("name: BOOT") || body.contains("name: \"BOOT\"")
                )
            }
    }
}
