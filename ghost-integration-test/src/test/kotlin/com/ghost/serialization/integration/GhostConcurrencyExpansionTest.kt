package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.LargeStringModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GhostConcurrencyExpansionTest {

    @Test
    fun testExtremeBufferPoolContention() = runTest {
        val numThreads = 16
        val operationsPerThread = 500
        val dispatcher = Executors.newFixedThreadPool(numThreads).asCoroutineDispatcher()
        
        val errorCount = AtomicInteger(0)
        val successCount = AtomicInteger(0)
        
        // Large string triggers scratch buffer usage in writer and reader slow paths
        val largeString = "🧛".repeat(2000) 
        val model = LargeStringModel(largeString)
        
        withContext(dispatcher) {
            val jobs = (0 until numThreads).map {
                launch {
                    repeat(operationsPerThread) {
                        try {
                            val json = Ghost.serialize(model)
                            val decoded = Ghost.deserialize<LargeStringModel>(json)
                            if (decoded.large == largeString) {
                                successCount.incrementAndGet()
                            } else {
                                errorCount.incrementAndGet()
                            }
                        } catch (e: Throwable) {
                            errorCount.incrementAndGet()
                            // e.printStackTrace()
                        }
                    }
                }
            }
            jobs.joinAll()
        }
        
        (dispatcher.executor as java.util.concurrent.ExecutorService).shutdown()
        assertTrue((dispatcher.executor as java.util.concurrent.ExecutorService).awaitTermination(10, TimeUnit.SECONDS))
        
        assertEquals(0, errorCount.get(), "Concurrency stress test failed with ${errorCount.get()} errors")
        assertEquals(numThreads * operationsPerThread, successCount.get(), "Some operations failed to complete")
    }

    @Test
    fun testConcurrentRegistryAccess() = runTest {
        val numThreads = 8
        val dispatcher = Executors.newFixedThreadPool(numThreads).asCoroutineDispatcher()
        
        withContext(dispatcher) {
            val jobs = (0 until 1000).map { i ->
                launch {
                    Ghost.getSerializer(LargeStringModel::class)
                }
            }
            jobs.joinAll()
        }
        (dispatcher.executor as java.util.concurrent.ExecutorService).shutdown()
        assertTrue((dispatcher.executor as java.util.concurrent.ExecutorService).awaitTermination(5, TimeUnit.SECONDS))
    }
}
