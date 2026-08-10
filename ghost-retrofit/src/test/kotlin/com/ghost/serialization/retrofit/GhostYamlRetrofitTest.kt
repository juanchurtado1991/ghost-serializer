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
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GhostYamlRetrofitTest {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: MockYamlApiService

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()
        Ghost.addRegistry(YamlRetrofitTestRegistry)

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GhostYamlConverterFactory.create())
            .build()

        apiService = retrofit.create(MockYamlApiService::class.java)
    }

    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
    }

    @Test
    fun `parses a yaml response body`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    deviceId: 42
                    label: sensor-1
                    """.trimIndent()
                )
                .addHeader("Content-Type", "application/yaml")
        )

        val profile = apiService.getProfile()
        assertEquals(42, profile.deviceId)
        assertEquals("sensor-1", profile.label)
    }

    @Test
    fun `writes a yaml request body`() = runTest {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    deviceId: 1
                    label: ack
                    """.trimIndent()
                )
        )

        apiService.createProfile(YamlDeviceProfile(deviceId = 7, label = "sensor-2"))

        val request = mockWebServer.takeRequest()
        val body = request.body.readUtf8()
        assertTrue(body.contains("deviceId: 7"))
        assertTrue(body.contains("label: sensor-2") || body.contains("label: \"sensor-2\""))
    }
}
