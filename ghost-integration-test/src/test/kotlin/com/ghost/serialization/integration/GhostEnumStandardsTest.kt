package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.GhostEnumWrapper
import com.ghost.serialization.integration.model.GhostStandardsEnum
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(InternalGhostApi::class)
class GhostEnumStandardsTest {

    @Test
    fun testEnumSerialNameStandards() {
        val json = """{"status":"advanced_match"}"""
        val decoded = Ghost.deserialize<GhostEnumWrapper>(json)

        assertEquals(expected = GhostStandardsEnum.Match, actual = decoded.status)

        val reSerialized = Ghost.serialize(decoded)
        assertEquals(expected = json, actual = reSerialized)
    }

    @Test
    fun testEnumGhostNameStandards() {
        val json = """{"status":"ghost_match"}"""
        val decoded = Ghost.deserialize<GhostEnumWrapper>(json)

        assertEquals(expected = GhostStandardsEnum.GhostMatch, actual = decoded.status)

        val reSerialized = Ghost.serialize(decoded)
        assertEquals(expected = json, actual = reSerialized)
    }

    @Test
    fun testEnumStandardMatch() {
        val json = """{"status":"Standard"}"""
        val decoded = Ghost.deserialize<GhostEnumWrapper>(json)

        assertEquals(expected = GhostStandardsEnum.Standard, actual = decoded.status)

        val reSerialized = Ghost.serialize(decoded)
        assertEquals(expected = json, actual = reSerialized)
    }

    @Test
    fun testNullableEnumStandards() {
        val json = """{"status":"Standard","optionalStatus":"advanced_match"}"""
        val decoded = Ghost.deserialize<GhostEnumWrapper>(json)

        assertEquals(expected = GhostStandardsEnum.Standard, actual = decoded.status)
        assertEquals(expected = GhostStandardsEnum.Match, actual = decoded.optionalStatus)
    }
}
