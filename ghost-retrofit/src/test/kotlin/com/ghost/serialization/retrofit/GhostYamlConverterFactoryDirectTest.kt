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

    private interface ParameterizedYamlHolder {
        fun list(): List<YamlDeviceProfile>
        fun set(): Set<YamlDeviceProfile>
        fun map(): Map<String, YamlDeviceProfile>
        fun intKeyMap(): Map<Int, YamlDeviceProfile>
    }

    private interface JsonOnlyListHolder {
        fun list(): List<ProtoDeviceEvent>
    }

    @BeforeEach
    fun setup() {
        Ghost.addRegistry(YamlRetrofitTestRegistry)
    }

    @Test
    fun responseBodyConverter_returnsNullForJsonOnlySerializer() {
        val factory = GhostYamlConverterFactory.create()
        val retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
        val converter =
            factory.responseBodyConverter(ProtoDeviceEvent::class.java, emptyArray(), retrofit)
        assertNull(converter)
    }

    @Test
    fun responseBodyConverter_parsesYamlPayload() {
        val factory = GhostYamlConverterFactory.create()
        val retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
        val converter =
            factory.responseBodyConverter(YamlDeviceProfile::class.java, emptyArray(), retrofit)
                ?: error("converter should not be null")

        val yaml = """
            deviceId: 42
            label: sensor-1
        """.trimIndent()
        val result = converter.convert(yaml.toResponseBody())
        assertEquals(YamlDeviceProfile(42, "sensor-1"), result)
    }

    @Test
    fun responseBodyConverter_returnsNullForListOfNonYamlSerializer() {
        val factory = GhostYamlConverterFactory.create()
        val retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
        val genericType = JsonOnlyListHolder::class.java.getMethod("list").genericReturnType
        assertNull(factory.responseBodyConverter(genericType, emptyArray(), retrofit))
    }

    @Test
    fun responseBodyConverter_parsesYamlListBody() {
        val factory = GhostYamlConverterFactory.create()
        val retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
        val genericType = ParameterizedYamlHolder::class.java.getMethod("list").genericReturnType
        val converter = factory.responseBodyConverter(genericType, emptyArray(), retrofit)!!

        val yaml = """
            - deviceId: 1
              label: one
            - deviceId: 2
              label: two
        """.trimIndent()

        @Suppress("UNCHECKED_CAST")
        val result = converter.convert(yaml.toResponseBody()) as List<YamlDeviceProfile>
        assertEquals(2, result.size)
        assertEquals("one", result[0].label)
    }

    @Test
    fun responseBodyConverter_parsesYamlMapBody() {
        val factory = GhostYamlConverterFactory.create()
        val retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
        val genericType = ParameterizedYamlHolder::class.java.getMethod("map").genericReturnType
        val converter = factory.responseBodyConverter(genericType, emptyArray(), retrofit)!!

        val yaml = """
            east:
              deviceId: 7
              label: east-pod
        """.trimIndent()

        @Suppress("UNCHECKED_CAST")
        val result = converter.convert(yaml.toResponseBody()) as Map<String, YamlDeviceProfile>
        assertEquals(YamlDeviceProfile(7, "east-pod"), result["east"])
    }

    @Test
    fun responseBodyConverter_parsesYamlSetBody() {
        val factory = GhostYamlConverterFactory.create()
        val retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
        val genericType = ParameterizedYamlHolder::class.java.getMethod("set").genericReturnType
        val converter = factory.responseBodyConverter(genericType, emptyArray(), retrofit)!!

        val yaml = """
            - deviceId: 1
              label: one
        """.trimIndent()

        @Suppress("UNCHECKED_CAST")
        val result = converter.convert(yaml.toResponseBody()) as Set<YamlDeviceProfile>
        assertEquals(setOf(YamlDeviceProfile(1, "one")), result)
    }

    @Test
    fun responseBodyConverter_returnsNullForNonStringKeyMap() {
        val factory = GhostYamlConverterFactory.create()
        val retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
        val genericType = ParameterizedYamlHolder::class.java.getMethod("intKeyMap").genericReturnType
        assertNull(factory.responseBodyConverter(genericType, emptyArray(), retrofit))
    }
}
