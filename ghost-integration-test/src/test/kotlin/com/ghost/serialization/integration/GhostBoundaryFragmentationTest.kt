package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.Object40
import com.ghost.serialization.integration.model.Object41
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostBoundaryFragmentationTest {

    @Test
    fun testObjectWithExactly40Properties() {
        // Standard emitter path
        val obj = Object40(p1 = 100, p40 = 400)
        val json = Ghost.serialize(obj)
        
        val result = Ghost.deserialize<Object40>(json)
        assertEquals(100, result.p1)
        assertEquals(400, result.p40)
        assertEquals(2, result.p2)
    }

    @Test
    fun testObjectWith41Properties() {
        // Fragmented emitter path (Threshold is > 40)
        val obj = Object41(p1 = 101, p40 = 401, p41 = 411)
        
        // Test String mode
        val json = Ghost.serialize(obj)
        val resultString = Ghost.deserialize<Object41>(json)
        assertEquals(101, resultString.p1)
        assertEquals(401, resultString.p40)
        assertEquals(411, resultString.p41)
        
        // Test Bytes mode
        val bytes = Ghost.encodeToBytes(obj)
        val resultBytes = Ghost.deserialize<Object41>(bytes)
        assertEquals(101, resultBytes.p1)
        assertEquals(401, resultBytes.p40)
        assertEquals(411, resultBytes.p41)
    }

    @Test
    fun testPartialUpdateInFragmentedObject() {
        // Test that fragmented decoder handles missing fields correctly (defaults)
        val partialJson = """{"p1": 999, "p41": 888}"""
        val result = Ghost.deserialize<Object41>(partialJson.encodeToByteArray())
        
        assertEquals(999, result.p1)
        assertEquals(888, result.p41)
        assertEquals(2, result.p2) // Default
        assertEquals(40, result.p40) // Default
    }
}
