package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.IgnoreModel
import com.ghost.serialization.integration.model.UnicodeModel
import com.ghost.serialization.integration.model.NamingModel
import com.ghost.serialization.exception.GhostJsonException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.assertFailsWith

class GhostFeatureTest {

    @Test
    fun testGhostIgnore() {
        val model = IgnoreModel(id = 1, secret = "TOP_SECRET", name = "Juan")
        val json = Ghost.serialize(model)
        
        // Secret should NOT be in JSON
        assertFalse(json.contains("secret"))
        assertFalse(json.contains("TOP_SECRET"))
        assertTrue(json.contains("\"id\":1"))
        assertTrue(json.contains("\"name\":\"Juan\""))
        
        // When deserializing, it should take the default value
        val deserialized = Ghost.deserialize<IgnoreModel>("{\"id\":1,\"secret\":\"HACKED\",\"name\":\"Juan\"}")
        assertEquals("default", deserialized.secret)
    }

    @Test
    fun testUnicodeAndEscapes() {
        val model = UnicodeModel(
            text = "Hello World",
            emoji = "🚀🔥✨",
            escaped = "Line1\nLine2\tTab\"Quote\"\\Backslash"
        )
        
        val json = Ghost.serialize(model)
        val deserialized = Ghost.deserialize<UnicodeModel>(json)
        
        assertEquals(model.text, deserialized.text)
        assertEquals(model.emoji, deserialized.emoji)
        assertEquals(model.escaped, deserialized.escaped)
        
        // Test raw unicode escape in JSON source
        val jsonWithUnicode = "{\"text\":\"\\u0041\\u0042\\u0043\",\"emoji\":\"\\uD83D\\uDE80\",\"escaped\":\"\"}"
        val deserialized2 = Ghost.deserialize<UnicodeModel>(jsonWithUnicode)
        assertEquals("ABC", deserialized2.text)
        assertEquals("🚀", deserialized2.emoji)
    }

    @Test
    fun testMalformedJsonResilience() {
        // Missing colon
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<IgnoreModel>("{\"id\" 1}")
        }

        // Unclosed string
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<IgnoreModel>("{\"name\":\"Juan}")
        }

        // Extra comma (trailing)
        assertFailsWith<GhostJsonException> {
            Ghost.deserialize<IgnoreModel>("{\"id\":1, \"name\":\"Juan\",}")
        }
    }

    @Test
    fun testGhostName() {
        val model = NamingModel(id = 42, name = "Juan", active = true)
        val json = Ghost.serialize(model)
        
        // Should use JSON names
        assertTrue(json.contains("\"user_id\":42"))
        assertTrue(json.contains("\"full_name\":\"Juan\""))
        assertTrue(json.contains("\"is_active\":true"))
        
        // Deserialize back
        val deserialized = Ghost.deserialize<NamingModel>(json)
        assertEquals(model.id, deserialized.id)
        assertEquals(model.name, deserialized.name)
        assertEquals(model.active, deserialized.active)
    }

    @Test
    fun testGhostNameWithExtraFields() {
        // Test that even with renamed fields, we skip unknown ones correctly
        val json = "{\"user_id\":42, \"unknown\": \"garbage\", \"full_name\":\"Juan\", \"is_active\":true}"
        val deserialized = Ghost.deserialize<NamingModel>(json)
        assertEquals(42, deserialized.id)
        assertEquals("Juan", deserialized.name)
    }
}
