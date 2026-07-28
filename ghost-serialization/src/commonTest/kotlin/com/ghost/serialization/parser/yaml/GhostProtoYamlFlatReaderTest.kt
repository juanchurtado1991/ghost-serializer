@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.parser.yaml

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.parser.common.decodeBase64String
import com.ghost.serialization.parser.common.encodeBase64String
import com.ghost.serialization.yaml.exception.GhostYamlException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.strings.beginObject

class GhostProtoYamlFlatReaderTest {

    @Test
    fun quotedInt64LongFieldAccepted() {
        val yaml = """
            request_id: "9223372036854775807"
        """.trimIndent()
        val reader = GhostProtoYamlFlatReader(yaml.encodeToByteArray())
        reader.beginObject()
        assertEquals("request_id", reader.nextKey())
        assertEquals(Long.MAX_VALUE, reader.nextLong())
        reader.endObject()
    }

    @Test
    fun bareInt64LongFieldAccepted() {
        val yaml = """
            request_id: 9223372036854775807
        """.trimIndent()
        val reader = GhostProtoYamlFlatReader(yaml.encodeToByteArray())
        reader.beginObject()
        reader.nextKey()
        assertEquals(Long.MAX_VALUE, reader.nextLong())
        reader.endObject()
    }

    @Test
    fun protoBytesFieldDecodesBase64Scalar() {
        val payload = "abc123!?$*&()'-=@~".encodeToByteArray()
        val encoded = encodeBase64String(payload)
        val yaml = """
            payload: "$encoded"
        """.trimIndent()
        val reader = GhostProtoYamlFlatReader(yaml.encodeToByteArray())
        reader.beginObject()
        reader.nextKey()
        assertEquals(payload.toList(), reader.nextProtoBytes().toList())
        reader.endObject()
    }

    @Test
    fun quotedUInt64FullRangeAccepted() {
        val yaml = """
            shard_id: "18446744073709551615"
        """.trimIndent()
        val reader = GhostProtoYamlFlatReader(yaml.encodeToByteArray())
        reader.beginObject()
        reader.nextKey()
        assertEquals(ULong.MAX_VALUE, reader.nextProtoUInt64())
        reader.endObject()
    }

    @Test
    fun yamlStringScalarLongParsesForBothReaders() {
        val yaml = """
            request_id: "9223372036854775807"
        """.trimIndent()
        val protoReader = GhostProtoYamlFlatReader(yaml.encodeToByteArray())
        protoReader.beginObject()
        protoReader.nextKey()
        assertEquals(Long.MAX_VALUE, protoReader.nextLong())
        protoReader.endObject()

        val plainReader = GhostYamlFlatReader(yaml.encodeToByteArray())
        plainReader.beginObject()
        plainReader.nextKey()
        assertEquals(Long.MAX_VALUE, plainReader.nextLong())
        plainReader.endObject()
    }

    @Test
    fun protoYamlReaderAlwaysEnablesNumericCoercionForLong() {
        val yaml = """
            request_id: "42"
        """.trimIndent()
        val protoReader = GhostProtoYamlFlatReader(yaml.encodeToByteArray())
        protoReader.beginObject()
        protoReader.nextKey()
        assertEquals(42L, protoReader.nextLong())
        protoReader.endObject()
    }

    @Test
    fun base64RoundTripMatchesSharedCodec() {
        val bytes = byteArrayOf(0, 127, -128, 1, 2, 3)
        val encoded = encodeBase64String(bytes)
        assertEquals(bytes.toList(), decodeBase64String(encoded).toList())
    }
}
