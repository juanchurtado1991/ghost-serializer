package com.ghost.serialization.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.annotations.GhostSerialization
import io.ktor.client.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.*
import io.ktor.util.reflect.*
import kotlinx.coroutines.test.runTest
import kotlin.test.*

class GhostKtorTest {

    @Test
    fun testContentNegotiationIntegration() = runTest {
        val converter = GhostContentConverter()
        val data = "Hello Ghost"
        
        // Mocking type info
        val typeInfo = typeInfo<String>()
        
        // When serializing
        val content = converter.serialize(
            ContentType.Application.Json,
            io.ktor.utils.io.charsets.Charsets.UTF_8,
            typeInfo,
            data
        )
        
        // Then (Verify it produced JSON/Content)
        assertNotNull(content)
    }
}
