package com.ghostserializer

import com.ghostserializer.core.contract.GhostRegistry
import com.ghostserializer.core.parser.GhostJsonReader

/**
 * Android/JVM Implementation of Ghost Registry Discovery.
 * Superiority: Zero-config automatic discovery.
 */
actual fun discoverRegistries(): List<GhostRegistry> {
    // 1. Try ServiceLoader (High Performance Industrial Standard)
    try {
        val discovered = java.util.ServiceLoader.load(GhostRegistry::class.java).toList()
        if (discovered.isNotEmpty()) return discovered
    } catch (e: Exception) {
        // Fallback to reflection if ServiceLoader fails on older Android versions
    }

    // 2. Reflective Fallback for complex KMP environments or R8 issues
    val registries = mutableListOf<GhostRegistry>()
    val patterns = listOf(
        "com.ghostserializer.generated.GhostModuleRegistry_Default",
        "com.ghostserializer.generated.GhostModuleRegistry_com.ghostserializer_sample_domain",
        "com.ghostserializer.generated.GhostModuleRegistry_com_ghost_integration"
    )

    patterns.forEach { className ->
        try {
            val clazz = Class.forName(className)
            val registry = try {
                // Try direct Singleton (object) behavior first
                clazz.getDeclaredField("INSTANCE").get(null) as? GhostRegistry
            } catch (_: Exception) {
                // Fallback to Companion object behavior
                try {
                    val companion = clazz.getDeclaredField("Companion").get(null)
                    companion.javaClass.getDeclaredField("INSTANCE").get(companion) as? GhostRegistry
                } catch (_: Exception) {
                    null
                }
            }
            if (registry != null) registries.add(registry)
        } catch (_: Exception) { }
    }
    return registries
}

@PublishedApi
internal class GhostReaderStorage {
    val reader: GhostJsonReader = GhostJsonReader(byteArrayOf())
    val buffer: ByteArray = ByteArray(512 * 1024) // 512KB Recycled Buffer
}

private val storageThreadLocal = ThreadLocal<GhostReaderStorage>()

actual fun <T> __ghost_internal_use_reader__(
    bytes: ByteArray,
    block: (GhostJsonReader) -> T
): T {
    var storage = storageThreadLocal.get()
    if (storage == null) {
        storage = GhostReaderStorage()
        storageThreadLocal.set(storage)
    }
    
    val reader = storage.reader
    reader.reset(bytes)
    
    try {
        return block(reader)
    } finally {
        reader.clear()
    }
}

actual fun <T> __ghost_internal_use_source__(
    source: okio.BufferedSource,
    block: (GhostJsonReader) -> T
): T {
    var storage = storageThreadLocal.get()
    if (storage == null) {
        storage = GhostReaderStorage()
        storageThreadLocal.set(storage)
    }

    val buffer = storage.buffer
    val reader = storage.reader

    // Zero-Allocation Load: Read directly into the recycled buffer
    val readCount = source.read(buffer)
    
    // Check if the source is fully consumed within the 512KB buffer
    if (source.exhausted()) {
        val limit = if (readCount == -1) 0 else readCount
        reader.reset(buffer, limit)
        try {
            return block(reader)
        } finally {
            reader.clear()
        }
    }
    
    // Industrial Fallback: Payload exceeds 512KB, fallback to readByteArray
    val remainingBytes = source.readByteArray()
    val fullBytes = if (readCount == -1) remainingBytes else {
        val initialPart = buffer.copyOfRange(0, readCount)
        initialPart + remainingBytes
    }
    
    reader.reset(fullBytes)
    try {
        return block(reader)
    } finally {
        reader.clear()
    }
}
