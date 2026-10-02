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
        Ghost.addRegistry(registry = YamlRetrofitTestRegistry)
    }

    @Test
    fun responseBodyConverter_returnsNullForJsonOnlySerializer() {
        val factory = GhostYamlConverterFactory.create()
        val retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
        val converter =
            factory.responseBodyConverter(
                type = ProtoDeviceEvent::class.java,
                annotations = emptyArray(),
                retrofit = retrofit
            )
        assertNull(actual = converter)
    }

    @Test
    fun responseBodyConverter_parsesYamlPayload() {
        val factory = GhostYamlConverterFactory.create()
        val retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
        val converter =
            factory.responseBodyConverter(
                type = YamlDeviceProfile::class.java,
                annotations = emptyArray(),
                retrofit = retrofit
            )
                ?: error("converter should not be null")

        val yaml = """
            deviceId: 42
            label: sensor-1
        """.trimIndent()
        val result = converter.convert(yaml.toResponseBody())
        assertEquals(expected = YamlDeviceProfile(deviceId = 42, label = "sensor-1"), actual = result)
    }

    @Test
    fun responseBodyConverter_returnsNullForListOfNonYamlSerializer() {
        val factory = GhostYamlConverterFactory.create()
        val retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
        val genericType = JsonOnlyListHolder::class.java.getMethod("list").genericReturnType
        assertNull(
            actual = factory.responseBodyConverter(type = genericType, annotations = emptyArray(), retrofit = retrofit)
        )
    }

    @Test
    fun responseBodyConverter_parsesYamlListBody() {
        val factory = GhostYamlConverterFactory.create()
        val retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
        val genericType = ParameterizedYamlHolder::class.java.getMethod("list").genericReturnType
        val converter = factory.responseBodyConverter(
            type = genericType,
            annotations = emptyArray(),
            retrofit = retrofit
        )!!

        val yaml = """
            - deviceId: 1
              label: one
            - deviceId: 2
              label: two
        """.trimIndent()

        @Suppress("UNCHECKED_CAST")
        val result = converter.convert(yaml.toResponseBody()) as List<YamlDeviceProfile>
        assertEquals(expected = 2, actual = result.size)
        assertEquals(expected = "one", actual = result[0].label)
    }

    @Test
    fun responseBodyConverter_parsesYamlMapBody() {
        val factory = GhostYamlConverterFactory.create()
        val retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
        val genericType = ParameterizedYamlHolder::class.java.getMethod("map").genericReturnType
        val converter = factory.responseBodyConverter(
            type = genericType,
            annotations = emptyArray(),
            retrofit = retrofit
        )!!

        val yaml = """
            east:
              deviceId: 7
              label: east-pod
        """.trimIndent()

        @Suppress("UNCHECKED_CAST")
        val result = converter.convert(yaml.toResponseBody()) as Map<String, YamlDeviceProfile>
        assertEquals(expected = YamlDeviceProfile(deviceId = 7, label = "east-pod"), actual = result["east"])
    }

    @Test
    fun responseBodyConverter_parsesYamlSetBody() {
        val factory = GhostYamlConverterFactory.create()
        val retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
        val genericType = ParameterizedYamlHolder::class.java.getMethod("set").genericReturnType
        val converter = factory.responseBodyConverter(
            type = genericType,
            annotations = emptyArray(),
            retrofit = retrofit
        )!!

        val yaml = """
            - deviceId: 1
              label: one
        """.trimIndent()

        @Suppress("UNCHECKED_CAST")
        val result = converter.convert(yaml.toResponseBody()) as Set<YamlDeviceProfile>
        assertEquals(expected = setOf(YamlDeviceProfile(deviceId = 1, label = "one")), actual = result)
    }

    @Test
    fun responseBodyConverter_returnsNullForNonStringKeyMap() {
        val factory = GhostYamlConverterFactory.create()
        val retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
        val genericType = ParameterizedYamlHolder::class.java.getMethod("intKeyMap").genericReturnType
        assertNull(
            actual = factory.responseBodyConverter(type = genericType, annotations = emptyArray(), retrofit = retrofit)
        )
    }
}
