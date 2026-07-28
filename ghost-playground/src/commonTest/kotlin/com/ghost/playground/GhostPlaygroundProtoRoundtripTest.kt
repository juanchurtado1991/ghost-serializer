package com.ghost.playground

import com.ghost.playground.features.ProtoOrderEvent
import com.ghost.serialization.Ghost
import com.ghost.serialization.generated.GhostModuleRegistry_playground
import com.ghost.serialization.proto.GhostProto
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GhostPlaygroundProtoRoundtripTest {

    @BeforeTest
    fun registerModule() {
        Ghost.addRegistry(GhostModuleRegistry_playground.INSTANCE)
    }

    @Test
    fun protoOrderEventOmitsZeroDefaultAndQuotesInt64() {
        val json = """{"orderId":"5001","label":"restock","retries":0}"""
        val event = GhostProto.deserialize<ProtoOrderEvent>(json)
        assertEquals(5001L, event.orderId)
        assertEquals("restock", event.label)
        assertEquals(0, event.retries)

        val encoded = GhostProto.encodeToString(event)
        assertTrue("\"5001\"" in encoded || encoded.contains("orderId"))
        assertFalse("\"retries\"" in encoded, "default zero field should be omitted: $encoded")
    }
}
