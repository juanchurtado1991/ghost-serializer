package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.exception.GhostJsonException
import com.ghost.serialization.integration.model.ProtoAccountIds
import com.ghost.serialization.integration.model.ProtoAccountIdsMessage
import com.ghost.serialization.integration.model.ProtoBenchUser
import com.ghost.serialization.integration.model.ProtoDeviceEventListItem
import com.ghost.serialization.integration.model.ProtoUInt64FieldMessage
import com.ghost.serialization.proto.GhostProto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
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
    fun uLongBareNumberAboveLongMaxDecodesExactly() {
        val json = """{"shardId":18446744073709551615}"""
        val parsed = GhostProto.deserialize<ProtoUInt64FieldMessage>(json)
        assertEquals(expected = ULong.MAX_VALUE, actual = parsed.shard_id)
    }

    @Test
    fun uLongBareNegativeNumberIsRejected() {
        assertFailsWith<GhostJsonException> {
            GhostProto.deserialize<ProtoUInt64FieldMessage>("""{"shardId":-1}""")
        }
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

    @Test
    fun zeroValuedFieldsOmittedOnSerializeDecodeBackToTheSameMessage() {
        val deviceItem = ProtoDeviceEventListItem(
            device_id = 0L,
            label = ""
        )
        val deviceJson = Ghost.encodeToString(value = deviceItem)
        assertEquals(
            expected = "{}",
            actual = deviceJson
        )
        assertEquals(
            expected = deviceItem,
            actual = Ghost.deserialize<ProtoDeviceEventListItem>(json = deviceJson)
        )
        assertEquals(
            expected = deviceItem,
            actual = GhostProto.deserialize<ProtoDeviceEventListItem>(json = deviceJson)
        )

        val shard = ProtoUInt64FieldMessage(shard_id = 0uL)
        val shardJson = Ghost.encodeToString(value = shard)
        assertEquals(
            expected = shard,
            actual = Ghost.deserialize<ProtoUInt64FieldMessage>(bytes = shardJson.encodeToByteArray())
        )
        assertEquals(
            expected = shard,
            actual = GhostProto.deserialize<ProtoUInt64FieldMessage>(json = shardJson)
        )

        val accounts = ProtoAccountIdsMessage(account_ids = ProtoAccountIds(value = emptyList()))
        val accountsJson = Ghost.encodeToString(value = accounts)
        assertEquals(
            expected = accounts,
            actual = GhostProto.deserialize<ProtoAccountIdsMessage>(json = accountsJson)
        )
    }

    @Test
    fun zeroValueOverNonZeroDefaultIsWrittenSoItReadsBack() {
        val user = ProtoBenchUser(
            user_id = 1L,
            name = "n",
            email = "e",
            score = 0.5,
            is_active = false,
            role = ""
        )
        val json = Ghost.encodeToString(value = user)

        assertTrue(actual = json.contains("\"isActive\":false"), message = json)
        assertEquals(
            expected = user,
            actual = GhostProto.deserialize<ProtoBenchUser>(json = json)
        )
    }
}
