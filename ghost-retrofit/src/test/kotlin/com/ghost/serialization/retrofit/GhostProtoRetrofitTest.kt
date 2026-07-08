@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import kotlin.test.assertEquals

interface MockProtoApiService {
    @GET("/event")
    suspend fun getEvent(): ProtoDeviceEvent

    @POST("/event")
    suspend fun createEvent(@Body event: ProtoDeviceEvent): ProtoDeviceEvent
}

/**
 * Verifies [GhostProtoConverterFactory] end-to-end over real HTTP (MockWebServer), proving the
 * proto3 quoted-int64 wire format round-trips correctly through Retrofit — not just that the
 * generated code contains the right method calls (that's covered by the KSP compiler tests).
 */
class GhostProtoRetrofitTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: MockProtoApiService

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        Ghost.addRegistry(ProtoRetrofitTestRegistry)

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GhostProtoConverterFactory.create())
            .build()

        apiService = retrofit.create(MockProtoApiService::class.java)
    }

    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `parses a quoted int64 response body`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"deviceId":"9223372036854775807","label":"sensor-1"}""")
                .addHeader("Content-Type", "application/json")
        )

        val event = apiService.getEvent()
        assertEquals(Long.MAX_VALUE, event.deviceId)
        assertEquals("sensor-1", event.label)
    }

    @Test
    fun `parses a bare numeric int64 response body`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"deviceId":42,"label":"sensor-2"}""")
                .addHeader("Content-Type", "application/json")
        )

        val event = apiService.getEvent()
        assertEquals(42L, event.deviceId)
    }

    @Test
    fun `writes a quoted int64 request body`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("""{"deviceId":"1","label":"ack"}"""))

        apiService.createEvent(ProtoDeviceEvent(deviceId = 123456789012345L, label = "sensor-3"))

        val request = mockWebServer.takeRequest()
        assertEquals(
            """{"deviceId":"123456789012345","label":"sensor-3"}""",
            request.body.readUtf8()
        )
    }
}
