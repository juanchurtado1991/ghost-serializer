package com.ghost.serialization

import com.ghost.serialization.benchmark.CharacterResponse
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for the v1.1.10 "Memory Edition" features.
 * Focuses on raw byte deserialization to ensure that offloading
 * string encoding to JS doesn't corrupt the data contract.
 */
@Suppress("UNCHECKED_CAST")
class GhostMemoryEditionTest {

    @BeforeTest
    fun setup() {
        com.ghost.serialization.generated.GhostAutoRegistry.registerAll()
        com.ghost.serialization.generated.GhostJsRegistryInitializer.register()
    }

    @Test
    fun testRawByteDeserializationIntegrity() {
        // Simulating a payload that would come from JS TextEncoder.encode()
        val json =
            """{"info": {"count": 1, "pages": 1}, "results":[{"id":1,"name":"Rick Sanchez","status":"Alive", "origin": {"name": "Earth", "url": ""}, "location": {"name": "Earth", "url": ""}}]}"""
        val bytes = json.encodeToByteArray()

        // Use the same internal API the JS bridge uses
        val serializer =
            Ghost.getSerializerByName("CharacterResponse") as? com.ghost.serialization.core.contract.GhostSerializer<CharacterResponse>
        val result =
            serializer?.deserialize(com.ghost.serialization.core.parser.GhostJsonReader(bytes))

        assertNotNull(result, "Deserialization should return a valid object")
        assertEquals(1, result.results.size)
        assertEquals("Rick Sanchez", result.results[0].name)
        assertEquals("Alive", result.results[0].status.name)
    }

    @Test
    fun testSpecialCharactersInRawBytes() {
        // Test with UTF-8 characters to ensure TextEncoder -> WASM bridge is safe
        val json =
            """{"info": {"count": 1, "pages": 1}, "results":[{"id":42,"name":"Mörty Smith 🧪","status":"unknown", "origin": {"name": "Earth", "url": ""}, "location": {"name": "Earth", "url": ""}}]}"""
        val bytes = json.encodeToByteArray()

        val serializer =
            Ghost.getSerializerByName("CharacterResponse") as? com.ghost.serialization.core.contract.GhostSerializer<CharacterResponse>
        val result =
            serializer?.deserialize(com.ghost.serialization.core.parser.GhostJsonReader(bytes))

        assertNotNull(result)
        assertEquals("Mörty Smith 🧪", result.results[0].name)
        assertEquals("unknown", result.results[0].status.name)
    }

    @Test
    fun testEmptyPayloadHandling() {
        val json = """{"info": {"count": 0, "pages": 0}, "results": []}"""
        val bytes = json.encodeToByteArray()

        val serializer =
            Ghost.getSerializerByName("CharacterResponse") as? com.ghost.serialization.core.contract.GhostSerializer<CharacterResponse>
        val result =
            serializer?.deserialize(com.ghost.serialization.core.parser.GhostJsonReader(bytes))

        assertNotNull(result)
        assertEquals(0, result.results.size)
    }
}
