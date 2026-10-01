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
        val json = """{"p0": 100, "p40": 400, "p59": 590}"""

        val result = Ghost.deserialize<GodObject>(json.encodeToByteArray())

        assertEquals(expected = 100, actual = result.p0)
        assertEquals(expected = 400, actual = result.p40)
        assertEquals(expected = 590, actual = result.p59)
        assertEquals(expected = 1, actual = result.p1)
    }

    @Test
    fun testGodObjectFragmentedSerialization() {
        val god = GodObject(p0 = 100, p40 = 400, p59 = 590)

        val bytes = Ghost.encodeToBytes(god)
        val json = bytes.decodeToString()

        assert(json.contains("\"p0\":100"))
        assert(json.contains("\"p40\":400"))
        assert(json.contains("\"p59\":590"))
        assert(json.contains("\"p1\":1"))

        val result = Ghost.deserialize<GodObject>(bytes)
        assertEquals(expected = 100, actual = result.p0)
        assertEquals(expected = 400, actual = result.p40)
        assertEquals(expected = 590, actual = result.p59)
        assertEquals(expected = 1, actual = result.p1)
    }
}
