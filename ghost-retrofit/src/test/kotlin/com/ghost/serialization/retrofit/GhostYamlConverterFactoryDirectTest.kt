@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import kotlin.test.assertEquals
import kotlin.test.assertNull

class GhostYamlConverterFactoryDirectTest {

    @BeforeEach
    fun setup() {
        Ghost.addRegistry(YamlRetrofitTestRegistry)
    }

    @Test
    fun responseBodyConverter_returnsNullForJsonOnlySerializer() {
        val factory = GhostYamlConverterFactory.create()
        val retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
        val converter = factory.responseBodyConverter(ProtoDeviceEvent::class.java, emptyArray(), retrofit)
        assertNull(converter)
    }

    @Test
    fun responseBodyConverter_parsesYamlPayload() {
        val factory = GhostYamlConverterFactory.create()
        val retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
        val converter = factory.responseBodyConverter(YamlDeviceProfile::class.java, emptyArray(), retrofit)
            ?: error("converter should not be null")

        val yaml = """
            deviceId: 42
            label: sensor-1
        """.trimIndent()
        val result = converter.convert(yaml.toResponseBody())
        assertEquals(YamlDeviceProfile(42, "sensor-1"), result)
    }
}
