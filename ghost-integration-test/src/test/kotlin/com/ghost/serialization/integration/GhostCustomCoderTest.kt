package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.LegacyUser
import com.ghost.serialization.integration.model.CustomDateUser
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostCustomCoderTest {

    @Test
    fun testCustomBooleanCoder() {
        val json = """{"id": 1, "isActive": "Y"}"""
        val result = Ghost.deserialize<LegacyUser>(json)
        
        assertEquals(1L, result.id)
        assertEquals(true, result.isActive)
        
        val serialized = Ghost.serialize(result)
        assert(serialized.contains("\"isActive\":\"Y\""))
    }

    @Test
    fun testCustomDateCoder() {
        // Test date format YYYY-MM-DD -> Long YYYYMMDD
        val json = """{"id": 100, "createdAt": "2023-10-15"}"""
        val result = Ghost.deserialize<CustomDateUser>(json)
        
        assertEquals(20231015L, result.createdAt)
        
        val serialized = Ghost.serialize(result)
        assert(serialized.contains("\"createdAt\":\"2023-10-15\""))
    }
}
