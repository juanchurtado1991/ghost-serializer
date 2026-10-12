@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.UserId
import com.ghost.serialization.integration.model.UserWithValueClass
import okio.Buffer
import kotlin.test.Test
import kotlin.test.assertEquals

class GhostValueClassTest {

    @Test
    fun testValueClassDeserialization() {
        val json = """{"id": 123, "name": "Ghost User"}"""
        val result = Ghost.deserialize<UserWithValueClass>(json.encodeToByteArray())

        assertEquals(expected = UserId(value = 123), actual = result.id)
        assertEquals(expected = "Ghost User", actual = result.name)
    }

    @Test
    fun testDirectValueClassDeserialization() {
        val json = "456"
        val result = Ghost.deserialize<UserId>(json.encodeToByteArray())
        assertEquals(expected = UserId(value = 456), actual = result)
    }

    @Test
    fun valueClassWrappingNullableRoundTripsOnEveryChannel() {
        val holder = NullableNoteHolder(
            note = NullableNote(value = null),
            notes = listOf(NullableNote(value = "n"), NullableNote(value = null))
        )
        val json = Ghost.encodeToString(value = holder)

        assertEquals(
            expected = "{\"note\":null,\"notes\":[\"n\",null]}",
            actual = json
        )
        listOf(
            Ghost.deserialize<NullableNoteHolder>(json = json),
            Ghost.deserialize<NullableNoteHolder>(bytes = json.encodeToByteArray()),
            Ghost.deserializeStreaming<NullableNoteHolder>(source = Buffer().writeUtf8(string = json))
        ).forEach { decoded ->
            assertEquals(
                expected = holder,
                actual = decoded
            )
        }
        assertEquals(
            expected = "null",
            actual = Ghost.encodeToString(value = NullableNote(value = null))
        )
    }
}
