package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.integration.model.IgnoreModel
import com.ghost.serialization.integration.model.NamingModel
import com.ghost.serialization.integration.model.UniCodeModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GhostFeatureTest {

    @Test
    fun testGhostIgnore() {
        val model = IgnoreModel(id = 1, secret = "TOP_SECRET", name = "Juan")
        val json = Ghost.serialize(model)

        assertFalse(actual = json.contains("secret"))
        assertFalse(actual = json.contains("TOP_SECRET"))
        assertTrue(actual = json.contains("\"id\":1"))
        assertTrue(actual = json.contains("\"name\":\"Juan\""))

        val deserialized =
            Ghost.deserialize<IgnoreModel>("{\"id\":1,\"secret\":\"HACKED\",\"name\":\"Juan\"}")
        assertEquals(expected = "default", actual = deserialized.secret)
    }

    @Test
    fun testUnicodeAndEscapes() {
        val model = UniCodeModel(
            text = "Hello World",
            emoji = "🚀🔥✨",
            escaped = "Line1\nLine2\tTab\"Quote\"\\Backslash"
        )

        val json = Ghost.serialize(model)
        val deserialized = Ghost.deserialize<UniCodeModel>(json)

        assertEquals(expected = model.text, actual = deserialized.text)
        assertEquals(expected = model.emoji, actual = deserialized.emoji)
        assertEquals(expected = model.escaped, actual = deserialized.escaped)

        val jsonWithUnicode =
            "{\"text\":\"\\u0041\\u0042\\u0043\",\"emoji\":\"\\uD83D\\uDE80\",\"escaped\":\"\"}"
        val deserialized2 = Ghost.deserialize<UniCodeModel>(jsonWithUnicode)
        assertEquals(expected = "ABC", actual = deserialized2.text)
        assertEquals(expected = "🚀", actual = deserialized2.emoji)
    }

    @Test
    fun testMalformedJsonResilience() {
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<IgnoreModel>("{\"id\" 1}")
        }

        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<IgnoreModel>("{\"name\":\"Juan}")
        }

        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<IgnoreModel>("{\"id\":1, \"name\":\"Juan\",}")
        }
    }

    @Test
    fun testGhostName() {
        val model = NamingModel(id = 42, name = "Juan", active = true)
        val json = Ghost.serialize(model)

        assertTrue(actual = json.contains("\"user_id\":42"))
        assertTrue(actual = json.contains("\"full_name\":\"Juan\""))
        assertTrue(actual = json.contains("\"is_active\":true"))

        val deserialized = Ghost.deserialize<NamingModel>(json)
        assertEquals(expected = model.id, actual = deserialized.id)
        assertEquals(expected = model.name, actual = deserialized.name)
        assertEquals(expected = model.active, actual = deserialized.active)
    }

    @Test
    fun testGhostNameWithExtraFields() {
        val json =
            "{\"user_id\":42, \"unknown\": \"garbage\", \"full_name\":\"Juan\", \"is_active\":true}"
        val deserialized = Ghost.deserialize<NamingModel>(json)
        assertEquals(expected = 42, actual = deserialized.id)
        assertEquals(expected = "Juan", actual = deserialized.name)
    }
}
