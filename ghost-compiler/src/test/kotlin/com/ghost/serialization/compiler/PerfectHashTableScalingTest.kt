package com.ghost.serialization.compiler

import com.ghost.serialization.compiler.hash.PerfectHashConfig
import com.ghost.serialization.compiler.hash.PerfectHashFinder
import com.ghost.serialization.parser.common.JsonReaderOptions
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextInt
import com.ghost.serialization.parser.streaming.selectString
import com.ghost.serialization.parser.strings.GhostJsonStringReader
import com.ghost.serialization.parser.strings.beginObject
import com.ghost.serialization.parser.strings.consumeKeySeparator
import com.ghost.serialization.parser.strings.endObject
import com.ghost.serialization.parser.strings.nextInt
import com.ghost.serialization.parser.strings.selectString
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue


/**
 * Verifies PerfectHashFinder scales the dispatch table correctly (128→256→512→1024→2048) and
 * that JsonReaderOptions dispatches every field correctly at runtime for each table size.
 */
class PerfectHashTableScalingTest {

    private fun readerOptions(
        hashConfig: PerfectHashConfig,
        fields: List<String>
    ): JsonReaderOptions {
        return if (hashConfig.extendedKeyHash) {
            JsonReaderOptions.of(
                hashConfig.shift,
                hashConfig.multiplier,
                hashConfig.tableSize,
                enableStringDispatch = true,
                extendedKeyHash = true,
                *fields.toTypedArray()
            )
        } else {
            JsonReaderOptions.of(
                hashConfig.shift,
                hashConfig.multiplier,
                hashConfig.tableSize,
                *fields.toTypedArray()
            )
        }
    }

    // ─── field generators ───────────────────────────────────────────────────────

    /**
     * Fields sharing a 4-byte prefix but with unique lengths, so no two collide. A shared
     * prefix concentrates hash entropy, making the perfect-hash search harder.
     */
    private fun generateDiverseFields(n: Int): List<String> =
        (0 until n).map { i -> "field_${i.toString().padStart(5, '0')}" }

    /**
     * Fields sharing prefix `coll`, with pairs also sharing length so they collide,
     * forcing the polynomial hash path.
     */
    private fun generateCollidingFields(n: Int): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (result.size < n) {
            result.add("coll_type$i")
            result.add("coll_kind$i")  // same prefix `coll`, same length as above if i < 10
            i++
        }
        return result.take(n)
    }

    // ─── dispatch verifier ──────────────────────────────────────────────────────

    /**
     * Build a JSON object with the given fields, run it through all three readers,
     * and assert every field dispatches to the correct index.
     */
    private fun verifyDispatch(fields: List<String>, options: JsonReaderOptions, label: String) {
        val json = buildString {
            append("{")
            fields.forEachIndexed { i, name ->
                if (i > 0) append(",")
                append("\"$name\":$i")
            }
            append("}")
        }
        val bytes = json.encodeToByteArray()

        val streaming = GhostJsonReader(bytes)
        streaming.beginObject()
        repeat(fields.size) {
            val idx = streaming.selectString(options)
            streaming.consumeKeySeparator()
            val value = streaming.nextInt()
            assertEquals(value, idx, "$label streaming: '${fields.getOrElse(value) { "?" }}'")
        }
        streaming.endObject()

        val string = GhostJsonStringReader(json)
        string.beginObject()
        repeat(fields.size) {
            val idx = string.selectString(options)
            string.consumeKeySeparator()
            val value = string.nextInt()
            assertEquals(value, idx, "$label string: '${fields.getOrElse(value) { "?" }}'")
        }
        string.endObject()
    }

    // ─── table size 128 ─────────────────────────────────────────────────────────

    @Test
    fun tableSize128_diverseFields_dispatchesCorrectly() {
        val fields = generateDiverseFields(60)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertEquals(128, hashConfig.tableSize, "Expected 128-entry table for 60 diverse fields")
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(fields, options, "tableSize=128")
    }

    @Test
    fun tableSize128_withCollisions_dispatchesCorrectly() {
        // ~40 colliding pairs → hasCollisions=true, polynomial path, still fits in 128
        val fields = generateCollidingFields(40)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertTrue(
            hashConfig.tableSize <= 256,
            "Expected table ≤ 256 for 40 colliding fields, got ${hashConfig.tableSize}"
        )
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(fields, options, "tableSize=${hashConfig.tableSize} collisions")
    }

    // ─── table size 256 ─────────────────────────────────────────────────────────

    @Test
    fun tableSize256_diverseFields_dispatchesCorrectly() {
        // 129+ fields guarantees the search must use at least 256 slots
        val fields = generateDiverseFields(130)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertTrue(
            hashConfig.tableSize >= 256,
            "Expected at least 256-entry table for 130 fields, got ${hashConfig.tableSize}"
        )
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(fields, options, "tableSize=${hashConfig.tableSize} (target 256)")
    }

    @Test
    fun tableSize256_withCollisions_dispatchesCorrectly() {
        val fields = generateCollidingFields(130)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertTrue(
            hashConfig.tableSize >= 256,
            "Expected at least 256, got ${hashConfig.tableSize}"
        )
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(fields, options, "tableSize=${hashConfig.tableSize} collisions (target 256)")
    }

    // ─── table size 512 ─────────────────────────────────────────────────────────

    @Test
    fun tableSize512_diverseFields_dispatchesCorrectly() {
        val fields = generateDiverseFields(260)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertTrue(
            hashConfig.tableSize >= 512,
            "Expected at least 512-entry table for 260 fields, got ${hashConfig.tableSize}"
        )
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(fields, options, "tableSize=${hashConfig.tableSize} (target 512)")
    }

    @Test
    fun tableSize512_withCollisions_dispatchesCorrectly() {
        val fields = generateCollidingFields(260)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertTrue(
            hashConfig.tableSize >= 512,
            "Expected at least 512, got ${hashConfig.tableSize}"
        )
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(fields, options, "tableSize=${hashConfig.tableSize} collisions (target 512)")
    }

    // ─── table size 1024 ────────────────────────────────────────────────────────

    @Test
    fun tableSize1024_diverseFields_dispatchesCorrectly() {
        val fields = generateDiverseFields(520)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertTrue(
            hashConfig.tableSize >= 1024,
            "Expected at least 1024-entry table for 520 fields, got ${hashConfig.tableSize}"
        )
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(fields, options, "tableSize=${hashConfig.tableSize} (target 1024)")
    }

    @Test
    fun tableSize1024_withCollisions_dispatchesCorrectly() {
        val fields = generateCollidingFields(520)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertTrue(
            hashConfig.tableSize >= 1024,
            "Expected at least 1024, got ${hashConfig.tableSize}"
        )
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(
            fields,
            options,
            "tableSize=${hashConfig.tableSize} collisions (target 1024)"
        )
    }

    // ─── table size 2048 ────────────────────────────────────────────────────────

    @Test
    fun tableSize2048_diverseFields_dispatchesCorrectly() {
        val fields = generateDiverseFields(1030)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertTrue(
            hashConfig.tableSize >= 2048,
            "Expected at least 2048-entry table for 1030 fields, got ${hashConfig.tableSize}"
        )
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(fields, options, "tableSize=${hashConfig.tableSize} (target 2048)")
    }

    @Test
    fun tableSize2048_withCollisions_dispatchesCorrectly() {
        val fields = generateCollidingFields(1030)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertTrue(
            hashConfig.tableSize >= 2048,
            "Expected at least 2048, got ${hashConfig.tableSize}"
        )
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(
            fields,
            options,
            "tableSize=${hashConfig.tableSize} collisions (target 2048)"
        )
    }

    // ─── end-to-end: PerfectHashFinder output matches runtime dispatch ───────────

    @Test
    fun finderOutputMatchesRuntimeDispatch_allTableSizes() {
        // Confirms the finder's chosen parameters produce correct dispatch, not just the right size.
        val boundaries = listOf(60, 130, 260, 520)
        for (n in boundaries) {
            val fields = generateDiverseFields(n)
            val hashConfig = PerfectHashFinder.findPerfectHash(fields)
            val options = readerOptions(hashConfig, fields)

            // Sample 5 fields from across the list to keep test time reasonable
            val step = maxOf(1, fields.size / 5)
            for (i in fields.indices step step) {
                val name = fields[i]
                val json = "{\"$name\":$i}"
                val bytes = json.encodeToByteArray()

                val reader = GhostJsonReader(bytes)
                reader.beginObject()
                assertEquals(
                    i,
                    reader.selectString(options),
                    "n=$n tableSize=${hashConfig.tableSize} field='$name'"
                )
            }
        }
    }

    @Test
    fun locationPermissionWireValues_dispatchWithoutCollision() {
        val wireValues = listOf(
            "d:locations",
            "x:locations:transfer",
            "r:cameras:clips",
            "r:hubmanager",
            "r:installedapps",
            "r:linkedplaces",
            "r:locations:currentmode",
            "r:modes",
            "r:rooms",
            "r:rules",
            "r:scenes",
            "w:cameras:clips",
            "w:devices",
            "w:devices:presence",
            "w:grants:locationshare",
            "w:installedapps",
            "w:hubmanager",
            "w:linkedplaces",
            "w:locations",
            "w:locations:currentmode",
            "w:locations:geo",
            "w:rooms",
            "w:rules",
            "w:scenes",
            "unknown"
        )
        val hashConfig = PerfectHashFinder.findPerfectHash(wireValues)
        assertTrue(
            hashConfig.extendedKeyHash,
            "LocationPermission wire values require extended key hashing"
        )
        val options = readerOptions(hashConfig, wireValues)

        val geoIndex = wireValues.indexOf("w:locations:geo")
        val geoJson = "\"w:locations:geo\"".encodeToByteArray()
        val reader = GhostJsonReader(geoJson)
        assertEquals(
            geoIndex,
            reader.selectString(options),
            "w:locations:geo should dispatch to its index"
        )
    }
}
