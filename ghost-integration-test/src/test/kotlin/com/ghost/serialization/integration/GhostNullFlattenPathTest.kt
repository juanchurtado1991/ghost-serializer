package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.integration.model.FlattenedModel
import com.ghost.serialization.integration.model.NullFlattenPathModel
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * A `null` object on a `@GhostFlatten` path counts as absent: the properties under it keep their defaults
 * and the rest of the document still decodes, on every reader channel.
 */
class GhostNullFlattenPathTest {

    @Test
    fun nullNestedObjectKeepsDefaultsUnderIt() {
        assertDecodedOnEveryChannel(
            json = NULL_COMMAND_MAPPINGS_JSON,
            expected = NullFlattenPathModel(
                id = ID,
                name = NAME,
                driverId = DRIVER_ID
            )
        )
    }

    @Test
    fun nullRootObjectKeepsEveryFlattenedDefault() {
        assertDecodedOnEveryChannel(
            json = NULL_VIRTUAL_JSON,
            expected = NullFlattenPathModel(id = ID)
        )
    }

    @Test
    fun keysAfterNullObjectStillDecode() {
        assertDecodedOnEveryChannel(
            json = NULL_FIRST_JSON,
            expected = NullFlattenPathModel(
                id = ID,
                name = NAME,
                driverId = DRIVER_ID
            )
        )
    }

    @Test
    fun presentObjectsStillDecode() {
        assertDecodedOnEveryChannel(
            json = FULL_JSON,
            expected = NullFlattenPathModel(
                id = ID,
                name = NAME,
                driverId = DRIVER_ID,
                commands = listOf(COMMAND)
            )
        )
    }

    @Test
    fun requiredFieldUnderNullObjectIsStillMissing() {
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<FlattenedModel>(json = NULL_REQUIRED_PARENT_JSON)
        }
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<FlattenedModel>(bytes = NULL_REQUIRED_PARENT_JSON.encodeToByteArray())
        }
        assertFailsWith<GhostJsonException> {
            Ghost.deserializeStreaming<FlattenedModel>(source = Buffer().writeUtf8(NULL_REQUIRED_PARENT_JSON))
        }
    }

    private fun assertDecodedOnEveryChannel(
        json: String,
        expected: NullFlattenPathModel
    ) {
        assertEquals(
            expected = expected,
            actual = Ghost.deserialize<NullFlattenPathModel>(json = json)
        )
        assertEquals(
            expected = expected,
            actual = Ghost.deserialize<NullFlattenPathModel>(bytes = json.encodeToByteArray())
        )
        assertEquals(
            expected = expected,
            actual = Ghost.deserializeStreaming<NullFlattenPathModel>(source = Buffer().writeUtf8(json))
        )
    }

    private companion object {
        const val ID = 1
        const val NAME = "v"
        const val DRIVER_ID = "d-1"
        const val COMMAND = "on"

        const val NULL_COMMAND_MAPPINGS_JSON =
            """{"id":1,"virtual":{"name":"v","commandMappings":null,"driverId":"d-1"}}"""
        const val NULL_VIRTUAL_JSON = """{"virtual":null,"id":1}"""
        const val NULL_FIRST_JSON = """{"virtual":{"commandMappings":null,"name":"v","driverId":"d-1"},"id":1}"""
        const val FULL_JSON =
            """{"id":1,"virtual":{"name":"v","driverId":"d-1","commandMappings":{"commands":["on"]}}}"""
        const val NULL_REQUIRED_PARENT_JSON = """{"id":1,"attributes":{"value":null,"status":"active"}}"""
    }
}
