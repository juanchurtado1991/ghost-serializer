package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.ProtoAccountIds
import com.ghost.serialization.integration.model.ProtoAccountIdsMessage
import com.ghost.serialization.integration.model.ProtoDeviceEventListItem
import com.ghost.serialization.integration.model.ProtoUInt64FieldMessage
import com.ghost.serialization.proto.GhostProto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GhostProtoKnownGapsIntegrationTest {

    @Test
    fun valueClassWrappingCollectionQuotesElementsAndOmitsEmptyOnSerialize() {
        val empty = ProtoAccountIdsMessage(account_ids = ProtoAccountIds(value = emptyList()))
        assertEquals(expected = "{}", actual = Ghost.encodeToString(empty))

        val populated = ProtoAccountIdsMessage(account_ids = ProtoAccountIds(value = listOf(123L, 456L)))
        val json = Ghost.encodeToString(populated)
        assertTrue(json.contains("\"123\""), json)
        assertTrue(json.contains("\"456\""), json)

        val roundTrip = Ghost.deserialize<ProtoAccountIdsMessage>(json.encodeToByteArray())
        assertEquals(expected = populated, actual = roundTrip)
    }

    @Test
    fun valueClassWrappingCollectionDeserializesBareNumbersLeniently() {
        val json = """{"accountIds":[9223372036854775807,1]}"""
        val parsed = GhostProto.deserialize<ProtoAccountIdsMessage>(json)
        assertEquals(
            expected = ProtoAccountIdsMessage(
                account_ids = ProtoAccountIds(value = listOf(Long.MAX_VALUE, 1L)),
            ),
            actual = parsed,
        )
    }

    @Test
    fun valueClassWrappingCollectionPreservesSingleElementList() {
        val model = ProtoAccountIdsMessage(account_ids = ProtoAccountIds(value = listOf(42L)))
        val json = Ghost.encodeToString(model)
        assertTrue(json.contains("\"42\""), json)
        assertFalse(
            actual = json.contains(",\"42\""),
            message = "single-element list must not duplicate values: $json"
        )

        val roundTrip = Ghost.deserialize<ProtoAccountIdsMessage>(json.encodeToByteArray())
        assertEquals(expected = model, actual = roundTrip)
    }

    @Test
    fun uLongFieldSupportsFullUint64RangeOnProtoPath() {
        val max = ULong.MAX_VALUE
        val model = ProtoUInt64FieldMessage(shard_id = max)
        val json = Ghost.encodeToString(model)
        assertTrue(json.contains("\"18446744073709551615\""), json)

        val viaProto = GhostProto.deserialize<ProtoUInt64FieldMessage>(json)
        assertEquals(expected = model, actual = viaProto)
    }

    @Test
    fun uLongBareNumberAboveLongMaxFallsBackToInt64Path() {
        // Bare JSON numbers take the int64 fast path — full uint64 range requires a quoted string.
        val json = """{"shardId":18446744073709551615}"""
        val parsed = GhostProto.deserialize<ProtoUInt64FieldMessage>(json)
        assertEquals(expected = Long.MAX_VALUE.toULong(), actual = parsed.shard_id)
    }

    @Test
    fun uLongFieldDeserializesQuotedBareAndGhostDeserializeRoundTrip() {
        val model = ProtoUInt64FieldMessage(shard_id = 9_000_000_000_000_000_001uL)
        val json = Ghost.encodeToString(model)
        assertTrue(json.contains("\"9000000000000000001\""), json)

        assertEquals(expected = model, actual = Ghost.deserialize(json.encodeToByteArray()))
        assertEquals(expected = model, actual = GhostProto.deserialize(json))
    }

    @Test
    fun uLongZeroValueIsOmittedOnSerialize() {
        val json = Ghost.encodeToString(ProtoUInt64FieldMessage(shard_id = 0uL))
        assertFalse(json.contains("shardId"), json)
    }

    @Test
    fun uLongNonZeroValueIsNotOmittedOnSerialize() {
        val json = Ghost.encodeToString(ProtoUInt64FieldMessage(shard_id = 1uL))
        assertTrue(json.contains("\"1\""), json)
    }

    @Test
    fun protoListBodyItemSerializesQuotedInt64Fields() {
        val items = listOf(
            ProtoDeviceEventListItem(device_id = Long.MAX_VALUE, label = "edge"),
            ProtoDeviceEventListItem(device_id = 7L, label = "seven"),
        )
        val json = Ghost.encodeToString(items)
        assertTrue(json.contains("\"9223372036854775807\""), json)
        assertTrue(json.contains("\"edge\""), json)

        val parsed = GhostProto.deserialize<List<ProtoDeviceEventListItem>>(json)
        assertEquals(expected = items, actual = parsed)
    }

    @Test
    fun protoListBodyDeserializesBareInt64Elements() {
        val json =
            """[{"deviceId":42,"label":"x"},{"deviceId":9223372036854775807,"label":"max"}]"""
        val parsed = GhostProto.deserialize<List<ProtoDeviceEventListItem>>(json)
        assertEquals(
            expected = listOf(
                ProtoDeviceEventListItem(device_id = 42L, label = "x"),
                ProtoDeviceEventListItem(device_id = Long.MAX_VALUE, label = "max"),
            ),
            actual = parsed,
        )
    }
}
