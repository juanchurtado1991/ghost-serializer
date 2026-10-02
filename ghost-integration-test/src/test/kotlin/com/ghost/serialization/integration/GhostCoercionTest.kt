@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.BooleanCoercionModel
import com.ghost.serialization.integration.model.UserId
import com.ghost.serialization.integration.model.UserWithValueClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class GhostCoercionTest {

    @Test
    fun testBooleanCoercion() {
        val json = """{"isActive": 1, "isEnabled": 0}"""

        assertFails {
            Ghost.deserialize<BooleanCoercionModel>(json.encodeToByteArray())
        }

        val result = Ghost.deserialize<BooleanCoercionModel>(
            json.encodeToByteArray(),
            options = { it.coerceBooleans = true }
        )

        assertEquals(expected = true, actual = result.isActive)
        assertEquals(expected = false, actual = result.isEnabled)
    }

    @Test
    fun testNumericCoercion() {
        val json = """{"id": "123", "name": "Coerced User"}"""

        assertFails {
            Ghost.deserialize<UserWithValueClass>(json.encodeToByteArray())
        }

        val result = Ghost.deserialize<UserWithValueClass>(
            json.encodeToByteArray(),
            options = { it.coerceStringsToNumbers = true }
        )

        assertEquals(expected = UserId(value = 123), actual = result.id)
        assertEquals(expected = "Coerced User", actual = result.name)
    }

    @Test
    fun testIntCoercion() {
        val json = """{"id": "456", "name": "Int Coerced"}"""
        val result = Ghost.deserialize<UserWithValueClass>(
            json.encodeToByteArray(),
            options = { it.coerceStringsToNumbers = true }
        )
        assertEquals(expected = UserId(value = 456), actual = result.id)
    }
}
