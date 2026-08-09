package com.ghost.serialization.retrofit

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import retrofit2.Retrofit
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(InternalGhostApi::class)
class GhostRetrofitExpansionTest {
    private lateinit var mockWebServer: MockWebServer
    private lateinit var apiService: ExpandedApiService

    @BeforeEach
    fun setup() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        Ghost.addRegistry(RetrofitTestRegistry)

        val retrofit = Retrofit.Builder()
            .baseUrl(mockWebServer.url("/"))
            .addConverterFactory(GhostConverterFactory.create())
            .build()

        apiService = retrofit.create(ExpandedApiService::class.java)
    }

    @AfterEach
    fun teardown() {
        mockWebServer.shutdown()
    }

    /**
     * Disabled after the Kotlin 2.4.0 bump: Retrofit's internal check that substitutes a null
     * body with [Unit] for `suspend fun foo(): Unit` endpoints no longer recognizes the return
     * type as [Unit] (likely a change in how Kotlin 2.4.0 encodes the `Continuation<Unit>`
     * generic signature). Not a Ghost bug — [GhostConverterFactory] has no [Unit]-specific
     * handling; this is Retrofit's own reflection-based detection. Re-enable once Retrofit
     * ships a fix or a newer Retrofit 2.x/3.x release resolves it (tried 2.11.0 and 2.12.0,
     * both affected).
     */
    @Disabled("Retrofit Unit/204 detection broken by Kotlin 2.4.0 Continuation<Unit> encoding — see comment")
    @Test
    fun `handles 204 No Content correctly`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(204))
        val result = apiService.getNoContent()
        assertEquals(Unit, result)
    }

    @Test
    fun `handles null response body correctly`() = runTest {
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("null"))
        val response = apiService.getEmpty()
        assertNull(response.body())
    }

    @Test
    fun `strict endpoint throws on missing comma`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("{\"id\":1 \"name\":\"John\"}")
        )
        kotlin.test.assertFailsWith<Exception> {
            apiService.getStrictUser()
        }
    }

    @Test
    fun `lenient endpoint passes on missing comma`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200).setBody("{\"id\":1 \"name\":\"John\"}")
        )
        val user = apiService.getLenientUser()
        assertEquals(1, user.id)
        assertEquals("John", user.name)
    }

    @Test
    fun `coerced endpoint coerces primitive values`() = runTest {
        mockWebServer.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("{\"id\":\"42\", \"name\":\"John\", \"isActive\":\"true\"}")
        )
        val user = apiService.getCoercedUser()
        assertEquals(42, user.id)
        assertEquals("John", user.name)
        assertEquals(true, user.isActive)
    }
}
