@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.yaml

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.decodeFromYaml
import com.ghost.serialization.encodeToYaml
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.util.isJvm
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.strings.beginObject

/**
 * JVM pool reuse and allocation guards for YAML flat reader/writer.
 * Follows the same pooling assertions as [com.ghost.serialization.proto.GhostProtoReaderPoolTest].
 */
class GhostYamlReaderPoolTest {

    private data class PoolWidget(val id: Int, val tag: String)

    private object PoolWidgetSerializer :
        GhostSerializer<PoolWidget>,
        GhostYamlSerializer<PoolWidget> {
        override val typeName: String = "PoolWidget"

        override fun serialize(writer: com.ghost.serialization.writer.bytes.GhostJsonWriter, value: PoolWidget) = Unit
        override fun serialize(writer: com.ghost.serialization.writer.bytes.GhostJsonFlatWriter, value: PoolWidget) = Unit
        override fun deserialize(reader: com.ghost.serialization.parser.streaming.GhostJsonReader): PoolWidget =
            PoolWidget(0, "")
        override fun deserialize(reader: com.ghost.serialization.parser.bytes.GhostJsonFlatReader): PoolWidget =
            PoolWidget(0, "")
        override fun deserialize(reader: com.ghost.serialization.parser.strings.GhostJsonStringReader): PoolWidget =
            PoolWidget(0, "")

        override fun serialize(writer: com.ghost.serialization.writer.yaml.GhostYamlWriter, value: PoolWidget) = Unit

        override fun serialize(writer: GhostYamlFlatWriter, value: PoolWidget) {
            writer.beginObject()
            writer.name("id").value(value.id)
            writer.name("tag").value(value.tag)
            writer.endObject()
        }

        override fun deserialize(reader: GhostYamlFlatReader): PoolWidget {
            var id = 0
            var tag = ""
            reader.beginObject()
            while (reader.hasNext()) {
                when (reader.nextKey()) {
                    "id" -> id = reader.nextInt()
                    "tag" -> tag = reader.nextString()
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
            return PoolWidget(id, tag)
        }
    }

    init {
        Ghost.addRegistry(
            object : GhostRegistry {
                private val map = mapOf<kotlin.reflect.KClass<*>, GhostSerializer<*>>(
                    PoolWidget::class to PoolWidgetSerializer,
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
    fun decodeFromYamlStillRoundTripsAfterPooling() {
        val yaml = """
            id: 7
            tag: pooled
        """.trimIndent()
        assertEquals(PoolWidget(7, "pooled"), Ghost.decodeFromYaml(yaml))
    }

    @Test
    fun resetSliceReusesSameReaderInstanceOnJvm() {
        if (!isJvm) return
        var first: GhostYamlFlatReader? = null
        var second: GhostYamlFlatReader? = null
        val payload = "id: 1\ntag: a".encodeToByteArray()

        ghostYamlInternalUseFlatReader(payload) { first = it }
        ghostYamlInternalUseFlatReader(payload) { second = it }

        assertEquals(first, second, "ThreadLocal pool should reuse GhostYamlFlatReader")
    }

    @Test
    fun resetReusesSameWriterInstanceOnJvm() {
        if (!isJvm) return
        var first: GhostYamlFlatWriter? = null
        var second: GhostYamlFlatWriter? = null

        ghostYamlInternalUseFlatWriter { first = it }
        ghostYamlInternalUseFlatWriter { second = it }

        assertEquals(first, second, "ThreadLocal pool should reuse GhostYamlFlatWriter")
    }

    @Test
    fun steadyStateDecodeAllocationIsLowOnJvm() {
        if (!isJvm) return
        val threadBean = ManagementFactory.getThreadMXBean() as? ThreadMXBean ?: return
        if (!threadBean.isThreadAllocatedMemorySupported) return
        threadBean.isThreadAllocatedMemoryEnabled = true

        val yaml = """
            id: 1
            tag: warm
        """.trimIndent()
        repeat(500) { Ghost.decodeFromYaml<PoolWidget>(yaml) }

        val threadId = Thread.currentThread().id
        val before = threadBean.getThreadAllocatedBytes(threadId)
        repeat(1_000) { Ghost.decodeFromYaml<PoolWidget>(yaml) }
        val after = threadBean.getThreadAllocatedBytes(threadId)
        val kbPerOp = (after - before).toDouble() / 1_000.0 / 1024.0

        assertTrue(
            kbPerOp < 4.0,
            "Pooled Ghost.decodeFromYaml should stay under 4 KB/op steady-state; was $kbPerOp KB/op",
        )
    }

    @Test
    fun encodeToYamlReusesWriterWithoutLeakingPriorDocument() {
        val first = Ghost.encodeToYaml(PoolWidget(1, "alpha"))
        val second = Ghost.encodeToYaml(PoolWidget(2, "beta"))
        assertTrue(first.contains("alpha"))
        assertTrue(second.contains("beta"))
        assertTrue(!second.contains("alpha"), second)
    }
}
