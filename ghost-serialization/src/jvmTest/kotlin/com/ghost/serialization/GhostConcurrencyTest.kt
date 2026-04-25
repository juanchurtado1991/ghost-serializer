package com.ghost.serialization

import com.ghost.serialization.core.contract.GhostRegistry
import com.ghost.serialization.core.contract.GhostSerializer
import com.ghost.serialization.core.parser.GhostJsonReader
import com.ghost.serialization.core.writer.GhostJsonWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GhostConcurrencyTest {

    private class ThreadSafeMockSerializer(val id: Int) : GhostSerializer<Int> {
        override val typeName: String = "Int_$id"
        override fun deserialize(reader: GhostJsonReader): Int = id
        override fun serialize(writer: GhostJsonWriter, value: Int) {
            writer.value(value.toLong())
        }
    }

    private class ThreadSafeMockRegistry(val batchId: Int) : GhostRegistry {
        override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
            if (clazz == Int::class) {
                @Suppress("UNCHECKED_CAST")
                return ThreadSafeMockSerializer(batchId) as GhostSerializer<T>
            }
            return null
        }
        override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> {
            return mapOf(Int::class to ThreadSafeMockSerializer(batchId))
        }
    }

    @Test
    fun testHighContentionRegistryOperations() = runTest {
        val numCoroutines = 2000
        val numThreads = Runtime.getRuntime().availableProcessors() * 2
        val dispatcher = Executors.newFixedThreadPool(numThreads).asCoroutineDispatcher()
        
        val successCount = AtomicInteger(0)
        val errorCount = AtomicInteger(0)

        withContext(dispatcher) {
            val jobs = (0 until numCoroutines).map { i ->
                launch {
                    try {
                        // Mix of reads and writes to stress the internal lock
                        if (i % 5 == 0) {
                            Ghost.addRegistry(ThreadSafeMockRegistry(i))
                        } else {
                            val serializer = Ghost.getSerializer(Int::class)
                            if (serializer != null) {
                                successCount.incrementAndGet()
                            }
                        }
                        
                        // Concurrent serialization/deserialization calls
                        val json = "123"
                        val result = Ghost.deserialize<Int>(json)
                        if (result == 123) {
                            successCount.incrementAndGet()
                        }
                    } catch (e: Throwable) {
                        errorCount.incrementAndGet()
                        e.printStackTrace()
                    }
                }
            }
            jobs.joinAll()
        }

        (dispatcher.executor as java.util.concurrent.ExecutorService).shutdown()
        assertTrue((dispatcher.executor as java.util.concurrent.ExecutorService).awaitTermination(5, TimeUnit.SECONDS))

        assertEquals(0, errorCount.get(), "Concurrency test failed with ${errorCount.get()} errors")
        assertTrue(successCount.get() > 0, "No successful operations recorded")
    }

    @Test
    fun testPrewarmConcurrency() = runTest {
        val dispatcher = Dispatchers.Default
        val jobs = (0 until 100).map {
            CoroutineScope(dispatcher).launch {
                Ghost.prewarm()
            }
        }
        jobs.joinAll()
    }
}
