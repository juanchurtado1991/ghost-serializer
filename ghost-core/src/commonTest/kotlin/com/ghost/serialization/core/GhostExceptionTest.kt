package com.ghost.serialization.core

import com.ghost.serialization.core.parser.skipCommaIfPresent
import com.ghost.serialization.core.parser.nextNonWhitespace
import com.ghost.serialization.core.parser.skipAnyValue
import com.ghost.serialization.serializers.IntArraySerializer
import com.ghost.serialization.serializers.LongArraySerializer
import com.ghost.serialization.core.contract.GhostRegistry
import com.ghost.serialization.core.contract.GhostSerializer

import com.ghost.serialization.core.parser.GhostJsonReader
import com.ghost.serialization.core.writer.GhostJsonWriter
import com.ghost.serialization.core.exception.GhostJsonException
import com.ghost.serialization.core.parser.nextKey
import com.ghost.serialization.core.parser.consumeKeySeparator
import com.ghost.serialization.core.parser.isNextNullValue
import com.ghost.serialization.core.parser.skipValue
import com.ghost.serialization.core.parser.JsonToken
import com.ghost.serialization.core.parser.peekJsonToken
import com.ghost.serialization.core.parser.readList
import com.ghost.serialization.core.parser.nextInt
import com.ghost.serialization.core.parser.nextDouble
import com.ghost.serialization.core.parser.consumeArraySeparator
import com.ghost.serialization.core.parser.nextLong
import com.ghost.serialization.core.parser.nextFloat
import com.ghost.serialization.core.parser.consumeNull

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
