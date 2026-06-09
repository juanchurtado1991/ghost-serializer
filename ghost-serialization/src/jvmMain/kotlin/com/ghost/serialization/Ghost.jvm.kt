@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization

import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.WriterSinkPair
import okio.BufferedSource
import java.util.ServiceLoader
import java.util.concurrent.ConcurrentHashMap

private val readerPool = ThreadLocal<GhostJsonReader>()
private val flatReaderPool = ThreadLocal<GhostJsonFlatReader>()
private val stringReaderPool = ThreadLocal<GhostJsonStringReader>()
private val sourceReaderPool = ThreadLocal<GhostJsonReader>()
private val writerPool = ThreadLocal<WriterSinkPair>()

actual fun <T> runSynchronized(lock: Any, block: () -> T): T = synchronized(lock, block)

actual fun <K, V> createAtomicMap(): MutableMap<K, V> = ConcurrentHashMap()

/**
 * Acquires the per-thread [WriterSinkPair], resets it for a fresh encode,
 * and returns it. The pair survives across calls so the underlying
 * [com.ghost.serialization.writer.FlatByteArrayWriter] grows once and stays warm.
 */
private fun acquireFlatWriterPair(): WriterSinkPair {
    val pair = writerPool.get()
        ?: WriterSinkPair()
            .also { writerPool.set(it) }
    
    pair.writer.reset()
    pair.byteWriter.reset()
    return pair
}

private class WriterStringPair {
    val charWriter = com.ghost.serialization.writer.FlatCharArrayWriter()
    val writer = com.ghost.serialization.writer.GhostJsonStringWriter(charWriter)
}

private val stringWriterPool = ThreadLocal<WriterStringPair>()

private fun acquireStringWriterPair(): WriterStringPair {
    val pair = stringWriterPool.get()
        ?: WriterStringPair().also { stringWriterPool.set(it) }
    pair.writer.reset()
    pair.charWriter.reset()
    return pair
}

actual fun ghostInternalEncodeToString(
    block: (com.ghost.serialization.writer.GhostJsonStringWriter) -> Unit
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

actual fun ghostInternalEncodeWithWriter(
    block: (GhostJsonFlatWriter) -> Unit
): ByteArray {
    val pair = acquireFlatWriterPair()
    block(pair.writer)
    val result = pair.byteWriter.toByteArray()
    pair.byteWriter.reset()
    return result
}

actual fun ghostInternalEncodeAndDiscard(
    block: (GhostJsonFlatWriter) -> Unit
) {
    val pair = acquireFlatWriterPair()
    block(pair.writer)
    pair.byteWriter.reset()
}

actual fun ghostInternalEncodeAndDrainTo(
    sink: okio.BufferedSink,
    block: (GhostJsonFlatWriter) -> Unit
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

actual fun discoverRegistries(): Iterable<GhostRegistry> = Iterable {
    object : Iterator<GhostRegistry> {
        private var fastRegistries: MutableList<GhostRegistry>? = null
        private var fastIterator: Iterator<GhostRegistry>? = null
        private var fastChecked = false
        private var slow: Iterator<GhostRegistry>? = null

        override fun hasNext(): Boolean {
            if (!fastChecked) {
                fastChecked = true
                val names = listOf(
                    Ghost.DEFAULT_REGISTRY_NAME,
                    Ghost.TEST_REGISTRY_NAME
                )
                fastRegistries = names.mapNotNull { name ->
                    runCatching {
                        Class.forName(name)
                            .getField(Ghost.INSTANCE_FIELD)
                            .get(null) as GhostRegistry
                    }.getOrNull()
                }.toMutableList()
                fastIterator = fastRegistries?.iterator()
            }
            
            if (fastIterator?.hasNext() == true) return true

            if (slow == null) {
                slow = runCatching {
                    ServiceLoader
                        .load(GhostRegistry::class.java)
                        .iterator()
                }
                    .getOrDefault(
                        emptyList<GhostRegistry>()
                            .iterator()
                    )
            }
            return slow!!.hasNext()
        }

        override fun next(): GhostRegistry {
            if (!hasNext()) throw NoSuchElementException()
            if (fastIterator?.hasNext() == true) return fastIterator!!.next()
            return slow!!.next()
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
    // Separate pool from readerPool to prevent re-entrance corruption if the
    // same thread nests a ByteArray read inside a streaming read.
    val reader = sourceReaderPool.get()
        ?: GhostJsonReader(source)
            .also { sourceReaderPool.set(it) }

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
