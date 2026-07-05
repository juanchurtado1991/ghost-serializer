package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.PageWithNestedDevices
import com.ghost.serialization.integration.model.NestedDeviceStub
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * End-to-end sealed dispatch when nested JSON precedes the discriminator key.
 */
class GhostDiscriminatorAfterNestedIntegrationTest {

    @Test
    fun deserializeSealedSubclassWhenDevicesArrayPrecedesPageType() {
        val json = """
            {
              "devices": [{"id": "hub-1"}, {"id": "sensor-2"}],
              "pageType": "LoggedIn",
              "name": "Living room"
            }
        """.trimIndent()

        val result = Ghost.deserialize<PageWithNestedDevices>(json)

        assertEquals(
            PageWithNestedDevices.LoggedIn(
                devices = listOf(
                    NestedDeviceStub(id = "hub-1"),
                    NestedDeviceStub(id = "sensor-2"),
                ),
                name = "Living room",
            ),
            result,
        )
    }
}
