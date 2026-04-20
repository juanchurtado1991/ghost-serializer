package com.ghost.serialization.integration

import com.ghost.serialization.integration.model.GhostEnumWrapper
import com.ghost.serialization.integration.model.GhostStandardsEnum
import com.ghost.serialization.Ghost
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostEnumStandardsTest {

    @Test
    fun testEnumSerialNameStandards() {
        val json = """{"status":"advanced_match"}"""
        val decoded = Ghost.deserialize<GhostEnumWrapper>(json)
        
        assertEquals(GhostStandardsEnum.Match, decoded.status)
        
        val reSerialized = Ghost.serialize(decoded)
        assertEquals(json, reSerialized)
    }

    @Test
    fun testEnumGhostNameStandards() {
        val json = """{"status":"ghost_match"}"""
        val decoded = Ghost.deserialize<GhostEnumWrapper>(json)
        
        assertEquals(GhostStandardsEnum.GhostMatch, decoded.status)
        
        val reSerialized = Ghost.serialize(decoded)
        assertEquals(json, reSerialized)
    }

    @Test
    fun testEnumStandardMatch() {
        val json = """{"status":"Standard"}"""
        val decoded = Ghost.deserialize<GhostEnumWrapper>(json)
        
        assertEquals(GhostStandardsEnum.Standard, decoded.status)
        
        val reSerialized = Ghost.serialize(decoded)
        assertEquals(json, reSerialized)
    }

    @Test
    fun testNullableEnumStandards() {
        val json = """{"status":"Standard","optionalStatus":"advanced_match"}"""
        val decoded = Ghost.deserialize<GhostEnumWrapper>(json)
        
        assertEquals(GhostStandardsEnum.Standard, decoded.status)
        assertEquals(GhostStandardsEnum.Match, decoded.optionalStatus)
    }
}
