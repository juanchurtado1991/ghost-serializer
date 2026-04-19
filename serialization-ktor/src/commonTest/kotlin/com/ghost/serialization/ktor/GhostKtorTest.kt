package com.ghostserializer.ktor

import io.ktor.http.ContentType
import io.ktor.util.reflect.typeInfo
import io.ktor.utils.io.charsets.Charsets
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertNotNull

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
            Charsets.UTF_8,
            typeInfo,
            data
        )
        
        // Then (Verify it produced JSON/Content)
        assertNotNull(content)
    }
}
