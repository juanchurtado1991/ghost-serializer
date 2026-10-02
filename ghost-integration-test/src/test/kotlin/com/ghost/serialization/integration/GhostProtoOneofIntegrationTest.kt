package com.ghost.serialization.integration

import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.OneofPayload
import com.ghost.serialization.integration.model.ProtoOneofEvent
import com.ghost.serialization.proto.GhostProto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * Integration coverage for proto3 `oneof` JSON mapping via `@GhostWrappedKeys` and
 * `@GhostSerialization(inferred = true)`. See [OneofPayload] for the composition pattern.
 */
class GhostProtoOneofIntegrationTest {

    @Test
    fun deserializesTextVariant() {
        val result = Ghost.deserialize<ProtoOneofEvent>("""{"id":"e1","text":"hello"}""")
        assertEquals(
            expected = ProtoOneofEvent(id = "e1", payload = OneofPayload.Text(text = "hello")),
            actual = result
        )
    }

    @Test
    fun deserializesCodeVariant() {
        val result = Ghost.deserialize<ProtoOneofEvent>("""{"id":"e2","code":42}""")
        assertEquals(expected = ProtoOneofEvent(id = "e2", payload = OneofPayload.Code(code = 42)), actual = result)
    }

    @Test
    fun serializedFormHasNoWrapperOrDiscriminatorKey() {
        val json =
            Ghost.encodeToString(ProtoOneofEvent(id = "e1", payload = OneofPayload.Text(text = "hello")))
        assertEquals(expected = """{"id":"e1","text":"hello"}""", actual = json)
    }

    @Test
    fun roundTripsBothVariants() {
        val textEvent = ProtoOneofEvent(id = "e1", payload = OneofPayload.Text(text = "hello"))
        assertEquals(expected = textEvent, actual = Ghost.deserialize<ProtoOneofEvent>(Ghost.encodeToString(textEvent)))

        val codeEvent = ProtoOneofEvent(id = "e2", payload = OneofPayload.Code(code = 7))
        assertEquals(expected = codeEvent, actual = Ghost.deserialize<ProtoOneofEvent>(Ghost.encodeToString(codeEvent)))
    }

    @Test
    fun worksThroughTheDedicatedProtobufEntryPointToo() {
        val result = GhostProto.deserialize<ProtoOneofEvent>("""{"id":"e3","code":9}""")
        assertEquals(expected = ProtoOneofEvent(id = "e3", payload = OneofPayload.Code(code = 9)), actual = result)
    }

    @Test
    fun throwsWhenNeitherVariantKeyIsPresent() {
        assertFailsWith<Exception> {
            Ghost.deserialize<ProtoOneofEvent>("""{"id":"e4"}""")
        }
    }
}
