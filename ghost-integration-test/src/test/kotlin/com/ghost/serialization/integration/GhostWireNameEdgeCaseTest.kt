package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostWireNameEdgeCaseTest {

    @Test
    fun oddWireNamesRoundTripOnEveryChannel() {
        val model = OddWireNamesModel(
            dashed = "d",
            spaced = 1,
            accented = true,
            lowerId = 2,
            upperId = 3,
            dotted = 4,
            underscored = 5
        )
        val json = Ghost.encodeToString(value = model)

        assertEquals(
            expected = EXPECTED_JSON,
            actual = json
        )
        assertEquals(
            expected = json,
            actual = Ghost.encodeToBytes(value = model).decodeToString()
        )
        listOf(
            Ghost.deserialize<OddWireNamesModel>(json = json),
            Ghost.deserialize<OddWireNamesModel>(bytes = json.encodeToByteArray()),
            Ghost.deserializeStreaming<OddWireNamesModel>(source = Buffer().writeUtf8(string = json))
        ).forEach { decoded ->
            assertEquals(
                expected = model,
                actual = decoded
            )
        }
    }

    companion object {
        private const val EXPECTED_JSON = "{\"renamed-key\":\"d\",\"with space\":1,\"café\":true," +
            "\"id\":2,\"ID\":3,\"a.b\":4,\"a_b\":5}"
    }
}
