package com.ghost.serialization.spring

import com.ghost.serialization.spring.fixture.GhostSpringTestApplication
import com.ghost.serialization.spring.fixture.YamlSpringTestRegistryConfig
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(classes = [GhostSpringTestApplication::class])
@Import(YamlSpringTestRegistryConfig::class)
@AutoConfigureMockMvc
class GhostSpringYamlMvcIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun ghostYamlConverterSerializesResponseBody() {
        mockMvc.perform(get("/api/yaml/profile").accept(MediaType("application", "yaml")))
            .andExpect(status().isOk)
            .andExpect(content().contentTypeCompatibleWith(MediaType("application", "yaml")))
            .andExpect(content().string(org.hamcrest.Matchers.containsString("id: 1")))
            .andExpect(content().string(org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.containsString("name: ghost"),
                org.hamcrest.Matchers.containsString("name: \"ghost\""),
            )))
    }

    @Test
    fun ghostYamlConverterDeserializesRequestBody() {
        mockMvc.perform(
            post("/api/yaml/profile")
                .contentType(MediaType("application", "yaml"))
                .content(
                    """
                    id: 42
                    name: boot
                    """.trimIndent()
                ),
        )
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.anyOf(
                org.hamcrest.Matchers.containsString("name: BOOT"),
                org.hamcrest.Matchers.containsString("name: \"BOOT\""),
            )))
    }
}
