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
 * Verifies GhostHttpMessageConverter routes @GhostProtoSerialization types through
 * GhostProtoJsonFlatReader (via the generated serializer's `isProto` flag) — using the real
 * KSP-generated serializer for [com.ghost.serialization.spring.fixture.ProtoEventMessage], not
 * a hand-written stand-in. GhostHttpMessageConverter is registered globally (Spring's
 * HttpMessageConverter list isn't per-endpoint), so this also proves plain @GhostSerialization
 * and @GhostProtoSerialization types correctly coexist on the same converter instance.
 */
@SpringBootTest(classes = [GhostSpringTestApplication::class])
@AutoConfigureMockMvc
class GhostSpringProtoMvcIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Test
    fun respondsWithQuotedInt64() {
        mockMvc.perform(get("/api/proto/event"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.deviceId").value(Long.MAX_VALUE.toString()))
            .andExpect(jsonPath("$.label").value("sensor-1"))
    }

    @Test
    fun parsesQuotedInt64RequestBody() {
        mockMvc.perform(
            post("/api/proto/event")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"deviceId":"9223372036854775807","label":"sensor-2"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deviceId").value(Long.MAX_VALUE.toString()))
            .andExpect(jsonPath("$.label").value("sensor-2"))
    }

    @Test
    fun parsesBareNumericInt64RequestBody() {
        mockMvc.perform(
            post("/api/proto/event")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"deviceId":42,"label":"sensor-3"}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.deviceId").value("42"))
    }
}
