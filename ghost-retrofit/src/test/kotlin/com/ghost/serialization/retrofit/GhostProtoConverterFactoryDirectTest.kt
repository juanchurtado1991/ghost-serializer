@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Converter
import retrofit2.Retrofit
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Direct unit tests for [GhostProtoConverterFactory] — proto3 JSON read path plus
 * `List<T>` / `Map<String, V>` body unwrapping when element serializers are registered.
 */
class GhostProtoConverterFactoryDirectTest {

    private lateinit var retrofit: Retrofit
    private lateinit var factory: GhostProtoConverterFactory

    private interface ParameterizedHolder {
        fun list(): List<ProtoDeviceEvent>
        fun set(): Set<ProtoDeviceEvent>
        fun map(): Map<String, ProtoDeviceEvent>
        fun intKeyMap(): Map<Int, ProtoDeviceEvent>
    }

    private data class Unregistered(val x: Int)

    @BeforeEach
    fun setup() {
        Ghost.addRegistry(registry = ProtoRetrofitTestRegistry)
        factory = GhostProtoConverterFactory.create()
        retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
    }

    @Test
    fun responseBodyConverter_returnsNullForUnregisteredType() {
        assertNull(
            actual = factory.responseBodyConverter(type = Unregistered::class.java, annotations = emptyArray(), retrofit = retrofit)
        )
    }

    @Test
    fun requestBodyConverter_returnsNullForUnregisteredType() {
        assertNull(
            actual = factory.requestBodyConverter(
                type = Unregistered::class.java,
                parameterAnnotations = emptyArray(),
                methodAnnotations = emptyArray(),
                retrofit = retrofit
            )
        )
    }

    @Test
    fun responseBodyConverter_resolvesParameterizedListType() {
        val genericType = ParameterizedHolder::class.java.getMethod("list").genericReturnType
        val converter = factory.responseBodyConverter(
            type = genericType,
            annotations = emptyArray(),
            retrofit = retrofit
        )
            ?: error("Expected List<ProtoDeviceEvent> converter")

        val json = """[{"deviceId":"1","label":"a"},{"deviceId":"2","label":"b"}]"""
        val body = json.toResponseBody(GhostRetrofitMediaTypes.APPLICATION_JSON_UTF8.toMediaType())
        val result = converter.convert(body) as List<ProtoDeviceEvent>

        assertEquals(expected = 2, actual = result.size)
        assertEquals(expected = ProtoDeviceEvent(deviceId = 1L, label = "a"), actual = result[0])
    }

    @Test
    fun responseBodyConverter_resolvesParameterizedMapType() {
        val genericType = ParameterizedHolder::class.java.getMethod("map").genericReturnType
        val converter = factory.responseBodyConverter(
            type = genericType,
            annotations = emptyArray(),
            retrofit = retrofit
        )
            ?: error("Expected Map converter")

        val json =
            """{"alpha":{"deviceId":"10","label":"A"},"beta":{"deviceId":"20","label":"B"}}"""
        val body = json.toResponseBody(GhostRetrofitMediaTypes.APPLICATION_JSON_UTF8.toMediaType())

        @Suppress("UNCHECKED_CAST")
        val result = converter.convert(body) as Map<String, ProtoDeviceEvent>

        assertEquals(expected = ProtoDeviceEvent(deviceId = 10L, label = "A"), actual = result["alpha"])
        assertEquals(expected = ProtoDeviceEvent(deviceId = 20L, label = "B"), actual = result["beta"])
    }

    @Test
    fun responseBodyConverter_resolvesParameterizedSetType() {
        val genericType = ParameterizedHolder::class.java.getMethod("set").genericReturnType
        val converter = factory.responseBodyConverter(
            type = genericType,
            annotations = emptyArray(),
            retrofit = retrofit
        )
            ?: error("Expected Set<ProtoDeviceEvent> converter")

        val json = """[{"deviceId":"1","label":"a"}]"""
        val body = json.toResponseBody(GhostRetrofitMediaTypes.APPLICATION_JSON_UTF8.toMediaType())
        @Suppress("UNCHECKED_CAST")
        val result = converter.convert(body) as Set<ProtoDeviceEvent>
        assertEquals(expected = setOf(ProtoDeviceEvent(deviceId = 1L, label = "a")), actual = result)
    }

    @Test
    fun responseBodyConverter_returnsNullForNonStringKeyMap() {
        val genericType = ParameterizedHolder::class.java.getMethod("intKeyMap").genericReturnType
        assertNull(
            actual = factory.responseBodyConverter(type = genericType, annotations = emptyArray(), retrofit = retrofit)
        )
    }

    @Test
    fun responseBodyConverter_parsesEmptyListBody() {
        val genericType = ParameterizedHolder::class.java.getMethod("list").genericReturnType
        val converter = factory.responseBodyConverter(
            type = genericType,
            annotations = emptyArray(),
            retrofit = retrofit
        )!!

        val result = converter.convert("[]".toResponseBody()) as List<*>
        assertEquals(expected = 0, actual = result.size)
    }

    @Test
    fun responseBodyConverter_parsesBareInt64InsideListElements() {
        val genericType = ParameterizedHolder::class.java.getMethod("list").genericReturnType
        val converter = factory.responseBodyConverter(
            type = genericType,
            annotations = emptyArray(),
            retrofit = retrofit
        )!!

        val json = """[{"deviceId":9223372036854775807,"label":"max"}]"""
        val result = converter.convert(json.toResponseBody()) as List<ProtoDeviceEvent>
        assertEquals(expected = Long.MAX_VALUE, actual = result.single().deviceId)
    }

    @Test
    fun requestBodyConverter_serializesListWithQuotedInt64() {
        val genericType = ParameterizedHolder::class.java.getMethod("list").genericReturnType
        val converter = factory.requestBodyConverter(
            type = genericType,
            parameterAnnotations = emptyArray(),
            methodAnnotations = emptyArray(),
            retrofit = retrofit,
        ) as Converter<List<ProtoDeviceEvent>, RequestBody>

        val body = converter.convert(
            listOf(ProtoDeviceEvent(deviceId = 99L, label = "batch")),
        )!!
        assertEquals(
            expected = """[{"deviceId":"99","label":"batch"}]""",
            actual = Buffer().apply { body.writeTo(this) }.readUtf8()
        )
    }

    @Test
    fun responseBodyConverter_growsScratchBufferForPayloadsLargerThanInitialSize() {
        val longLabel = "n".repeat(600_000)
        val json = """{"deviceId":"42","label":"$longLabel"}"""
        val converter =
            factory.responseBodyConverter(
                type = ProtoDeviceEvent::class.java,
                annotations = emptyArray(),
                retrofit = retrofit
            )
                ?: error("Expected a converter for a registered type")

        val body = json.toResponseBody(GhostRetrofitMediaTypes.APPLICATION_JSON_UTF8.toMediaType())
        val result = converter.convert(body)

        assertEquals(expected = ProtoDeviceEvent(deviceId = 42L, label = longLabel), actual = result)
    }
}
