@file:OptIn(InternalGhostApi::class)
@file:JvmName("Ghost_jvmKt")

package com.ghost.serialization

import android.annotation.SuppressLint
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.WriterSinkPair
import com.ghost.serialization.writer.FlatCharArrayWriter
import com.ghost.serialization.writer.GhostJsonStringWriter
import okio.BufferedSource
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

@PublishedApi
internal val writerPool = ThreadLocal<WriterSinkPair>()
private val readerPool = ThreadLocal<GhostJsonReader>()
private val flatReaderPool = ThreadLocal<GhostJsonFlatReader>()
private val stringReaderPool = ThreadLocal<GhostJsonStringReader>()
private val sourceReaderPool = ThreadLocal<GhostJsonReader>()

actual fun <T> runSynchronized(lock: Any, block: () -> T): T = synchronized(lock, block)

actual fun <K, V> createAtomicMap(): MutableMap<K, V> = ConcurrentHashMap()

@SuppressLint("NewApi")
actual fun discoverRegistries(): Iterable<GhostRegistry> = Iterable {
    object : Iterator<GhostRegistry> {
        private val fast = mutableListOf<GhostRegistry>()
        private var fastLoaded = false
        private var index = 0
        private var slow: Iterator<GhostRegistry>? = null

        override fun hasNext(): Boolean {
            if (!fastLoaded) {
                fastLoaded = true
                loadFastRegistries(fast)
            }
            if (index < fast.size) {
                return true
            }
            if (slow == null) {
                slow = runCatching {
                    ServiceLoader.load(GhostRegistry::class.java).iterator()
                }
                    .getOrDefault(emptyList<GhostRegistry>().iterator())
            }
            return slow!!.hasNext()
        }

        override fun next(): GhostRegistry {
            if (!hasNext()) {
                throw NoSuchElementException()
            }
            return if (index < fast.size) {
                fast[index++]
            } else {
                slow!!.next()
            }
        }
    }
}

private fun loadFastRegistries(
    out: MutableList<GhostRegistry>
) {
    listOf(
        Ghost.DEFAULT_REGISTRY_NAME,
        Ghost.ANDROID_REGISTRY_NAME
    ).forEach { name ->
        runCatching {
            val clazz = Class.forName(name)
            val field = runCatching { clazz.getField(Ghost.INSTANCE_FIELD) }
                .getOrNull()
                ?: clazz.getDeclaredField(Ghost.INSTANCE_FIELD)

            (field.get(null) as? GhostRegistry)?.let { out.add(it) }
        }
    }
}

actual fun <T> ghostInternalUseReader(
    bytes: ByteArray,
    block: (GhostJsonReader) -> T
): T {
    val reader = readerPool.get()
        ?: GhostJsonReader(bytes)
            .also { readerPool.set(it) }

    reader.reset(bytes)
    return block(reader)
}

actual fun <T> ghostInternalUseFlatReader(
    bytes: ByteArray,
    limit: Int,
    block: (GhostJsonFlatReader) -> T
): T {
    val reader = flatReaderPool.get()
        ?: GhostJsonFlatReader(bytes)
            .also { flatReaderPool.set(it) }

    reader.reset(bytes, limit)
    return block(reader)
}

actual fun <T> ghostInternalUseSource(
    source: BufferedSource,
    block: (GhostJsonReader) -> T
): T {
    // Separate pool from readerPool to prevent re-entrancy corruption if the
    // same thread nests a ByteArray read inside a streaming read.
    val reader = sourceReaderPool.get()
        ?: GhostJsonReader(source)
            .also { sourceReaderPool.set(it) }

    // reset(BufferedSource) wraps source in a StreamingGhostSource — Okio pulls
    // data in 8 KB segments on demand instead of loading the entire payload.
    reader.reset(source)
    return block(reader)
}

actual fun <T> ghostInternalUseStringReader(
    json: String,
    block: (GhostJsonStringReader) -> T
): T {
    val reader = stringReaderPool.get()
        ?: GhostJsonStringReader(json)
            .also { stringReaderPool.set(it) }

    reader.reset(json)
    return block(reader)
}

@PublishedApi
internal fun acquireFlatWriterPair(): WriterSinkPair {
    val pair = writerPool.get()
        ?: WriterSinkPair()
            .also { writerPool.set(it) }

    pair.writer.reset()
    pair.byteWriter.reset()
    return pair
}

@PublishedApi
internal class WriterStringPair {
    val charWriter = FlatCharArrayWriter()
    val writer = GhostJsonStringWriter(charWriter)
}

@PublishedApi
internal val stringWriterPool = ThreadLocal<WriterStringPair>()

@PublishedApi
internal fun acquireStringWriterPair(): WriterStringPair {
    val pair = stringWriterPool.get()
        ?: WriterStringPair().also { stringWriterPool.set(it) }
    pair.writer.reset()
    pair.charWriter.reset()
    return pair
}

@PublishedApi
actual internal inline fun ghostInternalEncodeToString(
    crossinline block: (GhostJsonStringWriter) -> Unit
): String {
    val pair = acquireStringWriterPair()
    block(pair.writer)
    val result = String(
        pair.charWriter.array,
        0,
        pair.charWriter.size
    )
    pair.charWriter.reset()
    return result
}

@PublishedApi
actual internal inline fun ghostInternalEncodeWithWriter(
    crossinline block: (GhostJsonFlatWriter) -> Unit
): ByteArray {
    val pair = acquireFlatWriterPair()
    block(pair.writer)
    val result = pair.byteWriter.toByteArray()
    pair.byteWriter.reset()
    return result
}

@PublishedApi
actual internal inline fun ghostInternalEncodeAndDiscard(
    crossinline block: (GhostJsonFlatWriter) -> Unit
) {
    val pair = acquireFlatWriterPair()
    block(pair.writer)
    pair.byteWriter.reset()
}

@PublishedApi
actual internal inline fun ghostInternalEncodeAndDrainTo(
    sink: okio.BufferedSink,
    crossinline block: (GhostJsonFlatWriter) -> Unit
) {
    val pair = acquireFlatWriterPair()
    block(pair.writer)
    sink.write(
        pair.byteWriter.array,
        0,
        pair.byteWriter.size
    )
    pair.byteWriter.reset()
}
