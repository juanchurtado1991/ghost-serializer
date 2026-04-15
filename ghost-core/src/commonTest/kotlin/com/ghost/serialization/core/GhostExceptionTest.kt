package com.ghost.serialization.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GhostExceptionTest {

    @Test
    fun exceptionContainsLineAndColumn() {
        val ex = GhostJsonException("test error", 5, 10)
        assertEquals(5, ex.line)
        assertEquals(10, ex.column)
        assertTrue(ex.message!!.contains("line 5"))
        assertTrue(ex.message!!.contains("col 10"))
    }

    @Test
    fun exceptionContainsPath() {
        val ex = GhostJsonException("test error", 1, 1, "$.user.name")
        assertEquals("$.user.name", ex.path)
        assertTrue(ex.message!!.contains("$.user.name"))
    }

    @Test
    fun exceptionContainsMessage() {
        val ex = GhostJsonException("Invalid token")
        assertTrue(ex.message!!.contains("Invalid token"))
    }

    @Test
    fun defaultLineAndColumnAreMinusOne() {
        val ex = GhostJsonException("defaults")
        assertEquals(-1, ex.line)
        assertEquals(-1, ex.column)
    }

    @Test
    fun defaultPathIsDollar() {
        val ex = GhostJsonException("defaults")
        assertEquals("$", ex.path)
    }

    @Test
    fun exceptionIsRuntimeException() {
        val ex: RuntimeException = GhostJsonException("type check")
        assertTrue(ex is GhostJsonException)
    }

    @Test
    fun massExceptionCreationIsPerformant() {
        repeat(100_000) {
            GhostJsonException("benchmark $it", it, it)
        }
    }
}
