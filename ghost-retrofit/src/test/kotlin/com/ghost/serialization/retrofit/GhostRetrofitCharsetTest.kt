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

/** A response body declared in a non-UTF-8 charset is decoded with it instead of being read as UTF-8. */
class GhostRetrofitCharsetTest {

    private val expected = RetrofitUser(id = 1, name = "José Ñandú €", isActive = true)
    private val json = "{\"id\":1,\"name\":\"José Ñandú €\",\"isActive\":true}"

    private lateinit var retrofit: Retrofit

    @BeforeEach
    fun setup() {
        Ghost.addRegistry(registry = RetrofitTestRegistry)
        retrofit = Retrofit.Builder().baseUrl("http://localhost/").build()
    }

    private fun decode(
        charsetName: String,
        bytes: ByteArray
    ): Any? {
        val converter = GhostConverterFactory.create().responseBodyConverter(
            type = RetrofitUser::class.java,
            annotations = emptyArray(),
            retrofit = retrofit
        ) ?: error("Expected a converter for RetrofitUser")
        val body = bytes.toResponseBody(contentType = "application/json; charset=$charsetName".toMediaType())
        return converter.convert(body)
    }

    @Test
    fun decodesIso88591Body() {
        assertEquals(
            expected = expected.copy(name = "José Ñandú ?"),
            actual = decode(
                charsetName = "ISO-8859-1",
                bytes = json.toByteArray(charset = Charsets.ISO_8859_1)
            )
        )
    }

    @Test
    fun decodesWindows1252Body() {
        assertEquals(
            expected = expected,
            actual = decode(
                charsetName = "windows-1252",
                bytes = json.toByteArray(charset = charset("windows-1252"))
            )
        )
    }

    @Test
    fun decodesUtf16Body() {
        assertEquals(
            expected = expected,
            actual = decode(
                charsetName = "UTF-16",
                bytes = json.toByteArray(charset = Charsets.UTF_16)
            )
        )
    }

    @Test
    fun decodesUtf8Body() {
        assertEquals(
            expected = expected,
            actual = decode(
                charsetName = "UTF-8",
                bytes = json.toByteArray(charset = Charsets.UTF_8)
            )
        )
    }
}
