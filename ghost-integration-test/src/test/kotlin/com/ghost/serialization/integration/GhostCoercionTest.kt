@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.BooleanCoercionModel
import com.ghost.serialization.integration.model.CoercionStressModel
import com.ghost.serialization.integration.model.UserId
import com.ghost.serialization.integration.model.UserWithValueClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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

        val maxIntJson = """{"id": "${Int.MAX_VALUE}", "name": "Max Int"}"""
        val maxIntResult = Ghost.deserialize<UserWithValueClass>(
            maxIntJson.encodeToByteArray(),
            options = { it.coerceStringsToNumbers = true }
        )
        assertEquals(expected = UserId(value = Int.MAX_VALUE), actual = maxIntResult.id)
    }

    @Test
    fun testBooleanCoercionStringForms() {
        val json = """
        {
            "b1": "true",
            "b2": "yes",
            "b3": "on",
            "b4": 1,
            "b5": "y",
            "b6": true
        }
        """.trimIndent()

        val model = Ghost.deserialize<CoercionStressModel>(json) {
            it.coerceBooleans = true
        }
        assertTrue(actual = model.b1)
        assertTrue(actual = model.b2)
        assertTrue(actual = model.b3)
        assertTrue(actual = model.b4)
        assertTrue(actual = model.b5)
        assertTrue(actual = model.b6)

        val jsonFalse = """
        {
            "b1": "false",
            "b2": "no",
            "b3": "off",
            "b4": 0,
            "b5": "n",
            "b6": false
        }
        """.trimIndent()

        val modelFalse = Ghost.deserialize<CoercionStressModel>(jsonFalse) {
            it.coerceBooleans = true
        }
        assertFalse(actual = modelFalse.b1)
        assertFalse(actual = modelFalse.b2)
        assertFalse(actual = modelFalse.b3)
        assertFalse(actual = modelFalse.b4)
        assertFalse(actual = modelFalse.b5)
        assertFalse(actual = modelFalse.b6)
    }
}
