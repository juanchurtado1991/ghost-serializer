package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.Object40
import com.ghost.serialization.integration.model.Object41
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostBoundaryFragmentationTest {

    @Test
    fun testObjectWithExactly40Properties() {
        val obj = Object40(p1 = 100, p40 = 400)
        val json = Ghost.serialize(obj)

        val result = Ghost.deserialize<Object40>(json)
        assertEquals(expected = 100, actual = result.p1)
        assertEquals(expected = 400, actual = result.p40)
        assertEquals(expected = 2, actual = result.p2)
    }

    @Test
    fun testObjectWith41Properties() {
        // Fragmented emitter path (threshold is > 40 properties)
        val obj = Object41(p1 = 101, p40 = 401, p41 = 411)

        val json = Ghost.serialize(obj)
        val resultString = Ghost.deserialize<Object41>(json)
        assertEquals(expected = 101, actual = resultString.p1)
        assertEquals(expected = 401, actual = resultString.p40)
        assertEquals(expected = 411, actual = resultString.p41)

        val bytes = Ghost.encodeToBytes(obj)
        val resultBytes = Ghost.deserialize<Object41>(bytes)
        assertEquals(expected = 101, actual = resultBytes.p1)
        assertEquals(expected = 401, actual = resultBytes.p40)
        assertEquals(expected = 411, actual = resultBytes.p41)
    }

    @Test
    fun testPartialUpdateInFragmentedObject() {
        val partialJson = """{"p1": 999, "p41": 888}"""
        val result = Ghost.deserialize<Object41>(partialJson.encodeToByteArray())

        assertEquals(expected = 999, actual = result.p1)
        assertEquals(expected = 888, actual = result.p41)
        assertEquals(expected = 2, actual = result.p2)
        assertEquals(expected = 40, actual = result.p40)
    }
}
