@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.GodObject
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostGodObjectTest {

    @Test
    fun testGodObjectFragmentedDeserialization() {
        // Only provide p0, p40, p59. Others use defaults.
        val json = """{"p0": 100, "p40": 400, "p59": 590}"""
        
        val result = Ghost.deserialize<GodObject>(json.encodeToByteArray())
        
        assertEquals(100, result.p0)
        assertEquals(400, result.p40)
        assertEquals(590, result.p59)
        assertEquals(1, result.p1)
    }

    @Test
    fun testGodObjectFragmentedSerialization() {
        val god = GodObject(p0 = 100, p40 = 400, p59 = 590)
        
        val bytes = Ghost.encodeToBytes(god)
        val json = bytes.decodeToString()
        
        // Verify some keys are present
        assert(json.contains("\"p0\":100"))
        assert(json.contains("\"p40\":400"))
        assert(json.contains("\"p59\":590"))
        assert(json.contains("\"p1\":1"))
        
        // Deserialize back to verify integrity
        val result = Ghost.deserialize<GodObject>(bytes)
        assertEquals(100, result.p0)
        assertEquals(400, result.p40)
        assertEquals(590, result.p59)
        assertEquals(1, result.p1)
    }
}
