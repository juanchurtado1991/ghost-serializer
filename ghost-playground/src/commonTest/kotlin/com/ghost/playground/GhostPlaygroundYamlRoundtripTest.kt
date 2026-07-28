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
        Ghost.addRegistry(GhostModuleRegistry_playground.INSTANCE)
    }

    @Test
    fun playgroundUserRoundTripsYaml() {
        val yaml = """
            id: 42
            name: Ghost
            email: playground@ghost.io
        """.trimIndent()

        val user = Ghost.decodeFromYaml<PlaygroundUser>(yaml)
        assertEquals(42L, user.id)
        assertEquals("Ghost", user.name)
        assertEquals("playground@ghost.io", user.email)

        val encoded = Ghost.encodeToYaml(user)
        val restored = Ghost.decodeFromYaml<PlaygroundUser>(encoded)
        assertEquals(user, restored)
        assertTrue("name:" in encoded)
    }
}
