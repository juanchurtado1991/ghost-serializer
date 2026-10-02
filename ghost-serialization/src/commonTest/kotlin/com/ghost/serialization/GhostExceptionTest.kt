package com.ghost.serialization

import com.ghost.serialization.exception.GhostJsonException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GhostExceptionTest {

    @Test
    fun exceptionContainsLineAndColumn() {
        val ex = GhostJsonException("test error", 5, 10)
        assertEquals(
            expected = 5,
            actual = ex.line
        )
        assertEquals(
            expected = 10,
            actual = ex.column
        )
        assertTrue(actual = ex.message.contains("line 5"))
        assertTrue(actual = ex.message.contains("col 10"))
    }

    @Test
    fun exceptionContainsPath() {
        val ex = GhostJsonException("test error", 1, 1, "$.user.name")
        assertEquals(
            expected = "$.user.name",
            actual = ex.path
        )
        assertTrue(actual = ex.message.contains("$.user.name"))
    }

    @Test
    fun exceptionContainsHintWhenProvided() {
        val ex = GhostJsonException(
            message = "Unexpected string for numeric type (coercion disabled)",
            line = 1,
            column = 2,
            path = "$.age",
            hint = "Enable coerceStringsToNumbers",
        )
        assertEquals(
            expected = "Enable coerceStringsToNumbers",
            actual = ex.hint
        )
        assertTrue(actual = ex.message.contains("Hint: Enable coerceStringsToNumbers"))
    }

    @Test
    fun exceptionOmitsHintLineWhenAbsent() {
        val ex = GhostJsonException("Invalid token", 1, 1)
        assertEquals(
            expected = null,
            actual = ex.hint
        )
        assertTrue(actual = !ex.message.contains("Hint:"))
    }

    @Test
    fun exceptionContainsMessage() {
        val ex = GhostJsonException("Invalid token")
        assertTrue(actual = ex.message.contains("Invalid token"))
    }

    @Test
    fun defaultLineAndColumnAreMinusOne() {
        val ex = GhostJsonException("defaults")
        assertEquals(
            expected = -1,
            actual = ex.line
        )
        assertEquals(
            expected = -1,
            actual = ex.column
        )
    }

    @Test
    fun defaultPathIsDollar() {
        val ex = GhostJsonException("defaults")
        assertEquals(
            expected = "$",
            actual = ex.path
        )
    }

    @Test
    fun exceptionIsRuntimeException() {
        val ex: RuntimeException = GhostJsonException("type check")
        assertTrue(actual = ex is GhostJsonException)
    }

    @Test
    fun massExceptionCreationIsPerformant() {
        repeat(100_000) {
            GhostJsonException("benchmark $it", it, it)
        }
    }
}
