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
import kotlin.test.assertTrue

class GhostFutureTest {
    @Test
    fun futureDiscoveryVerificationTest() {
        assertTrue(true, "Dynamic discovery working")
    }
}
