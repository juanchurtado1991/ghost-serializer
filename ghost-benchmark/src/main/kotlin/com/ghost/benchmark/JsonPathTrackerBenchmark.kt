package com.ghost.benchmark

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.bytes.GhostJsonFlatReader
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.sun.management.ThreadMXBean
import java.lang.management.ManagementFactory

/**
 * Happy-path overhead probe for [com.ghost.serialization.parser.common.GhostJsonPathTracker].
 *
 * Confirms breadcrumbs (push/pop only) do not allocate on successful nested decode and that
 * a fixed nested walk stays in a stable ns/op band. Not a regression gate — Twitter Decode
 * KB/op remains the CI gate; this surfaces tracker-specific regressions during local runs.
 */
@OptIn(InternalGhostApi::class)
object JsonPathTrackerBenchmark {

    private const val LABEL = "JSONPath tracker — nested happy-path walk (alloc + ns/op)"
    private const val JSON =
        """{"user":{"addresses":[{"zip":"10001"},{"zip":"94105"}],"age":42},"tags":["a","b","c"]}"""
    private const val WARMUP = 20_000
    private const val ITERATIONS = 200_000

    fun run() {
        val threadBean = ManagementFactory.getThreadMXBean() as? ThreadMXBean
        if (threadBean == null || !threadBean.isThreadAllocatedMemorySupported) {
            println("  ⚠️  ThreadMXBean not available — skipping path-tracker benchmark.")
            return
        }
        threadBean.isThreadAllocatedMemoryEnabled = true

        val bytes = JSON.encodeToByteArray()
        val userOpts = JsonReaderOptions.of("user", "tags")
        val userInner = JsonReaderOptions.of("addresses", "age")
        val addrOpts = JsonReaderOptions.of("zip")

        fun walk(reader: GhostJsonFlatReader) {
            reader.reset(bytes)
            reader.beginObject()
            while (true) {
                when (reader.selectNameAndConsume(userOpts)) {
                    0 -> {
                        reader.beginObject()
                        while (true) {
                            when (reader.selectNameAndConsume(userInner)) {
                                0 -> {
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        reader.beginObject()
                                        while (true) {
                                            when (reader.selectNameAndConsume(addrOpts)) {
                                                0 -> reader.nextString()
                                                -1 -> break
                                                else -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                    }
                                    reader.endArray()
                                }
                                1 -> reader.nextInt()
                                -1 -> break
                                else -> reader.skipValue()
                            }
                        }
                        reader.endObject()
                    }
                    1 -> {
                        reader.beginArray()
                        while (reader.hasNext()) {
                            reader.nextString()
                        }
                        reader.endArray()
                    }
                    -1 -> break
                    else -> reader.skipValue()
                }
            }
            reader.endObject()
        }

        val reader = GhostJsonFlatReader(bytes)
        repeat(WARMUP) { walk(reader) }

        val threadId = Thread.currentThread().id
        System.gc()
        Thread.sleep(50)

        val allocBefore = threadBean.getThreadAllocatedBytes(threadId)
        val t0 = System.nanoTime()
        repeat(ITERATIONS) { walk(reader) }
        val elapsedNs = System.nanoTime() - t0
        val allocAfter = threadBean.getThreadAllocatedBytes(threadId)
        val allocBytes = (allocAfter - allocBefore).coerceAtLeast(0)
        val nsPerOp = elapsedNs.toDouble() / ITERATIONS
        val bytesPerOp = allocBytes.toDouble() / ITERATIONS

        println("\n── $LABEL")
        println(
            "  iterations=$ITERATIONS  ns/op=${"%.1f".format(nsPerOp)}  " +
                "B/op=${"%.2f".format(bytesPerOp)}  (expect ~0 B/op beyond reader reuse)"
        )
        // Soft signal only: breadcrumbs should not allocate per successful walk.
        if (bytesPerOp > 64.0) {
            println(
                "  ⚠️  Unexpected allocation on happy path (path tracker should be push/pop only)."
            )
        } else {
            println("  ✓ Happy-path allocation looks tracker-clean.")
        }
    }
}
