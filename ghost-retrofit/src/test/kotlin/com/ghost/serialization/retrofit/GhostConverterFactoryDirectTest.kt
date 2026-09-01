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
 * Direct unit tests for [GhostConverterFactory], bypassing MockWebServer. Covers the
 * null-return contract for unsupported types and scratch-buffer growth (payload > 512 KB),
 * which [GhostRetrofitTest]/[GhostRetrofitExpansionTest] don't exercise.
 */
class GhostConverterFactoryDirectTest {

    private lateinit var retrofit: Retrofit
    private lateinit var factory: GhostConverterFactory

    private interface SetGenericHolder {
        fun set(): Set<RetrofitUser>
    }

    private interface MapGenericHolder {
        fun stringKey(): Map<String, RetrofitUser>
        fun intKey(): Map<Int, RetrofitUser>
    }

    private class UnsupportedHolder<T>

    private interface UnsupportedGenericHolder {
        fun holder(): UnsupportedHolder<RetrofitUser>
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
        val converter =
            factory.responseBodyConverter(Unregistered::class.java, emptyArray(), retrofit)
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
    fun responseBodyConverter_resolvesSetGenericType() {
        val genericType = SetGenericHolder::class.java.getMethod("set").genericReturnType
        val converter = factory.responseBodyConverter(genericType, emptyArray(), retrofit)
            ?: error("Expected a converter for Set<RetrofitUser>")

        val body = """[{"id":1,"name":"a","isActive":true}]"""
            .toResponseBody("application/json; charset=UTF-8".toMediaType())
        @Suppress("UNCHECKED_CAST")
        val result = converter.convert(body) as Set<RetrofitUser>
        assertEquals(setOf(RetrofitUser(1, "a", true)), result)
    }

    @Test
    fun responseBodyConverter_resolvesStringKeyMapGenericType() {
        val genericType = MapGenericHolder::class.java.getMethod("stringKey").genericReturnType
        val converter = factory.responseBodyConverter(genericType, emptyArray(), retrofit)
            ?: error("Expected a converter for Map<String, RetrofitUser>")

        val body = """{"a":{"id":1,"name":"a","isActive":true}}"""
            .toResponseBody("application/json; charset=UTF-8".toMediaType())
        @Suppress("UNCHECKED_CAST")
        val result = converter.convert(body) as Map<String, RetrofitUser>
        assertEquals(RetrofitUser(1, "a", true), result["a"])
    }

    @Test
    fun responseBodyConverter_returnsNullForNonStringKeyMap() {
        val genericType = MapGenericHolder::class.java.getMethod("intKey").genericReturnType
        assertNull(factory.responseBodyConverter(genericType, emptyArray(), retrofit))
    }

    @Test
    fun responseBodyConverter_returnsNullForUnsupportedNestedGenericType() {
        val genericType = UnsupportedGenericHolder::class.java.getMethod("holder").genericReturnType
        val converter = factory.responseBodyConverter(genericType, emptyArray(), retrofit)
        assertNull(converter)
    }

    @Test
    fun responseBodyConverter_growsScratchBufferForPayloadsLargerThanInitialSize() {
        val longName = "n".repeat(600_000)
        val json = """{"id":1,"name":"$longName","isActive":true}"""
        val converter =
            factory.responseBodyConverter(RetrofitUser::class.java, emptyArray(), retrofit)
                ?: error("Expected a converter for a registered type")

        val body = json.toResponseBody("application/json; charset=UTF-8".toMediaType())
        val result = converter.convert(body)

        assertEquals(RetrofitUser(1, longName, true), result)
    }
}
