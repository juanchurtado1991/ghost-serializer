package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.GhostEnumWrapper
import com.ghost.serialization.integration.model.GhostStandardsEnum
import com.ghost.serialization.integration.model.ResilientEnumModel
import com.ghost.serialization.exception.GhostJsonException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertFailsWith

class GhostEnumResilienceTest {

    @Test
    fun testStrictEnumFailure() {
        // Unknown enum value in a standard model should fail
        val json = "{\"status\":\"UNKNOWN_VALUE\"}"
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<GhostEnumWrapper>(json)
        }
    }

    @Test
    fun testResilientEnumDefault() {
        // Unknown enum value with @GhostResilient and default value
        val json = "{\"status\":\"UNKNOWN_VALUE\"}"
        val decoded = Ghost.deserialize<ResilientEnumModel>(json)
        
        // Should fall back to default "Standard"
        assertEquals(GhostStandardsEnum.Standard, decoded.status)
    }

    @Test
    fun testResilientEnumNullable() {
        // Unknown enum value with @GhostResilient and nullable
        val json = "{\"nullableStatus\":\"UNKNOWN_VALUE\"}"
        val decoded = Ghost.deserialize<ResilientEnumModel>(json)
        
        // Should fall back to null
        assertNull(decoded.nullableStatus)
    }
}
