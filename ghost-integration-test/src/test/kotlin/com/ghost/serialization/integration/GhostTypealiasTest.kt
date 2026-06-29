package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.integration.model.TypealiasModel
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Regression tests for Ghost 1.2.3:
 * Fields whose declared types are typealiases (Map, List, String aliases)
 * must be recognized and dispatched to the correct serializer.
 */
@OptIn(InternalGhostApi::class)
class GhostTypealiasTest {

    @Test
    fun deserializesModelWithTypealiasMapField() {
        val json = """{"id":"device-1","attributes":{"color":"red","mode":"auto"},"tags":["home","iot"]}"""
        val model = Ghost.deserialize<TypealiasModel>(json)
        assertEquals("device-1", model.id)
        assertEquals(mapOf("color" to "red", "mode" to "auto"), model.attributes)
        assertEquals(listOf("home", "iot"), model.tags)
    }

    @Test
    fun serializesModelWithTypealiasFields() {
        val model = TypealiasModel(
            id = "device-2",
            attributes = mapOf("brightness" to "80"),
            tags = listOf("light", "bedroom")
        )
        val json = Ghost.serialize(model)
        val restored = Ghost.deserialize<TypealiasModel>(json)
        assertEquals(model.id, restored.id)
        assertEquals(model.attributes, restored.attributes)
        assertEquals(model.tags, restored.tags)
    }

    @Test
    fun deserializesEmptyCollectionTypealiasFields() {
        val json = """{"id":"device-3","attributes":{},"tags":[]}"""
        val model = Ghost.deserialize<TypealiasModel>(json)
        assertEquals("device-3", model.id)
        assertEquals(emptyMap<String, String>(), model.attributes)
        assertEquals(emptyList<String>(), model.tags)
    }
}
