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
 * Direct unit tests for [GhostConverterFactory] -- calls `responseBodyConverter`/
 * `requestBodyConverter` directly with hand-built `Type`/`ResponseBody` values, no
 * `MockWebServer` round trip needed. [GhostRetrofitTest]/[GhostRetrofitExpansionTest] already
 * cover the registered-type happy paths (including `List`/`Map` generics) and null-body/strict/
 * coerce behavior end-to-end; this fills the null-return contract for unsupported types and the
 * scratch-buffer growth path (payload > the 512 KB initial buffer) that weren't covered anywhere.
 */
class GhostConverterFactoryDirectTest {

    private lateinit var retrofit: Retrofit
    private lateinit var factory: GhostConverterFactory

    private interface UnsupportedGenericHolder {
        fun set(): Set<RetrofitUser>
    }

    private data class Unregistered(val x: Int)

    @BeforeEach
    fun setup() {
        Ghost.addRegistry(RetrofitTestRegistry)
        factory = GhostConverterFactory.create()
        retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
    }

    @Test
    fun responseBodyConverter_returnsNullForUnregisteredType() {
        val converter = factory.responseBodyConverter(Unregistered::class.java, emptyArray(), retrofit)
        assertNull(converter)
    }

    @Test
    fun requestBodyConverter_returnsNullForUnregisteredType() {
        val converter = factory.requestBodyConverter(
            Unregistered::class.java,
            emptyArray(),
            emptyArray(),
            retrofit
        )
        assertNull(converter)
    }

    @Test
    fun responseBodyConverter_returnsNullForUnsupportedGenericType() {
        // Set<T> is neither List nor Map -- getSerializerForType has no branch for it.
        val genericType = UnsupportedGenericHolder::class.java.getMethod("set").genericReturnType
        val converter = factory.responseBodyConverter(genericType, emptyArray(), retrofit)
        assertNull(converter)
    }

    @Test
    fun responseBodyConverter_growsScratchBufferForPayloadsLargerThanInitialSize() {
        val longName = "n".repeat(600_000)
        val json = """{"id":1,"name":"$longName","isActive":true}"""
        val converter = factory.responseBodyConverter(RetrofitUser::class.java, emptyArray(), retrofit)
            ?: error("Expected a converter for a registered type")

        val body = json.toResponseBody("application/json; charset=UTF-8".toMediaType())
        val result = converter.convert(body)

        assertEquals(RetrofitUser(1, longName, true), result)
    }
}
