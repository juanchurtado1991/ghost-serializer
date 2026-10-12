@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.exception.GhostJsonException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** Empty and multi-megabyte response bodies on the JSON, YAML and proto converters. */
class GhostRetrofitBodySizeTest {

    private val bigText = "n".repeat(3_000_000)
    private lateinit var retrofit: Retrofit

    @BeforeEach
    fun setup() {
        Ghost.addRegistry(registry = RetrofitTestRegistry)
        Ghost.addRegistry(registry = YamlRetrofitTestRegistry)
        Ghost.addRegistry(registry = ProtoRetrofitTestRegistry)
        retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
    }

    private fun responseConverter(
        factory: retrofit2.Converter.Factory,
        type: Class<*>
    ) = factory.responseBodyConverter(type, emptyArray(), retrofit) ?: error("Expected a converter for ${type.simpleName}")

    @Test
    fun jsonEmptyBodyThrowsGhostJsonException() {
        val converter = responseConverter(factory = GhostConverterFactory.create(), type = RetrofitUser::class.java)
        assertFailsWith<GhostJsonException> { converter.convert("".toResponseBody()) }
    }

    @Test
    fun jsonBlankBodyThrowsGhostJsonException() {
        val converter = responseConverter(factory = GhostConverterFactory.create(), type = RetrofitUser::class.java)
        assertFailsWith<GhostJsonException> { converter.convert("   ".toResponseBody()) }
    }

    @Test
    fun jsonNullBodyIsNull() {
        val converter = responseConverter(factory = GhostConverterFactory.create(), type = RetrofitUser::class.java)
        assertNull(actual = converter.convert("null".toResponseBody()))
    }

    @Test
    fun jsonLargeBodyRoundTrips() {
        val converter = responseConverter(factory = GhostConverterFactory.create(), type = RetrofitUser::class.java)
        val body = "{\"id\":1,\"name\":\"$bigText\",\"isActive\":true}".toResponseBody()
        assertEquals(
            expected = RetrofitUser(id = 1, name = bigText, isActive = true),
            actual = converter.convert(body)
        )
    }

    @Test
    fun protoEmptyBodyThrowsGhostJsonException() {
        val converter = responseConverter(factory = GhostProtoConverterFactory.create(), type = ProtoDeviceEvent::class.java)
        assertFailsWith<GhostJsonException> { converter.convert("".toResponseBody()) }
    }

    @Test
    fun yamlLargeBodyRoundTrips() {
        val converter = responseConverter(factory = GhostYamlConverterFactory.create(), type = YamlDeviceProfile::class.java)
        val body = "deviceId: 1\nlabel: $bigText".toResponseBody()
        assertEquals(
            expected = YamlDeviceProfile(deviceId = 1, label = bigText),
            actual = converter.convert(body)
        )
    }

    @Test
    fun yamlEmptyBodyReadsAsAnEmptyMapping() {
        val converter = responseConverter(factory = GhostYamlConverterFactory.create(), type = YamlDeviceProfile::class.java)
        assertEquals(
            expected = converter.convert("{}".toResponseBody()),
            actual = converter.convert("".toResponseBody())
        )
    }
}
