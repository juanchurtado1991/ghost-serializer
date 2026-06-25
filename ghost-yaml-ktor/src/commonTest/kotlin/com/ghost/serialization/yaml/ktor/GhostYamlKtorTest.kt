package com.ghost.serialization.yaml.ktor

import com.ghost.serialization.Ghost
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.yaml.writer.GhostYamlWriter
import com.ghost.serialization.yaml.writer.GhostYamlFlatWriter
import com.ghost.serialization.yaml.parser.GhostYamlFlatReader
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import com.ghost.serialization.decodeFromYaml
import com.ghost.serialization.encodeToYaml
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest

import kotlin.reflect.KClass
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private const val YAML_CONTENT_TYPE = "$CONTENT_TYPE_APPLICATION/$CONTENT_TYPE_YAML"

data class KtorYamlUser(val id: Int, val name: String, val isActive: Boolean)

object KtorYamlUserSerializer : GhostSerializer<KtorYamlUser>, GhostYamlSerializer<KtorYamlUser> {
    override val typeName: String = "com.ghost.serialization.yaml.ktor.KtorYamlUser"

    override fun serialize(writer: com.ghost.serialization.writer.GhostJsonWriter, value: KtorYamlUser) {
    }

    override fun serialize(writer: com.ghost.serialization.writer.GhostJsonFlatWriter, value: KtorYamlUser) {
    }

    override fun deserialize(reader: com.ghost.serialization.parser.GhostJsonReader): KtorYamlUser {
        return KtorYamlUser(0, "", false)
    }

    override fun serialize(sink: okio.BufferedSink, value: KtorYamlUser) {
        super<GhostYamlSerializer>.serialize(sink, value)
    }

    override fun deserialize(source: okio.BufferedSource): KtorYamlUser {
        return super<GhostYamlSerializer>.deserialize(source)
    }

    override fun serialize(writer: GhostYamlWriter, value: KtorYamlUser) {
        writer.beginObject()
        writer.name("id")
        writer.value(value.id)
        writer.name("name")
        writer.value(value.name)
        writer.name("isActive")
        writer.value(value.isActive)
        writer.endObject()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: KtorYamlUser) {
        writer.beginObject()
        writer.name("id")
        writer.value(value.id)
        writer.name("name")
        writer.value(value.name)
        writer.name("isActive")
        writer.value(value.isActive)
        writer.endObject()
    }

    override fun deserialize(reader: GhostYamlFlatReader): KtorYamlUser {
        var id = 0
        var name = ""
        var isActive = false
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextKey() ?: break
            when (key) {
                "id" -> id = reader.nextInt()
                "name" -> name = reader.nextString()
                "isActive" -> isActive = reader.nextBoolean()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return KtorYamlUser(id, name, isActive)
    }
}

class GhostYamlKtorTest {

    @BeforeTest
    fun setup() {
        Ghost.addRegistry(object : GhostRegistry {
            override fun prewarm() {}
            override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> {
                return mapOf(KtorYamlUser::class to KtorYamlUserSerializer)
            }

            @Suppress("UNCHECKED_CAST")
            override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? {
                if (clazz == KtorYamlUser::class) return KtorYamlUserSerializer as GhostSerializer<T>
                return null
            }
        })
    }

    @Test
    fun testSuccessfulYamlSerializationAndDeserialization() = runTest {
        val mockEngine = MockEngine { request ->
            assertEquals("/user", request.url.encodedPath)
            respond(
                content = """
                    id: 42
                    name: "John"
                    isActive: true
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, YAML_CONTENT_TYPE)
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                ghostYaml()
            }
        }

        val response: KtorYamlUser = client.get("/user").body()
        assertEquals(42, response.id)
        assertEquals("John", response.name)
        assertEquals(true, response.isActive)
    }

    @Test
    fun testYamlSerializationOfRequestBody() = runTest {
        val mockEngine = MockEngine { request ->
            val bodyText = when (val body = request.body) {
                is io.ktor.http.content.TextContent -> body.text
                is io.ktor.http.content.OutgoingContent.ByteArrayContent -> body.bytes().decodeToString()
                else -> error("Unsupported body type: ${body::class}")
            }
            assertTrue(bodyText.contains("id: 100"))
            assertTrue(bodyText.contains("name: \"Alice\"") || bodyText.contains("name: Alice"))
            assertTrue(bodyText.contains("isActive: false"))
            respond(
                content = bodyText,
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, YAML_CONTENT_TYPE)
            )
        }

        val client = HttpClient(mockEngine) {
            install(ContentNegotiation) {
                ghostYaml()
            }
        }

        val response: KtorYamlUser = client.post("/user") {
            contentType(ContentType(CONTENT_TYPE_APPLICATION, CONTENT_TYPE_YAML))
            setBody(KtorYamlUser(100, "Alice", false))
        }.body()

        assertEquals(100, response.id)
    }

    @Test
    fun testGhostExtensionsWork() {
        val user = KtorYamlUser(99, "Bob", true)
        val yaml = Ghost.encodeToYaml(user)
        val decoded = Ghost.decodeFromYaml<KtorYamlUser>(yaml)
        assertEquals(user, decoded)
    }
}
