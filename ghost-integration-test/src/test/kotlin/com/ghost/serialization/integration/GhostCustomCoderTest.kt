package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.CustomDateUser
import com.ghost.serialization.integration.model.LegacyUser
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostCustomCoderTest {

    @Test
    fun testCustomBooleanCoder() {
        val json = """{"id": 1, "isActive": "Y"}"""
        val result = Ghost.deserialize<LegacyUser>(json)

        assertEquals(expected = 1L, actual = result.id)
        assertEquals(expected = true, actual = result.isActive)

        val serialized = Ghost.serialize(result)
        assert(serialized.contains("\"isActive\":\"Y\""))
    }

    @Test
    fun testCustomDateCoder() {
        val json = """{"id": 100, "createdAt": "2023-10-15"}"""
        val result = Ghost.deserialize<CustomDateUser>(json)

        assertEquals(expected = 20231015L, actual = result.createdAt)

        val serialized = Ghost.serialize(result)
        assert(serialized.contains("\"createdAt\":\"2023-10-15\""))
    }
}
