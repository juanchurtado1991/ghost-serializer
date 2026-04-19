package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.UserWithValueClass
import com.ghost.serialization.integration.model.UserId
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostValueClassTest {

    @Test
    fun testValueClassDeserialization() {
        val json = """{"id": 123, "name": "Ghost User"}"""
        val result = Ghost.deserialize<UserWithValueClass>(json.encodeToByteArray())
        
        assertEquals(UserId(123), result.id)
        assertEquals("Ghost User", result.name)
    }

    @Test
    fun testDirectValueClassDeserialization() {
        val json = "456"
        val result = Ghost.deserialize<UserId>(json.encodeToByteArray())
        assertEquals(UserId(456), result)
    }
}
