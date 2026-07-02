package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import kotlin.test.Test
import kotlin.test.assertEquals

/** Top-level scalar edge cases not covered by tri-channel harness. */
class GhostExtendedScalarsTest {

    @Test
    fun topLevelFloatDeserializeFromString() {
        assertEquals(3.14f, Ghost.deserialize("3.14"), 0.001f)
    }
}
