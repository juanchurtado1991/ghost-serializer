package compat

import com.ghost.serialization.Ghost
import com.ghost.serialization.generated.GhostModuleRegistry_compat
import kotlin.test.Test
import kotlin.test.assertEquals

class RoundTripTest {

    @Test
    fun roundTripsThroughGhost() {
        Ghost.addRegistry(registry = GhostModuleRegistry_compat.INSTANCE)
        val device = Device(id = 42L, name = "sensor", tags = listOf("a", "b"), online = true)

        val json = Ghost.encodeToString(value = device)

        assertEquals(expected = device, actual = Ghost.deserialize<Device>(json = json))
    }
}
