@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.BooleanCoercionModel
import com.ghost.serialization.integration.model.UserWithValueClass
import com.ghost.serialization.integration.model.UserId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class GhostCoercionTest {

    @Test
    fun testBooleanCoercion() {
        // "1" and "0" as booleans
        val json = """{"isActive": 1, "isEnabled": 0}"""
        
        // Should fail by default
        assertFails {
            Ghost.deserialize<BooleanCoercionModel>(json.encodeToByteArray())
        }

        // Should pass with coercion enabled
        val result = Ghost.deserialize<BooleanCoercionModel>(
            json.encodeToByteArray(),
            options = { it.coerceBooleans = true }
        )
        
        assertEquals(true, result.isActive)
        assertEquals(false, result.isEnabled)
    }

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
