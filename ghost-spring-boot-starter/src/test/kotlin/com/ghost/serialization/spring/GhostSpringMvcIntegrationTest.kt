package com.ghost.serialization.spring

import com.ghost.serialization.spring.fixture.GhostSpringTestApplication
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

/**
 * Verifies Ghost auto-configuration on Spring Boot 3.4 with a real MVC controller
 * and KSP-generated [com.ghost.serialization.spring.fixture.HelloMessage] serializer.
 */
@SpringBootTest(classes = [GhostSpringTestApplication::class])
@AutoConfigureMockMvc
class GhostSpringMvcIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun ghostConverterSerializesResponseBody() {
        mockMvc.perform(get("/api/hello"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(1))
            .andExpect(jsonPath("$.name").value("ghost"))
    }

    @Test
    fun ghostConverterDeserializesRequestBody() {
        mockMvc.perform(
            post("/api/hello")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":42,"name":"boot"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(42))
            .andExpect(jsonPath("$.name").value("BOOT"))
    }

    @Test
    fun strictEndpointThrowsOnMissingComma() {
        org.junit.jupiter.api.assertThrows<Exception> {
            mockMvc.perform(
                post("/api/strict")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"id":1 "name":"boot"}"""),
            )
        }
    }

    @Test
    fun strictParamEndpointThrowsOnMissingComma() {
        org.junit.jupiter.api.assertThrows<Exception> {
            mockMvc.perform(
                post("/api/strict-param")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""{"id":1 "name":"boot"}"""),
            )
        }
    }

    @Test
    fun lenientEndpointPassesOnMissingComma() {
        mockMvc.perform(
            post("/api/hello")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":42 "name":"boot"}"""),
        )
            .andExpect(status().isOk)
    }

    @Test
    fun coercedEndpointCoercesPrimitiveValues() {
        mockMvc.perform(
            post("/api/coerce")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"id":"42","name":"boot"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(42))
    }
}
