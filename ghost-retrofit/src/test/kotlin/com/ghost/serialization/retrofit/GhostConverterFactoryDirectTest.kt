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
        Ghost.addRegistry(registry = RetrofitTestRegistry)
        factory = GhostConverterFactory.create()
        retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
    }

    @Test
    fun responseBodyConverter_returnsNullForUnregisteredType() {
        val converter =
            factory.responseBodyConverter(
                type = Unregistered::class.java,
                annotations = emptyArray(),
                retrofit = retrofit
            )
        assertNull(actual = converter)
    }

    @Test
    fun requestBodyConverter_returnsNullForUnregisteredType() {
        val converter = factory.requestBodyConverter(
            type = Unregistered::class.java,
            parameterAnnotations = emptyArray(),
            methodAnnotations = emptyArray(),
            retrofit = retrofit
        )
        assertNull(actual = converter)
    }

    @Test
    fun responseBodyConverter_resolvesSetGenericType() {
        val genericType = SetGenericHolder::class.java.getMethod("set").genericReturnType
        val converter = factory.responseBodyConverter(
            type = genericType,
            annotations = emptyArray(),
            retrofit = retrofit
        )
            ?: error("Expected a converter for Set<RetrofitUser>")

        val body = """[{"id":1,"name":"a","isActive":true}]"""
            .toResponseBody(GhostRetrofitMediaTypes.APPLICATION_JSON_UTF8.toMediaType())
        @Suppress("UNCHECKED_CAST")
        val result = converter.convert(body) as Set<RetrofitUser>
        assertEquals(expected = setOf(RetrofitUser(id = 1, name = "a", isActive = true)), actual = result)
    }

    @Test
    fun responseBodyConverter_resolvesStringKeyMapGenericType() {
        val genericType = MapGenericHolder::class.java.getMethod("stringKey").genericReturnType
        val converter = factory.responseBodyConverter(
            type = genericType,
            annotations = emptyArray(),
            retrofit = retrofit
        )
            ?: error("Expected a converter for Map<String, RetrofitUser>")

        val body = """{"a":{"id":1,"name":"a","isActive":true}}"""
            .toResponseBody(GhostRetrofitMediaTypes.APPLICATION_JSON_UTF8.toMediaType())
        @Suppress("UNCHECKED_CAST")
        val result = converter.convert(body) as Map<String, RetrofitUser>
        assertEquals(expected = RetrofitUser(id = 1, name = "a", isActive = true), actual = result["a"])
    }

    @Test
    fun responseBodyConverter_returnsNullForNonStringKeyMap() {
        val genericType = MapGenericHolder::class.java.getMethod("intKey").genericReturnType
        assertNull(
            actual = factory.responseBodyConverter(type = genericType, annotations = emptyArray(), retrofit = retrofit)
        )
    }

    @Test
    fun responseBodyConverter_returnsNullForUnsupportedNestedGenericType() {
        val genericType = UnsupportedGenericHolder::class.java.getMethod("holder").genericReturnType
        val converter = factory.responseBodyConverter(
            type = genericType,
            annotations = emptyArray(),
            retrofit = retrofit
        )
        assertNull(actual = converter)
    }

    @Test
    fun responseBodyConverter_growsScratchBufferForPayloadsLargerThanInitialSize() {
        val longName = "n".repeat(600_000)
        val json = """{"id":1,"name":"$longName","isActive":true}"""
        val converter =
            factory.responseBodyConverter(
                type = RetrofitUser::class.java,
                annotations = emptyArray(),
                retrofit = retrofit
            )
                ?: error("Expected a converter for a registered type")

        val body = json.toResponseBody(GhostRetrofitMediaTypes.APPLICATION_JSON_UTF8.toMediaType())
        val result = converter.convert(body)

        assertEquals(expected = RetrofitUser(id = 1, name = longName, isActive = true), actual = result)
    }
}
