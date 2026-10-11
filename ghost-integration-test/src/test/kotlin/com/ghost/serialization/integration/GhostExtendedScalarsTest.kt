package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.integration.model.ExtendedScalarsModel
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Top-level scalar edge cases not covered by tri-channel harness. */
class GhostExtendedScalarsTest {

    @Test
    fun generatedByteAndShortFieldsRejectOutOfRangeValues() {
        listOf(BYTE_OUT_OF_RANGE_JSON, SHORT_OUT_OF_RANGE_JSON).forEach { json ->
            assertFailsWith<GhostJsonException> { Ghost.deserialize<ExtendedScalarsModel>(json) }
            assertFailsWith<GhostJsonException> { Ghost.deserialize<ExtendedScalarsModel>(json.encodeToByteArray()) }
            assertFailsWith<GhostJsonException> {
                Ghost.deserializeStreaming<ExtendedScalarsModel>(Buffer().writeUtf8(string = json))
            }
        }
    }

    @Test
    fun topLevelFloatDeserializeFromString() {
        assertEquals(expected = 3.14f, actual = Ghost.deserialize("3.14"), absoluteTolerance = 0.001f)
    }

    companion object {
        private const val BYTE_OUT_OF_RANGE_JSON =
            """{"tags":[],"code":300,"port":1,"letter":"a","ratio":1.0}"""
        private const val SHORT_OUT_OF_RANGE_JSON =
            """{"tags":[],"code":1,"port":70000,"letter":"a","ratio":1.0}"""
    }
}
