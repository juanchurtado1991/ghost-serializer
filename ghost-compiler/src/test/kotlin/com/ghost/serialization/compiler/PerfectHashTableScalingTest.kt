package com.ghost.serialization.compiler

import com.ghost.serialization.parser.GhostJsonFlatReader
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.GhostJsonStringReader
import com.ghost.serialization.parser.JsonReaderOptions
import com.ghost.serialization.parser.beginObject
import com.ghost.serialization.parser.consumeKeySeparator
import com.ghost.serialization.parser.endObject
import com.ghost.serialization.parser.nextInt
import com.ghost.serialization.parser.selectString
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies that PerfectHashFinder scales the dispatch table correctly (128→256→512→1024→2048)
 * and that JsonReaderOptions dispatches every field correctly at runtime for each table size.
 *
 * Strategy: generate enough fields with intentional prefix collisions to force the brute-force
 * search to exhaust smaller table sizes and fall through to the target size. Then verify that
 * the resulting (shift, multiplier, tableSize) dispatches correctly in all three readers.
 */
class PerfectHashTableScalingTest {

    private fun readerOptions(hashConfig: PerfectHashConfig, fields: List<String>): JsonReaderOptions {
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
     * Generate N fields that all start with the same 4-byte prefix but have unique lengths.
     * When hasCollisions=true (triggered if any two share prefix+length) the polynomial
     * accumulation runs over all extra bytes. Using a shared prefix maximises hash entropy
     * concentration, making the perfect-hash search harder and forcing larger tables.
     *
     * Fields: "field_00000", "field_00001", ..., "field_NNNNN"
     * All share prefix `fiel` (102,105,101,108) and have unique lengths (no collisions).
     */
    private fun generateDiverseFields(n: Int): List<String> =
        (0 until n).map { i -> "field_${i.toString().padStart(5, '0')}" }

    /**
     * Generate N fields that share prefix `coll` AND some share the same length,
     * triggering hasCollisions=true and forcing the polynomial path.
     * Fields: "coll_a0", "coll_b0", "coll_a1", "coll_b1", ...
     * Pairs (coll_aX, coll_bX) share prefix `coll` + same length → collision.
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

        // Streaming reader
        val streaming = GhostJsonReader(bytes)
        streaming.beginObject()
        repeat(fields.size) {
            val idx = streaming.selectString(options)
            streaming.consumeKeySeparator()
            val value = streaming.nextInt()
            assertEquals(value, idx, "$label streaming: '${fields.getOrElse(value) { "?" }}'")
        }
        streaming.endObject()

        // Flat reader
        val flat = GhostJsonFlatReader(bytes)
        flat.beginObject()
        repeat(fields.size) {
            val idx = flat.selectString(options)
            flat.consumeKeySeparator()
            val value = flat.nextInt()
            assertEquals(value, idx, "$label flat: '${fields.getOrElse(value) { "?" }}'")
        }
        flat.endObject()

        // String reader
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
        assertTrue(hashConfig.tableSize <= 256, "Expected table ≤ 256 for 40 colliding fields, got ${hashConfig.tableSize}")
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(fields, options, "tableSize=${hashConfig.tableSize} collisions")
    }

    // ─── table size 256 ─────────────────────────────────────────────────────────

    @Test
    fun tableSize256_diverseFields_dispatchesCorrectly() {
        // 129+ fields guarantees the search must use at least 256 slots
        val fields = generateDiverseFields(130)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertTrue(hashConfig.tableSize >= 256, "Expected at least 256-entry table for 130 fields, got ${hashConfig.tableSize}")
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(fields, options, "tableSize=${hashConfig.tableSize} (target 256)")
    }

    @Test
    fun tableSize256_withCollisions_dispatchesCorrectly() {
        val fields = generateCollidingFields(130)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertTrue(hashConfig.tableSize >= 256, "Expected at least 256, got ${hashConfig.tableSize}")
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(fields, options, "tableSize=${hashConfig.tableSize} collisions (target 256)")
    }

    // ─── table size 512 ─────────────────────────────────────────────────────────

    @Test
    fun tableSize512_diverseFields_dispatchesCorrectly() {
        val fields = generateDiverseFields(260)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertTrue(hashConfig.tableSize >= 512, "Expected at least 512-entry table for 260 fields, got ${hashConfig.tableSize}")
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(fields, options, "tableSize=${hashConfig.tableSize} (target 512)")
    }

    @Test
    fun tableSize512_withCollisions_dispatchesCorrectly() {
        val fields = generateCollidingFields(260)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertTrue(hashConfig.tableSize >= 512, "Expected at least 512, got ${hashConfig.tableSize}")
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(fields, options, "tableSize=${hashConfig.tableSize} collisions (target 512)")
    }

    // ─── table size 1024 ────────────────────────────────────────────────────────

    @Test
    fun tableSize1024_diverseFields_dispatchesCorrectly() {
        val fields = generateDiverseFields(520)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertTrue(hashConfig.tableSize >= 1024, "Expected at least 1024-entry table for 520 fields, got ${hashConfig.tableSize}")
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(fields, options, "tableSize=${hashConfig.tableSize} (target 1024)")
    }

    @Test
    fun tableSize1024_withCollisions_dispatchesCorrectly() {
        val fields = generateCollidingFields(520)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertTrue(hashConfig.tableSize >= 1024, "Expected at least 1024, got ${hashConfig.tableSize}")
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(fields, options, "tableSize=${hashConfig.tableSize} collisions (target 1024)")
    }

    // ─── table size 2048 ────────────────────────────────────────────────────────

    @Test
    fun tableSize2048_diverseFields_dispatchesCorrectly() {
        val fields = generateDiverseFields(1030)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertTrue(hashConfig.tableSize >= 2048, "Expected at least 2048-entry table for 1030 fields, got ${hashConfig.tableSize}")
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(fields, options, "tableSize=${hashConfig.tableSize} (target 2048)")
    }

    @Test
    fun tableSize2048_withCollisions_dispatchesCorrectly() {
        val fields = generateCollidingFields(1030)
        val hashConfig = PerfectHashFinder.findPerfectHash(fields)
        assertTrue(hashConfig.tableSize >= 2048, "Expected at least 2048, got ${hashConfig.tableSize}")
        val options = readerOptions(hashConfig, fields)
        verifyDispatch(fields, options, "tableSize=${hashConfig.tableSize} collisions (target 2048)")
    }

    // ─── end-to-end: PerfectHashFinder output matches runtime dispatch ───────────

    @Test
    fun finderOutputMatchesRuntimeDispatch_allTableSizes() {
        // For each size boundary, confirm that the finder's chosen parameters
        // actually produce correct dispatch — not just that the size is right.
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

                val flat = GhostJsonFlatReader(bytes)
                flat.beginObject()
                assertEquals(i, flat.selectString(options), "n=$n tableSize=${hashConfig.tableSize} field='$name'")
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
        assertTrue(hashConfig.extendedKeyHash, "LocationPermission wire values require extended key hashing")
        val options = readerOptions(hashConfig, wireValues)

        val geoIndex = wireValues.indexOf("w:locations:geo")
        val geoJson = "\"w:locations:geo\"".encodeToByteArray()
        val flat = GhostJsonFlatReader(geoJson)
        assertEquals(geoIndex, flat.selectString(options), "w:locations:geo should dispatch to its index")
    }
}
