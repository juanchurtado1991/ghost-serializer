package com.ghost.serialization

import com.ghost.serialization.contract.AbstractGhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.serializers.StringSerializer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass
import kotlin.reflect.typeOf
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Registering and resolving serializers from many threads at once must neither throw nor lose a serializer. */
class GhostRegistryConcurrencyTest {

    private class Model0
    private class Model1
    private class Model2
    private class Model3
    private class Model4
    private class Model5
    private class Model6
    private class Model7

    private val models: List<KClass<*>> = listOf(
        Model0::class, Model1::class, Model2::class, Model3::class,
        Model4::class, Model5::class, Model6::class, Model7::class
    )

    private fun registryFor(owned: KClass<*>) = object : AbstractGhostRegistry() {
        override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> = mapOf(owned to StringSerializer)

        @Suppress("UNCHECKED_CAST")
        override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
            if (clazz == owned) StringSerializer as GhostSerializer<T> else null
    }

    @Test
    fun concurrentAddRegistryAndLookupsDoNotThrow() {
        Ghost.resetForTest()
        val threads = 8
        val iterations = 2_000
        val pool = Executors.newFixedThreadPool(threads)
        val start = CountDownLatch(1)
        val failures = CopyOnWriteArrayList<Throwable>()
        repeat(threads) { worker ->
            pool.execute {
                try {
                    start.await()
                    repeat(iterations) { i ->
                        when ((worker + i) % 5) {
                            0 -> Ghost.addRegistry(registry = registryFor(models[i % models.size]))
                            1 -> Ghost.getSerializer(clazz = Unregistered::class)
                            2 -> Ghost.getSerializer(type = typeOf<List<String>>())
                            3 -> Ghost.getSerializer(clazz = models[i % models.size])
                            else -> Ghost.getAllSerializers()
                        }
                    }
                } catch (t: Throwable) {
                    failures.add(t)
                }
            }
        }
        start.countDown()
        pool.shutdown()
        assertTrue(actual = pool.awaitTermination(60, TimeUnit.SECONDS))
        assertEquals(expected = emptyList(), actual = failures.map { it.toString() })
        models.forEach { model ->
            assertTrue(actual = Ghost.getSerializer(clazz = model) != null, message = "$model lost")
        }
    }

    private class Unregistered
}
