package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.integration.model.GhostEnumWrapper
import com.ghost.serialization.integration.model.GhostStandardsEnum
import com.ghost.serialization.integration.model.ResilientEnumModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class GhostEnumResilienceTest {

    @Test
    fun testStrictEnumFailure() {
        val json = "{\"status\":\"UNKNOWN_VALUE\"}"
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<GhostEnumWrapper>(json)
        }
    }

    @Test
    fun testResilientEnumDefault() {
        val json = "{\"status\":\"UNKNOWN_VALUE\"}"
        val decoded = Ghost.deserialize<ResilientEnumModel>(json)

        assertEquals(GhostStandardsEnum.Standard, decoded.status)
    }

    @Test
    fun testResilientEnumNullable() {
        val json = "{\"nullableStatus\":\"UNKNOWN_VALUE\"}"
        val decoded = Ghost.deserialize<ResilientEnumModel>(json)

        assertNull(decoded.nullableStatus)
    }
}
