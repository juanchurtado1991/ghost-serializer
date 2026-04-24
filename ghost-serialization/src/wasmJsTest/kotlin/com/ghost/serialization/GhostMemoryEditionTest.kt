package com.ghost.serialization

import com.ghost.serialization.generated.CharacterResponse
import com.ghost.serialization.generated.GhostJsRegistryInitializer
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * Unit tests for the v1.1.10 "Memory Edition" features.
 * Focuses on raw byte deserialization to ensure that offloading 
 * string encoding to JS doesn't corrupt the data contract.
 */
class GhostMemoryEditionTest {

    @BeforeTest
    fun setup() {
        com.ghost.serialization.generated.GhostAutoRegistry.registerAll()
        com.ghost.serialization.generated.GhostJsRegistryInitializer.register()
    }

    @Test
    fun testRawByteDeserializationIntegrity() {
        // Simulating a payload that would come from JS TextEncoder.encode()
        val json = """{"results":[{"id":1,"name":"Rick Sanchez","status":"Alive"}]}"""
        val bytes = json.encodeToByteArray()
        
        // Use the same internal API the JS bridge uses
        val serializer = Ghost.getSerializerByName("CharacterResponse") as? com.ghost.serialization.core.contract.GhostSerializer<CharacterResponse>
        val result = serializer?.deserialize(com.ghost.serialization.core.parser.GhostJsonReader(bytes))
        
        assertNotNull(result, "Deserialization should return a valid object")
        assertEquals(1, result.results.size)
        assertEquals("Rick Sanchez", result.results[0].name)
        assertEquals("Alive", result.results[0].status)
    }

    @Test
    fun testSpecialCharactersInRawBytes() {
        // Test with UTF-8 characters to ensure TextEncoder -> WASM bridge is safe
        val json = """{"results":[{"id":42,"name":"Mörty Smith 🧪","status":"Unknown"}]}"""
        val bytes = json.encodeToByteArray()
        
        val serializer = Ghost.getSerializerByName("CharacterResponse") as? com.ghost.serialization.core.contract.GhostSerializer<CharacterResponse>
        val result = serializer?.deserialize(com.ghost.serialization.core.parser.GhostJsonReader(bytes))
        
        assertNotNull(result)
        assertEquals("Mörty Smith 🧪", result.results[0].name)
    }

    @Test
    fun testEmptyPayloadHandling() {
        val json = """{"results":[]}"""
        val bytes = json.encodeToByteArray()
        
        val serializer = Ghost.getSerializerByName("CharacterResponse") as? com.ghost.serialization.core.contract.GhostSerializer<CharacterResponse>
        val result = serializer?.deserialize(com.ghost.serialization.core.parser.GhostJsonReader(bytes))
        
        assertNotNull(result)
        assertEquals(0, result.results.size)
    }
}
