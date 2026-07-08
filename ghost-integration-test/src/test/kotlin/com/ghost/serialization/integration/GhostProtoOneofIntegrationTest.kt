package com.ghost.serialization.integration

import com.ghost.protobuf.GhostProtobuf
import com.ghost.serialization.Ghost
import com.ghost.serialization.integration.model.OneofPayload
import com.ghost.serialization.integration.model.ProtoOneofEvent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * End-to-end proof that `@GhostWrappedKeys` + `@GhostSerialization(inferred = true)` compose to
 * implement proto3 `oneof` JSON mapping — real KSP-generated code, not a hand-written stand-in
 * or a source-text assertion. See [com.ghost.serialization.integration.model.ProtoOneofEvent]
 * for why this composition works.
 */
class GhostProtoOneofIntegrationTest {

    @Test
    fun deserializesTextVariant() {
        val result = Ghost.deserialize<ProtoOneofEvent>("""{"id":"e1","text":"hello"}""")
        assertEquals(ProtoOneofEvent(id = "e1", payload = OneofPayload.Text("hello")), result)
    }

    @Test
    fun deserializesCodeVariant() {
        val result = Ghost.deserialize<ProtoOneofEvent>("""{"id":"e2","code":42}""")
        assertEquals(ProtoOneofEvent(id = "e2", payload = OneofPayload.Code(42)), result)
    }

    @Test
    fun serializedFormHasNoWrapperOrDiscriminatorKey() {
        val json = Ghost.encodeToString(ProtoOneofEvent(id = "e1", payload = OneofPayload.Text("hello")))
        assertEquals("""{"id":"e1","text":"hello"}""", json)
    }

    @Test
    fun roundTripsBothVariants() {
        val textEvent = ProtoOneofEvent(id = "e1", payload = OneofPayload.Text("hello"))
        assertEquals(textEvent, Ghost.deserialize<ProtoOneofEvent>(Ghost.encodeToString(textEvent)))

        val codeEvent = ProtoOneofEvent(id = "e2", payload = OneofPayload.Code(7))
        assertEquals(codeEvent, Ghost.deserialize<ProtoOneofEvent>(Ghost.encodeToString(codeEvent)))
    }

    @Test
    fun worksThroughTheDedicatedProtobufEntryPointToo() {
        val result = GhostProtobuf.deserialize<ProtoOneofEvent>("""{"id":"e3","code":9}""")
        assertEquals(ProtoOneofEvent(id = "e3", payload = OneofPayload.Code(9)), result)
    }

    @Test
    fun throwsWhenNeitherVariantKeyIsPresent() {
        assertFailsWith<Exception> {
            Ghost.deserialize<ProtoOneofEvent>("""{"id":"e4"}""")
        }
    }
}
