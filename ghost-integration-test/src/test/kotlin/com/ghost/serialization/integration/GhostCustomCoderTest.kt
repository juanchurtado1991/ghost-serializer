package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.CustomCoderStressModel
import com.ghost.serialization.integration.model.CustomDateUser
import com.ghost.serialization.integration.model.LegacyUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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

    @Test
    fun testCustomCoderHexAndNullDecoding() {
        val json = """
        {
            "id": "c1",
            "secret": "AABBCC",
            "score": null
        }
        """.trimIndent()

        val model = Ghost.deserialize<CustomCoderStressModel>(json)
        assertEquals(expected = "HEX:AABBCC", actual = model.secret)
        // score decoder returns -1 for null
        assertEquals(expected = -1, actual = model.score)

        val serialized = Ghost.serialize(model)
        assertTrue(actual = serialized.contains("\"secret\":\"AABBCC\""))
    }
}
