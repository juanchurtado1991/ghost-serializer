package com.ghost.playground

import com.ghost.playground.features.PlaygroundUser
import com.ghost.serialization.Ghost
import com.ghost.serialization.decodeFromYaml
import com.ghost.serialization.encodeToYaml
import com.ghost.serialization.generated.GhostModuleRegistry_playground
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GhostPlaygroundYamlRoundtripTest {

    @BeforeTest
    fun registerModule() {
        Ghost.addRegistry(registry = GhostModuleRegistry_playground.INSTANCE)
    }

    @Test
    fun playgroundUserRoundTripsYaml() {
        val yaml = """
            id: 42
            name: Ghost
            email: playground@ghost.io
        """.trimIndent()

        val user = Ghost.decodeFromYaml<PlaygroundUser>(yaml)
        assertEquals(expected = 42L, actual = user.id)
        assertEquals(expected = "Ghost", actual = user.name)
        assertEquals(expected = "playground@ghost.io", actual = user.email)

        val encoded = Ghost.encodeToYaml(value = user)
        val restored = Ghost.decodeFromYaml<PlaygroundUser>(encoded)
        assertEquals(expected = user, actual = restored)
        assertTrue(actual = "name:" in encoded)
    }
}
