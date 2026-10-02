package compat

import com.ghost.serialization.Ghost
import compat.models.Sensor
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * No `Ghost.addRegistry` anywhere: JVM/Android resolve serializers through ServiceLoader, and
 * Kotlin/Native and Kotlin/Wasm through the link the Ghost Gradle plugin's compiler plugin adds.
 */
class RoundTripTest {

    @Test
    fun roundTripsModelFromAnotherModule() {
        val sensor = Sensor(id = "s-1", celsius = 21.5, labels = listOf("kitchen"))

        val json = Ghost.encodeToString(value = sensor)

        assertEquals(expected = sensor, actual = Ghost.deserialize<Sensor>(json = json))
    }

    @Test
    fun roundTripsModelFromSameModule() {
        val device = Device(id = 42L, name = "sensor", tags = listOf("a", "b"), online = true)

        val json = Ghost.encodeToString(value = device)

        assertEquals(expected = device, actual = Ghost.deserialize<Device>(json = json))
    }
}
