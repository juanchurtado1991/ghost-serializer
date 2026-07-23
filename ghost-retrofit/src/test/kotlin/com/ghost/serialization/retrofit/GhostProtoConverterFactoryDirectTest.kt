@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Direct unit tests for [GhostProtoConverterFactory] -- mirrors
 * [GhostConverterFactoryDirectTest]. Unlike the plain factory, this one only supports direct
 * `Class<*>` types (no `List`/`Map` unwrapping), so a `ParameterizedType` request is itself an
 * "unsupported type" case worth covering, not just an unregistered `Class`.
 */
class GhostProtoConverterFactoryDirectTest {

    private lateinit var retrofit: Retrofit
    private lateinit var factory: GhostProtoConverterFactory

    private interface ParameterizedHolder {
        fun list(): List<ProtoDeviceEvent>
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
    fun responseBodyConverter_returnsNullForParameterizedType() {
        // This factory only supports direct Class<*> types -- a ParameterizedType (List<T>)
        // isn't unwrapped like GhostConverterFactory does.
        val genericType = ParameterizedHolder::class.java.getMethod("list").genericReturnType
        assertNull(factory.responseBodyConverter(genericType, emptyArray(), retrofit))
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
