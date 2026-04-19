package com.ghost.integration

import com.ghostserializer.Ghost
import com.ghost.integration.model.GodObject
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
        assertEquals(39, result.p39)
        assertEquals(41, result.p41)
    }
}
