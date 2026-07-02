@file:OptIn(com.ghost.serialization.InternalGhostApi::class)

package com.ghost.serialization.integration

import com.ghost.serialization.integration.model.DeviceEventPayload
import com.ghost.serialization.integration.model.InvoicePaidPayload
import com.ghost.serialization.integration.model.ModeEventPayload
import com.ghost.serialization.integration.model.SseEventEnvelope
import com.ghost.serialization.integration.model.SseEventEnvelopeSerializer
import com.ghost.serialization.integration.model.WebhookEnvelope
import com.ghost.serialization.integration.model.WebhookEnvelopeSerializer
import com.ghost.serialization.types.RawJson
import com.ghost.serialization.types.RawJsonKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

/** Runtime tests for [@GhostJsonEnvelope][com.ghost.serialization.annotations.GhostJsonEnvelope] routing. */
class GhostJsonEnvelopeIntegrationTest {

    @Test
    fun parsePayload_zeroCopySliceFromFlatBytes() {
        val json = """
            {"eventType":"DEVICE_EVENT","eventTime":42,"deviceEvent":{"deviceId":"abc"}}
        """.trimIndent().encodeToByteArray()

        val payload = SseEventEnvelopeSerializer.parsePayload(json)
        assertEquals(RawJsonKind.OBJECT, payload?.kind())
        assertSame(json, payload?.storage)
        assertTrue(payload!!.storageOffset > 0)
    }

    @Test
    fun routeTyped_decodesAnnotatedTarget() {
        val json = """
            {"eventType":"MODE_EVENT","eventTime":1,"modeEvent":{"mode":"away"}}
        """.trimIndent().encodeToByteArray()

        val typed = SseEventEnvelopeSerializer.parseTyped(json)
        assertEquals(ModeEventPayload("away"), typed)
    }

    @Test
    fun routePayload_unknownDiscriminatorUsesFallback() {
        val json = """
            {"eventType":"UNKNOWN_TYPE","unknownEvent":{"x":1}}
        """.trimIndent().encodeToByteArray()

        val payload = SseEventEnvelopeSerializer.parsePayload(json)
        assertEquals(RawJsonKind.OBJECT, payload?.kind())
    }

    @Test
    fun genericWebhook_parseTypedDecodesSharedDataField() {
        val json = """{"type":"invoice.paid","data":{"amount":999}}""".encodeToByteArray()

        val typed = WebhookEnvelopeSerializer.parseTyped(json)
        assertEquals(InvoicePaidPayload(999), typed)
    }

    @Test
    fun genericWebhook_routePayloadReturnsDataForAnyType() {
        val json = """{"type":"customer.created","data":{"id":"cus_1"}}""".encodeToByteArray()

        val payload = WebhookEnvelopeSerializer.parsePayload(json)
        assertEquals(RawJsonKind.OBJECT, payload?.kind())
    }

    @Test
    fun routePayload_knownTypeWithoutPayloadReturnsNull() {
        val json = """{"eventType":"DEVICE_EVENT","eventTime":0}""".encodeToByteArray()
        assertNull(SseEventEnvelopeSerializer.parsePayload(json))
    }

    @Test
    fun routePayload_afterDeserialize_matchesDirectRouting() {
        val json = """
            {"eventType":"DEVICE_EVENT","eventTime":7,"deviceEvent":{"deviceId":"d1"}}
        """.trimIndent().encodeToByteArray()
        val envelope = com.ghost.serialization.Ghost.deserialize<SseEventEnvelope>(json)
        assertEquals(
            SseEventEnvelopeSerializer.routePayload(envelope),
            SseEventEnvelopeSerializer.parsePayload(json)
        )
    }

    @Test
    fun routeTyped_deviceEventPayload() {
        val json = """
            {"eventType":"DEVICE_EVENT","eventTime":0,"deviceEvent":{"deviceId":"hub-1"}}
        """.trimIndent().encodeToByteArray()
        assertEquals(DeviceEventPayload("hub-1"), SseEventEnvelopeSerializer.parseTyped(json))
    }
}
