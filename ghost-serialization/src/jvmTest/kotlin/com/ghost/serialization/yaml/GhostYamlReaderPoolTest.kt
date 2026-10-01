@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.yaml

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.decodeFromYaml
import com.ghost.serialization.encodeToYaml
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.util.isJvm
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * JVM pool reuse and allocation guards for YAML flat reader/writer.
 * Follows the same pooling assertions as `GhostProtoReaderPoolTest`.
 */
class GhostYamlReaderPoolTest {

    private data class PoolWidget(val id: Int, val tag: String)

    private object PoolWidgetSerializer :
        AbstractGhostSerializer<PoolWidget>(),
        GhostYamlSerializer<PoolWidget> {
        override val typeName: String = "PoolWidget"

        override fun serialize(
            writer: GhostJsonWriter,
            value: PoolWidget
        ) = Unit

        override fun deserialize(reader: GhostJsonReader): PoolWidget =
            PoolWidget(id = 0, tag = "")

        override fun deserialize(reader: GhostJsonStringReader): PoolWidget =
            PoolWidget(id = 0, tag = "")

        override fun serialize(writer: GhostYamlWriter, value: PoolWidget) {
            writer.beginObject()
            writer.name(key = "id").value(value.id)
            writer.name(key = "tag").value(value.tag)
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
            return PoolWidget(id = id, tag = tag)
        }
    }

    init {
        Ghost.addRegistry(
            registry = object : AbstractGhostRegistry() {
                private val map = mapOf<kotlin.reflect.KClass<*>, GhostSerializer<*>>(
                    PoolWidget::class to PoolWidgetSerializer,
                )

                @Suppress("UNCHECKED_CAST")
                override fun <T : Any> getSerializer(clazz: kotlin.reflect.KClass<T>): GhostSerializer<T>? {
                    return map[clazz] as? GhostSerializer<T>
                }

                override fun getAllSerializers(): Map<kotlin.reflect.KClass<*>, GhostSerializer<*>> =
                    map

            },
        )
    }

    @Test
    fun decodeFromYamlStillRoundTripsAfterPooling() {
        val yaml = """
            id: 7
            tag: pooled
        """.trimIndent()
        assertEquals(
            expected = PoolWidget(id = 7, tag = "pooled"),
            actual = Ghost.decodeFromYaml(yaml)
        )
    }

    @Test
    fun resetSliceReusesSameReaderInstanceOnJvm() {
        if (!isJvm) return
        var first: GhostYamlFlatReader? = null
        var second: GhostYamlFlatReader? = null
        val payload = "id: 1\ntag: a".encodeToByteArray()

        ghostYamlInternalUseFlatReader(bytes = payload) { first = it }
        ghostYamlInternalUseFlatReader(bytes = payload) { second = it }

        assertEquals(
            expected = first,
            actual = second,
            message = "ThreadLocal pool should reuse GhostYamlFlatReader"
        )
    }

    @Test
    fun resetReusesSameWriterInstanceOnJvm() {
        if (!isJvm) return
        var first: GhostYamlWriter? = null
        var second: GhostYamlWriter? = null

        ghostYamlInternalUseFlatWriter { writer, _ -> first = writer }
        ghostYamlInternalUseFlatWriter { writer, _ -> second = writer }

        assertEquals(
            expected = first,
            actual = second,
            message = "ThreadLocal pool should reuse GhostYamlWriter"
        )
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
            actual = kbPerOp < 4.0,
            message = "Pooled Ghost.decodeFromYaml should stay under 4 KB/op steady-state; was $kbPerOp KB/op"
        )
    }

    @Test
    fun encodeToYamlReusesWriterWithoutLeakingPriorDocument() {
        val first = Ghost.encodeToYaml(value = PoolWidget(id = 1, tag = "alpha"))
        val second = Ghost.encodeToYaml(value = PoolWidget(id = 2, tag = "beta"))
        assertTrue(actual = first.contains("alpha"))
        assertTrue(actual = second.contains("beta"))
        assertTrue(
            actual = !second.contains("alpha"),
            message = second
        )
    }
}
