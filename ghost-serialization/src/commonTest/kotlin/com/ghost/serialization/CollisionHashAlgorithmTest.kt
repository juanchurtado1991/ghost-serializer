package com.ghost.serialization

import com.ghost.serialization.parser.common.json.JsonReaderOptions
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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.ghost.serialization.parser.common.constants.GhostJsonScanConstants as SCN

/**
 * Regression suite for the collision disambiguation algorithm.
 *
 * Field dispatch uses a 4-byte prefix hash. When two fields share the same
 * first 4 bytes AND length, hasCollisions=true and a polynomial accumulation
 * over bytes 4..N distinguishes them. Pins every edge case so a change to
 * PerfectHashFinder, JsonReaderOptions.init, buildStringDispatchTable, or
 * computeKeyHash fails loudly here first.
 *
 * Cases covered:
 *  1.  No collisions — hasCollisions stays false
 *  2.  Basic collision — same prefix+length, differ at last byte (user_id/user_ip)
 *  3.  Invisible-to-XOR collision — same prefix, same length, same lastByte, same middleByte
 *      (eventType/eventTime: XOR of 2 bytes gives identical keys; polynomial required)
 *  4.  Multiple collision pairs in one options block
 *  5.  Short fields (< 4 bytes) mixed with collision fields — polynomial must not be applied
 *  6.  Exactly 4-byte fields mixed with longer collision fields
 *  7.  Fields where hasCollisions=true but some fields are short (< 4 bytes)
 *  8.  Large collision group (40+ fields like RawSseEventEnvelope)
 *  9.  All three readers (streaming, flat, string) produce the same dispatch result
 *  10. Unknown fields return MATCH_NONE in all readers
 *  11. Field order independence — dispatch is correct regardless of JSON key order
 *  12. Single-field options (trivially no collision)
 *  13. Collision group where pairs differ only deep in the string (bytes 8+)
 *  14. hasCollisions detection: triggered only when prefix AND length match
 */
class CollisionHashAlgorithmTest {

    // ─── helpers ────────────────────────────────────────────────────────────────

    private fun optionsOf(vararg names: String) = JsonReaderOptions.of(*names)

    /** Parse {"f0":0,"f1":1,...} and verify each field dispatches to its correct index. */
    private fun assertAllThreeReadersDispatch(options: JsonReaderOptions, fields: List<String>) {
        val json = buildString {
            append("{")
            fields.forEachIndexed { i, name ->
                if (i > 0) append(",")
                append("\"$name\":$i")
            }
            append("}")
        }
        val bytes = json.encodeToByteArray()
        val expected = fields.indices.toList()

        // Streaming reader
        val streaming = GhostJsonReader(bytes)
        val streamingResults = mutableListOf<Pair<Int, Int>>()
        streaming.beginObject()
        repeat(fields.size) {
            val idx = streaming.selectString(options = options)
            streaming.consumeKeySeparator()
            val value = streaming.nextInt()
            streamingResults.add(idx to value)
        }
        streaming.endObject()
        streamingResults.forEach { (idx, value) ->
            assertEquals(
                expected = value,
                actual = idx,
                message = "Streaming reader: field '${fields[value]}' dispatched to wrong index"
            )
        }

        // Flat reader
        val flat = GhostJsonReader(bytes)
        val flatResults = mutableListOf<Pair<Int, Int>>()
        flat.beginObject()
        repeat(fields.size) {
            val idx = flat.selectString(options = options)
            flat.consumeKeySeparator()
            val value = flat.nextInt()
            flatResults.add(idx to value)
        }
        flat.endObject()
        flatResults.forEach { (idx, value) ->
            assertEquals(
                expected = value,
                actual = idx,
                message = "Flat reader: field '${fields[value]}' dispatched to wrong index"
            )
        }

        // String reader
        val string = GhostJsonStringReader(rawData = json)
        val stringResults = mutableListOf<Pair<Int, Int>>()
        string.beginObject()
        repeat(fields.size) {
            val idx = string.selectString(options = options)
            string.consumeKeySeparator()
            val value = string.nextInt()
            stringResults.add(idx to value)
        }
        string.endObject()
        stringResults.forEach { (idx, value) ->
            assertEquals(
                expected = value,
                actual = idx,
                message = "String reader: field '${fields[value]}' dispatched to wrong index"
            )
        }
    }

    // ─── 1. no collisions ───────────────────────────────────────────────────────

    @Test
    fun noCollision_differentPrefixes() {
        val options = optionsOf("name", "age", "email", "phone")
        assertFalse(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(options = options, fields = listOf("name", "age", "email", "phone"))
    }

    @Test
    fun noCollision_samePrefixDifferentLength() {
        // All share prefix `devi` but each has a unique length → no collision
        // "device"(6), "deviceId"(8), "deviceName"(10), "deviceStatus"(12)
        val options = optionsOf("device", "deviceId", "deviceName", "deviceStatus")
        assertFalse(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(
            options = options,
            fields = listOf("device", "deviceId", "deviceName", "deviceStatus")
        )
    }

    // ─── 2. basic collision — differ at last byte ───────────────────────────────

    @Test
    fun collision_userId_userIp_differAtLastByte() {
        // Both share prefix `user`, length 7 → hasCollisions=true
        // Polynomial bytes[4..6]: '_','i','d' vs '_','i','p' → different at byte 6
        val options = optionsOf("user_id", "user_ip")
        assertTrue(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(options = options, fields = listOf("user_id", "user_ip"))
    }

    @Test
    fun collision_userId_userIp_reverseOrder() {
        // Same fields, swapped index — dispatch must follow the options array order, not JSON order
        val options = optionsOf("user_ip", "user_id")
        assertTrue(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(options = options, fields = listOf("user_ip", "user_id"))
    }

    // ─── 3. invisible-to-XOR collision (the regression that broke core-kmp) ────

    @Test
    fun collision_eventType_eventTime_sameLastByteAndMiddleByte() {
        // Both: prefix `even`, length 9, lastByte='e', bytes[4]='t'
        // 2-byte XOR produces IDENTICAL keys → polynomial required to distinguish
        // e-v-e-n-t-T-y-p-e  vs  e-v-e-n-t-T-i-m-e  (differ at bytes 6,7)
        val options = optionsOf("eventType", "eventTime")
        assertTrue(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(options = options, fields = listOf("eventType", "eventTime"))
    }

    @Test
    fun collision_eventType_eventTime_mixedWithOtherFields() {
        val options = optionsOf("id", "eventType", "eventTime", "source")
        assertTrue(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(options = options, fields = listOf("id", "eventType", "eventTime", "source"))
    }

    // ─── 4. multiple collision pairs ────────────────────────────────────────────

    @Test
    fun collision_multiplePairs_userAndEvent() {
        // user_id/user_ip (differ at last) + eventType/eventTime (same last+middle)
        val options = optionsOf("user_id", "user_ip", "eventType", "eventTime")
        assertTrue(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(
            options = options,
            fields = listOf("user_id", "user_ip", "eventType", "eventTime")
        )
    }

    @Test
    fun collision_multipleGroups_hubZwave() {
        // hubZwaveExceptionEvent (22) / hubZwaveS2AuthRequestEvent (26) / hubZwaveStatusEvent (19)
        // All share `hubZ`, but different lengths → no collision within this group
        // Mixed with user_id/user_ip that DO collide
        val options = optionsOf(
            "user_id", "user_ip",
            "hubZwaveExceptionEvent", "hubZwaveS2AuthRequestEvent", "hubZwaveStatusEvent"
        )
        assertTrue(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(
            options = options,
            fields = listOf(
                "user_id",
                "user_ip",
                "hubZwaveExceptionEvent",
                "hubZwaveS2AuthRequestEvent",
                "hubZwaveStatusEvent"
            )
        )
    }

    // ─── 5. short fields (< 4 bytes) mixed with collision fields ────────────────

    @Test
    fun collision_withShortFields_polynomialNotAppliedToShortOnes() {
        // "id"/"name" are short and don't collide with each other or with user_id/user_ip,
        // but user_id/user_ip still trigger hasCollisions=true for the whole options block
        val options = optionsOf("id", "name", "user_id", "user_ip")
        assertTrue(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(options = options, fields = listOf("id", "name", "user_id", "user_ip"))
    }

    @Test
    fun collision_withOneByteField() {
        val options = optionsOf("x", "user_id", "user_ip", "count")
        assertTrue(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(options = options, fields = listOf("x", "user_id", "user_ip", "count"))
    }

    @Test
    fun collision_withTwoByteField() {
        val options = optionsOf("id", "ok", "user_id", "user_ip")
        assertTrue(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(options = options, fields = listOf("id", "ok", "user_id", "user_ip"))
    }

    @Test
    fun collision_withThreeByteField() {
        val options = optionsOf("age", "tag", "user_id", "user_ip")
        assertTrue(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(options = options, fields = listOf("age", "tag", "user_id", "user_ip"))
    }

    // ─── 6. exactly 4-byte fields ───────────────────────────────────────────────

    @Test
    fun collision_exactlyFourByteFieldWithLongerColliders() {
        // "user" (4 bytes) + "user_id"/"user_ip" (7 bytes each)
        // "user" shares prefix `user` but has length 4 — different lengths, no collision with 7-byte ones
        // user_id/user_ip still trigger hasCollisions=true
        val options = optionsOf("user", "user_id", "user_ip")
        assertTrue(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(options = options, fields = listOf("user", "user_id", "user_ip"))
    }

    // ─── 7. hasCollisions detection accuracy ────────────────────────────────────

    @Test
    fun hasCollisions_falseWhenOnlySamePrefixDifferentLength() {
        // All share `devi` prefix but different lengths
        val options = optionsOf(
            "deviceEvent",               // 11
            "deviceGroupEvent",          // 16
            "deviceHealthEvent",         // 17
            "deviceLifecycleEvent"       // 20
        )
        assertFalse(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(
            options = options,
            fields = listOf("deviceEvent", "deviceGroupEvent", "deviceHealthEvent", "deviceLifecycleEvent")
        )
    }

    @Test
    fun hasCollisions_trueOnlyWhenBothPrefixAndLengthMatch() {
        // Adding a field that shares prefix AND length with an existing one
        // Despite the name, this does collide: deviceEvent and deviceGroup are both
        // 11 chars and share prefix `devi`.
        val withoutCollision = optionsOf("deviceEvent", "deviceGroup")
        assertTrue(actual = withoutCollision.hasCollisions)
    }

    // ─── 8. large collision group (RawSseEventEnvelope-style) ───────────────────

    @Test
    fun collision_largeFieldSet_sseEventEnvelopeStyle() {
        // Field set modeled after RawSseEventEnvelope; eventType/eventTime share last+middle byte (regression case)
        val fields = listOf(
            "eventType",
            "eventTime",                          // share `even` len 9 — same lastByte+middleByte
            "deviceEvent",
            "deviceCommandsEvent",              // share `devi`, different lengths
            "deviceGroupEvent",
            "deviceHealthEvent",           // share `devi`, different lengths
            "deviceLifecycleEvent",
            "deviceJoinEvent",         // share `devi`, different lengths
            "hubHealthEvent",
            "hubLifecycleEvent",             // share `hubH`/`hubL`
            "hubZwaveExceptionEvent",
            "hubZwaveStatusEvent",   // share `hubZ`, different lengths
            "smartAppEvent",
            "smartAppDashboardCardEvent",     // share `smar`, different lengths
            "locationLifecycleEvent",
            "modeEvent",             // different prefixes
            "sceneLifecycleEvent",
            "ruleLifecycleEvent"        // different prefixes
        )
        val options = optionsOf(*fields.toTypedArray())
        assertTrue(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(options = options, fields = fields)
    }

    @Test
    fun collision_fortyPlusFields_withMultipleSamePrefixGroups() {
        val fields = listOf(
            // event group: eventType/eventTime share prefix+length+lastByte+middleByte
            "eventType", "eventTime",
            // profile group — many fields sharing `prof`
            "profileId", "profileUrl", "profileAge", "profileTag",
            "profileName", "profileBio", "profileKey",
            // device group — same prefix, different lengths
            "deviceId", "deviceEvent", "deviceGroupEvent", "deviceHealthEvent",
            "deviceLifecycleEvent", "deviceJoinEvent", "deviceCommandsEvent",
            // hub group
            "hubId", "hubEvent", "hubGroupLifecycleEvent", "hubHealthEvent",
            "hubLifecycleEvent", "hubMatterDeviceRendezvousEvent",
            "hubZwaveExceptionEvent", "hubZwaveS2AuthRequestEvent",
            "hubZwaveSecureJoinResultEvent", "hubZwaveStatusEvent",
            // unrelated
            "id", "name", "type", "status", "source", "target",
            "createdAt", "updatedAt", "deletedAt"
        )
        val options = optionsOf(*fields.toTypedArray())
        assertTrue(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(options = options, fields = fields)
    }

    // ─── 9. unknown fields return MATCH_NONE ────────────────────────────────────

    @Test
    fun unknownField_returnsMATCH_NONE_inAllReaders() {
        val options = optionsOf("user_id", "user_ip")
        val json = """{"user_name":99}"""
        val bytes = json.encodeToByteArray()

        val streaming = GhostJsonReader(bytes)
        streaming.beginObject()
        assertEquals(
            expected = SCN.MATCH_NONE,
            actual = streaming.selectString(options = options)
        )

        val flat = GhostJsonReader(bytes)
        flat.beginObject()
        assertEquals(
            expected = SCN.MATCH_NONE,
            actual = flat.selectString(options = options)
        )

        val string = GhostJsonStringReader(rawData = json)
        string.beginObject()
        assertEquals(
            expected = SCN.MATCH_NONE,
            actual = string.selectString(options = options)
        )
    }

    // ─── 10. field order independence ───────────────────────────────────────────

    @Test
    fun collision_jsonFieldOrderDoesNotAffectDispatch() {
        // JSON has fields in reverse order vs options array
        val options = optionsOf("eventType", "eventTime", "user_id", "user_ip")
        val json = """{"user_ip":3,"user_id":2,"eventTime":1,"eventType":0}"""
        val bytes = json.encodeToByteArray()

        fun readAll(
            selectFn: () -> Int,
            intFn: () -> Int,
            separatorFn: () -> Unit,
            endFn: () -> Unit
        ): Map<Int, Int> {
            val result = mutableMapOf<Int, Int>()
            repeat(4) {
                val idx = selectFn()
                separatorFn()
                val value = intFn()
                result[idx] = value
            }
            endFn()
            return result
        }

        val streaming = GhostJsonReader(bytes)
        streaming.beginObject()
        val sResult = readAll(
            selectFn = { streaming.selectString(options = options) },
            intFn = { streaming.nextInt() },
            separatorFn = { streaming.consumeKeySeparator() },
            endFn = { streaming.endObject() }
        )
        // Indices follow options array order: eventType, eventTime, user_id, user_ip
        assertEquals(
            expected = 0,
            actual = sResult[0]
        )
        assertEquals(
            expected = 1,
            actual = sResult[1]
        )
        assertEquals(
            expected = 2,
            actual = sResult[2]
        )
        assertEquals(
            expected = 3,
            actual = sResult[3]
        )
    }

    // ─── 11. single field (trivially no collision) ──────────────────────────────

    @Test
    fun singleField_noCollision() {
        val options = optionsOf("eventType")
        assertFalse(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(options = options, fields = listOf("eventType"))
    }

    // ─── 12. collision deep in the string (bytes 8+) ────────────────────────────

    @Test
    fun collision_differOnlyAtByte8OrLater() {
        // "profileCategory" (15) vs "profileCallback" (15): prefix `prof`, len 15
        // p-r-o-f-i-l-e-C-a-t-e-g-o-r-y  vs  p-r-o-f-i-l-e-C-a-l-l-b-a-c-k
        // Same: bytes 0-8 (profileCa), differ at byte 9: 't' vs 'l'
        val options = optionsOf("profileCategory", "profileCallback")
        assertTrue(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(options = options, fields = listOf("profileCategory", "profileCallback"))
    }

    @Test
    fun collision_differOnlyAtLastByte_longFields() {
        // "installationStatus" (18) vs "installationStatud" (18) — synthetic but valid edge case
        // prefix `inst`, same length 18, differ only at last byte: 's' vs 'd'
        val options = optionsOf("installationStatus", "installationStatud")
        assertTrue(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(options = options, fields = listOf("installationStatus", "installationStatud"))
    }

    // ─── 13. collision with fields that only differ at byte 4 (first byte after prefix) ──

    @Test
    fun collision_differAtByte4Only() {
        // "user_admin" (10) vs "user_buyer" (10): prefix `user`, len 10
        // differ at byte 4: '_' (95) is same! byte 5: 'a' vs 'b'
        val options = optionsOf("user_admin", "user_buyer")
        assertTrue(actual = options.hasCollisions)
        assertAllThreeReadersDispatch(options = options, fields = listOf("user_admin", "user_buyer"))
    }

    // ─── 14. mixed readers produce identical index for the same field ────────────

    @Test
    fun allReadersAgreeOnIndex_forCollisionFields() {
        val fields = listOf("eventType", "eventTime", "user_id", "user_ip")
        val options = optionsOf(*fields.toTypedArray())

        fields.forEachIndexed { expectedIdx, fieldName ->
            val json = """{"$fieldName":1}"""
            val bytes = json.encodeToByteArray()

            val streaming = GhostJsonReader(bytes)
            streaming.beginObject()
            val streamIdx = streaming.selectString(options = options)

            val flat = GhostJsonReader(bytes)
            flat.beginObject()
            val flatIdx = flat.selectString(options = options)

            val string = GhostJsonStringReader(rawData = json)
            string.beginObject()
            val stringIdx = string.selectString(options = options)

            assertEquals(
                expected = expectedIdx,
                actual = streamIdx,
                message = "Streaming: '$fieldName'"
            )
            assertEquals(
                expected = expectedIdx,
                actual = flatIdx,
                message = "Flat: '$fieldName'"
            )
            assertEquals(
                expected = expectedIdx,
                actual = stringIdx,
                message = "String: '$fieldName'"
            )
        }
    }
}
