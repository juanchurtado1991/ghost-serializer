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
        fun map(): Map<String, ProtoDeviceEvent>
    }

    private data class Unregistered(val x: Int)

    @BeforeEach
    fun setup() {
        Ghost.addRegistry(ProtoRetrofitTestRegistry)
        factory = GhostProtoConverterFactory.create()
        retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
    }

    @Test
    fun responseBodyConverter_returnsNullForUnregisteredType() {
        assertNull(factory.responseBodyConverter(Unregistered::class.java, emptyArray(), retrofit))
    }

    @Test
    fun requestBodyConverter_returnsNullForUnregisteredType() {
        assertNull(
            factory.requestBodyConverter(Unregistered::class.java, emptyArray(), emptyArray(), retrofit)
        )
    }

    @Test
    fun responseBodyConverter_resolvesParameterizedListType() {
        val genericType = ParameterizedHolder::class.java.getMethod("list").genericReturnType
        val converter = factory.responseBodyConverter(genericType, emptyArray(), retrofit)
            ?: error("Expected List<ProtoDeviceEvent> converter")

        val json = """[{"deviceId":"1","label":"a"},{"deviceId":"2","label":"b"}]"""
        val body = json.toResponseBody("application/json; charset=UTF-8".toMediaType())
        val result = converter.convert(body) as List<ProtoDeviceEvent>

        assertEquals(2, result.size)
        assertEquals(ProtoDeviceEvent(1L, "a"), result[0])
    }

    @Test
    fun responseBodyConverter_resolvesParameterizedMapType() {
        val genericType = ParameterizedHolder::class.java.getMethod("map").genericReturnType
        val converter = factory.responseBodyConverter(genericType, emptyArray(), retrofit)
            ?: error("Expected Map converter")

        val json = """{"alpha":{"deviceId":"10","label":"A"},"beta":{"deviceId":"20","label":"B"}}"""
        val body = json.toResponseBody("application/json; charset=UTF-8".toMediaType())
        @Suppress("UNCHECKED_CAST")
        val result = converter.convert(body) as Map<String, ProtoDeviceEvent>

        assertEquals(ProtoDeviceEvent(10L, "A"), result["alpha"])
        assertEquals(ProtoDeviceEvent(20L, "B"), result["beta"])
    }

    @Test
    fun responseBodyConverter_parsesEmptyListBody() {
        val genericType = ParameterizedHolder::class.java.getMethod("list").genericReturnType
        val converter = factory.responseBodyConverter(genericType, emptyArray(), retrofit)!!

        val result = converter.convert("[]".toResponseBody()) as List<*>
        assertEquals(0, result.size)
    }

    @Test
    fun responseBodyConverter_parsesBareInt64InsideListElements() {
        val genericType = ParameterizedHolder::class.java.getMethod("list").genericReturnType
        val converter = factory.responseBodyConverter(genericType, emptyArray(), retrofit)!!

        val json = """[{"deviceId":9223372036854775807,"label":"max"}]"""
        val result = converter.convert(json.toResponseBody()) as List<ProtoDeviceEvent>
        assertEquals(Long.MAX_VALUE, result.single().deviceId)
    }

    @Test
    fun requestBodyConverter_serializesListWithQuotedInt64() {
        val genericType = ParameterizedHolder::class.java.getMethod("list").genericReturnType
        val converter = factory.requestBodyConverter(
            genericType,
            emptyArray(),
            emptyArray(),
            retrofit,
        ) as Converter<List<ProtoDeviceEvent>, RequestBody>

        val body = converter.convert(
            listOf(ProtoDeviceEvent(deviceId = 99L, label = "batch")),
        )!!
        assertEquals("""[{"deviceId":"99","label":"batch"}]""", Buffer().apply { body.writeTo(this) }.readUtf8())
    }

    @Test
    fun responseBodyConverter_growsScratchBufferForPayloadsLargerThanInitialSize() {
        val longLabel = "n".repeat(600_000)
        val json = """{"deviceId":"42","label":"$longLabel"}"""
        val converter = factory.responseBodyConverter(ProtoDeviceEvent::class.java, emptyArray(), retrofit)
            ?: error("Expected a converter for a registered type")

        val body = json.toResponseBody("application/json; charset=UTF-8".toMediaType())
        val result = converter.convert(body)

        assertEquals(ProtoDeviceEvent(42L, longLabel), result)
    }
}
