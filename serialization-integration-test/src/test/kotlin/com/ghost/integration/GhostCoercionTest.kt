package com.ghost.integration

import com.ghostserializer.Ghost
import com.ghost.integration.model.UserWithValueClass
import com.ghost.integration.model.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class GhostCoercionTest {

    @Test
    fun testNumericCoercion() {
        // "123" instead of 123
        val json = """{"id": "123", "name": "Coerced User"}"""
        
        // Should fail by default
        assertFails {
            Ghost.deserialize<UserWithValueClass>(json.encodeToByteArray())
        }

        // Should pass with coercion enabled
        val result = Ghost.deserialize<UserWithValueClass>(
            json.encodeToByteArray(),
            options = { it.coerceStringsToNumbers = true }
        )
        
        assertEquals(UserId(123), result.id)
        assertEquals("Coerced User", result.name)
    }

    @Test
    fun testIntCoercion() {
        val json = """{"id": "456", "name": "Int Coerced"}"""
        val result = Ghost.deserialize<UserWithValueClass>(
            json.encodeToByteArray(),
            options = { it.coerceStringsToNumbers = true }
        )
        assertEquals(UserId(456), result.id)
    }
}
