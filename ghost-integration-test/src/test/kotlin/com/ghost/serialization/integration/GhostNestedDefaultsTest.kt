package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.NestedDefaultsEvent
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Runtime behavior of serializers whose constructor defaults reference classes nested in the model:
 * absent fields take those defaults, present fields decode normally, on every reader channel.
 */
class GhostNestedDefaultsTest {

    @Test
    fun absentFieldsTakeNestedClassDefaults() {
        assertDecodedOnEveryChannel(
            json = ONLY_ID_JSON,
            expected = NestedDefaultsEvent(id = EVENT_ID)
        )
    }

    @Test
    fun presentFieldsOverrideNestedClassDefaults() {
        assertDecodedOnEveryChannel(
            json = FULL_JSON,
            expected = NestedDefaultsEvent(
                id = EVENT_ID,
                kind = NestedDefaultsEvent.Kind.CREATED,
                lifecycleType = NestedDefaultsEvent.Lifecycle.Type.DELETE,
                ownerId = OWNER_ID,
                locationId = null,
                roomId = ROOM_ID,
                principal = null
            )
        )
    }

    @Test
    fun partialFieldsMixDefaultsAndValues() {
        assertDecodedOnEveryChannel(
            json = LIFECYCLE_ONLY_JSON,
            expected = NestedDefaultsEvent(
                id = EVENT_ID,
                lifecycleType = NestedDefaultsEvent.Lifecycle.Type.CREATE
            )
        )
    }

    @Test
    fun roundTripKeepsEveryValue() {
        val event = NestedDefaultsEvent(
            id = EVENT_ID,
            kind = NestedDefaultsEvent.Kind.CREATED,
            lifecycleType = NestedDefaultsEvent.Lifecycle.Type.DELETE,
            ownerId = OWNER_ID,
            roomId = ROOM_ID
        )

        assertDecodedOnEveryChannel(
            json = Ghost.encodeToString(value = event),
            expected = event
        )
    }

    private fun assertDecodedOnEveryChannel(
        json: String,
        expected: NestedDefaultsEvent
    ) {
        assertEquals(
            expected = expected,
            actual = Ghost.deserialize<NestedDefaultsEvent>(json = json)
        )
        assertEquals(
            expected = expected,
            actual = Ghost.deserialize<NestedDefaultsEvent>(bytes = json.encodeToByteArray())
        )
        assertEquals(
            expected = expected,
            actual = Ghost.deserializeStreaming<NestedDefaultsEvent>(source = Buffer().writeUtf8(json))
        )
    }

    private companion object {
        const val EVENT_ID = "e-1"
        const val OWNER_ID = "o-1"
        const val ROOM_ID = "r-1"

        const val ONLY_ID_JSON = """{"id":"e-1"}"""
        const val LIFECYCLE_ONLY_JSON = """{"id":"e-1","lifecycleType":"CREATE"}"""
        const val FULL_JSON =
            """{"id":"e-1","kind":"CREATED","lifecycleType":"DELETE","ownerId":"o-1","locationId":null,"roomId":"r-1"}"""
    }
}
