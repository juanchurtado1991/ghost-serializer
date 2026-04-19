package com.ghostserializer.integration

import com.ghost.integration.model.GhostEnumWrapper
import com.ghost.integration.model.GhostSovereigntyEnum
import com.ghostserializer.Ghost
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostEnumSovereigntyTest {

    @Test
    fun testEnumSerialNameSovereignty() {
        val json = """{"status":"industrial_match"}"""
        val decoded = Ghost.deserialize<GhostEnumWrapper>(json)
        
        assertEquals(GhostSovereigntyEnum.Match, decoded.status)
        
        val reSerialized = Ghost.serialize(decoded)
        assertEquals(json, reSerialized)
    }

    @Test
    fun testEnumGhostNameSovereignty() {
        val json = """{"status":"ghost_match"}"""
        val decoded = Ghost.deserialize<GhostEnumWrapper>(json)
        
        assertEquals(GhostSovereigntyEnum.GhostMatch, decoded.status)
        
        val reSerialized = Ghost.serialize(decoded)
        assertEquals(json, reSerialized)
    }

    @Test
    fun testEnumStandardMatch() {
        val json = """{"status":"Standard"}"""
        val decoded = Ghost.deserialize<GhostEnumWrapper>(json)
        
        assertEquals(GhostSovereigntyEnum.Standard, decoded.status)
        
        val reSerialized = Ghost.serialize(decoded)
        assertEquals(json, reSerialized)
    }

    @Test
    fun testNullableEnumSovereignty() {
        val json = """{"status":"Standard","optionalStatus":"industrial_match"}"""
        val decoded = Ghost.deserialize<GhostEnumWrapper>(json)
        
        assertEquals(GhostSovereigntyEnum.Standard, decoded.status)
        assertEquals(GhostSovereigntyEnum.Match, decoded.optionalStatus)
    }
}
