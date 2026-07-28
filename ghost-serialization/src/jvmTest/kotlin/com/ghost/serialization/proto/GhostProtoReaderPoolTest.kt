@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.proto.GhostProtoJsonFlatReader
import com.ghost.serialization.proto.wkt.ProtoDuration
import com.ghost.serialization.proto.wkt.ProtoDurationSerializer
import com.ghost.serialization.util.isJvm
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GhostProtoReaderPoolTest {

    init {
        Ghost.addRegistry(
            object : GhostRegistry {
                private val map =
                    mapOf<kotlin.reflect.KClass<*>, GhostSerializer<*>>(
                        ProtoDuration::class to ProtoDurationSerializer,
                    )

                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> getSerializer(clazz: kotlin.reflect.KClass<T>): GhostSerializer<T>? {
                    return map[clazz] as? GhostSerializer<T>
                }

                override fun getAllSerializers(): Map<kotlin.reflect.KClass<*>, GhostSerializer<*>> = map
            },
        )
    }

    @Test
    fun deserializeBytesStillRoundTripsAfterPooling() {
        val json = "\"-123.450000000s\""
        val parsed = GhostProto.deserialize<ProtoDuration>(json.encodeToByteArray())
        assertEquals(-123L, parsed.seconds)
        assertEquals(-450_000_000, parsed.nanos)
    }

    @Test
    fun steadyStateDeserializeAllocationIsLowOnJvm() {
        if (!isJvm) return
        val threadBean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
            ?: return
        if (!threadBean.isThreadAllocatedMemorySupported) return
        threadBean.isThreadAllocatedMemoryEnabled = true

        val json = "\"10.5s\"".encodeToByteArray()
        repeat(500) { GhostProto.deserialize<ProtoDuration>(json) }

        val threadId = Thread.currentThread().id
        val before = threadBean.getThreadAllocatedBytes(threadId)
        repeat(1_000) { GhostProto.deserialize<ProtoDuration>(json) }
        val after = threadBean.getThreadAllocatedBytes(threadId)
        val kbPerOp = (after - before).toDouble() / 1_000.0 / 1024.0

        assertTrue(
            kbPerOp < 4.0,
            "Pooled GhostProto.deserialize should stay under 4 KB/op steady-state; was $kbPerOp KB/op",
        )
    }

    @Test
    fun resetSliceReusesSameReaderInstanceOnJvm() {
        if (!isJvm) return
        var first: GhostProtoJsonFlatReader? = null
        var second: GhostProtoJsonFlatReader? = null
        val payload = "\"1s\"".encodeToByteArray()

        ghostProtoInternalUseFlatReader(payload) { first = it }
        ghostProtoInternalUseFlatReader(payload) { second = it }

        assertEquals(first, second, "ThreadLocal pool should reuse GhostProtoJsonFlatReader")
    }
}
