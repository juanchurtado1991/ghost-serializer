package com.ghost.serialization

import com.ghost.serialization.core.contract.GhostRegistry
import com.ghost.serialization.core.contract.GhostSerializer
import com.ghost.serialization.core.parser.GhostJsonReader
import com.ghost.serialization.core.writer.GhostJsonWriter
import kotlinx.coroutines.*
import kotlinx.coroutines.test.runTest
import java.util.concurrent.Executors
import kotlin.reflect.KClass
import kotlin.test.Test
import kotlin.test.assertNotNull

class GhostConcurrencyTest {

    private class MockSerializer(val id: Int) : GhostSerializer<String> {
        override fun deserialize(reader: GhostJsonReader): String = ""
        override fun serialize(writer: GhostJsonWriter, value: String) {}
    }

    private class MockRegistry(val startId: Int) : GhostRegistry {
        override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
            @Suppress("UNCHECKED_CAST")
            return MockSerializer(startId) as GhostSerializer<T>
        }
    }

    @Test
    fun testParallelRegistryAccess() = runTest {
        val dispatcher = Executors.newFixedThreadPool(16).asCoroutineDispatcher()
        
        withContext(dispatcher) {
            val jobs = mutableListOf<Job>()
            repeat(100) { i ->
                jobs += launch {
                    if (i % 2 == 0) {
                        Ghost.addRegistry(MockRegistry(i))
                    } else {
                        Ghost.getSerializer(String::class)
                    }
                }
            }
            jobs.joinAll()
        }
        
        assertNotNull(Ghost.getSerializer(String::class))
    }
}
