package com.ghost.playground.features

import com.ghost.playground.ui.icons.PlaygroundIconKind
import com.ghost.serialization.proto.GhostProto
import com.ghost.serialization.Ghost

object FeatureCatalog {
    val labs: List<FeatureLab> = listOf(
        FeatureLab(
            id = "ghostSerialization",
            icon = PlaygroundIconKind.RoundTrip,
            titleEn = "@GhostSerialization",
            titleEs = "@GhostSerialization",
            introEn = "Ghost turns JSON into a typed Kotlin value and back — using generated code, not reflection.",
            introEs = "Ghost convierte JSON en un valor Kotlin tipado y viceversa — con código generado, sin reflexión.",
            dtoSource = """
                @GhostSerialization
                data class PlaygroundUser(
                    val id: Long,
                    val name: String,
                    val email: String? = null,
                )
            """.trimIndent(),
            fieldNames = listOf("id", "name", "email"),
            variants = listOf(
                LabVariant("full", "Full profile", "Perfil completo", """{"id":7,"name":"Neo","email":"neo@matrix.io"}"""),
                LabVariant("nullEmail", "Null email", "Email nulo", """{"id":8,"name":"Trinity","email":null}"""),
                LabVariant("missingOptional", "Missing optional field", "Campo opcional ausente", """{"id":9,"name":"Morpheus"}"""),
                LabVariant("unicode", "Unicode name", "Nombre con unicode", """{"id":10,"name":"Niobe 🚀","email":"niobe@zion.io"}"""),
                LabVariant("largeId", "Max-size id", "Id al máximo", """{"id":9223372036854775807,"name":"Architect","email":"architect@matrix.io"}"""),
            ),
            run = { json ->
                val user = Ghost.deserialize<PlaygroundUser>(json)
                Ghost.encodeToString(user)
            },
            explainEn = { _, out ->
                "Ghost parsed each field via the generated serializer, then wrote JSON again using precomputed field headers. Output: $out"
            },
            explainEs = { _, out ->
                "Ghost parseó cada campo con el serializer generado y reescribió JSON con headers precomputados. Salida: $out"
            },
        ),
        FeatureLab(
            id = "resilient",
            icon = PlaygroundIconKind.Shield,
            titleEn = "@GhostResilient",
            titleEs = "@GhostResilient",
            introEn = "Wrong types in JSON? Ghost keeps your defaults instead of crashing the whole parse.",
            introEs = "¿Tipos incorrectos en JSON? Ghost conserva tus defaults en vez de tumbar todo el parse.",
            dtoSource = """
                @GhostSerialization
                data class ResilientConfig(
                    @GhostResilient
                    val theme: String? = null,
                    @GhostResilient
                    val retryCount: Int = 3,
                )
            """.trimIndent(),
            fieldNames = listOf("theme", "retryCount"),
            variants = listOf(
                LabVariant("bothWrong", "Both fields wrong type", "Ambos campos con tipo incorrecto", """{"theme":123,"retryCount":"nope"}"""),
                LabVariant("themeWrong", "Wrong theme type", "Tipo incorrecto en theme", """{"theme":true,"retryCount":5}"""),
                LabVariant("retryWrong", "Wrong retryCount type", "Tipo incorrecto en retryCount", """{"theme":"dark","retryCount":"lots"}"""),
                LabVariant("retryNull", "Explicit null for a non-nullable field", "Null explícito en un campo no-nullable", """{"theme":"ok","retryCount":null}"""),
                LabVariant("bothValid", "Both fields valid", "Ambos campos válidos", """{"theme":"dark","retryCount":10}"""),
            ),
            run = { json ->
                val cfg = Ghost.deserialize<ResilientConfig>(json)
                "theme=${cfg.theme}, retryCount=${cfg.retryCount}"
            },
            explainEn = { _, out ->
                "@GhostResilient keeps the field's default for any value with the wrong JSON type (or missing/null) instead of failing the whole parse. Result: $out"
            },
            explainEs = { _, out ->
                "@GhostResilient conserva el default del campo ante un tipo incorrecto (o valor ausente/null) en vez de tumbar todo el parse. Resultado: $out"
            },
        ),
        FeatureLab(
            id = "flatten",
            icon = PlaygroundIconKind.Flatten,
            titleEn = "@GhostFlatten",
            titleEs = "@GhostFlatten",
            introEn = "Nested JSON paths map straight into flat DTO properties — no wrapper classes.",
            introEs = "Paths JSON anidados mapean a props planas del DTO — sin clases wrapper.",
            dtoSource = """
                @GhostSerialization
                data class FlattenedPerson(
                    val name: String,
                    @GhostFlatten("address.city")
                    val city: String,
                    @GhostFlatten("address.zip")
                    val zip: String,
                )
            """.trimIndent(),
            fieldNames = listOf("name", "city", "zip"),
            variants = listOf(
                LabVariant("london", "London office", "Oficina en Londres", """{"name":"Ada","address":{"city":"London","zip":"EC2"}}"""),
                LabVariant("us", "US address", "Dirección en EE.UU.", """{"name":"Grace","address":{"city":"Arlington","zip":"22203"}}"""),
                LabVariant("cambridge", "Cambridge", "Cambridge", """{"name":"Alan","address":{"city":"Cambridge","zip":"CB2"}}"""),
                LabVariant("unicode", "Unicode city name", "Ciudad con unicode", """{"name":"José","address":{"city":"São Paulo","zip":"01310-100"}}"""),
                LabVariant("numericZip", "Numeric-looking zip", "Zip con apariencia numérica", """{"name":"Katherine","address":{"city":"Hampton","zip":"23666"}}"""),
            ),
            run = { json ->
                val person = Ghost.deserialize<FlattenedPerson>(json)
                Ghost.encodeToString(person)
            },
            explainEn = { _, out ->
                "address.city and address.zip were read from the nested object into city/zip fields, then re-encoded: $out"
            },
            explainEs = { _, out ->
                "address.city y address.zip se leyeron del objeto anidado hacia city/zip y se re-encodearon: $out"
            },
        ),
        FeatureLab(
            id = "fallback",
            icon = PlaygroundIconKind.Fallback,
            titleEn = "@GhostFallback",
            titleEs = "@GhostFallback",
            introEn = "Unknown sealed-class discriminators route to a safe fallback type instead of throwing.",
            introEs = "Discriminadores desconocidos en sealed class van a un fallback seguro en vez de explotar.",
            dtoSource = """
                @GhostSerialization
                sealed class DeviceEvent {
                    @GhostSerialization
                    data class Status(val ok: Boolean) : DeviceEvent()

                    @GhostFallback
                    @GhostSerialization
                    data class Unknown(val raw: String = "unknown") : DeviceEvent()
                }
            """.trimIndent(),
            fieldNames = emptyList(),
            variants = listOf(
                LabVariant("future", "Unknown type", "Tipo desconocido", """{"type":"FutureEvent","payload":true}"""),
                LabVariant("legacy", "Legacy ping", "Ping legado", """{"type":"LegacyPing","payload":"hello"}"""),
                LabVariant("empty", "Empty type", "Tipo vacío", """{"type":"","payload":null}"""),
                LabVariant("nested", "Nested payload", "Payload anidado", """{"type":"SensorAlert","payload":{"level":"critical","code":42}}"""),
                LabVariant("versioned", "Versioned type", "Tipo versionado", """{"type":"v2.event","payload":123}"""),
            ),
            run = { json ->
                Ghost.deserialize<DeviceEvent>(json).toString()
            },
            explainEn = { input, out ->
                val type = extractJsonStringField(input, "type") ?: "?"
                "type=$type is unknown — @GhostFallback returned Unknown instead of failing. $out"
            },
            explainEs = { input, out ->
                val type = extractJsonStringField(input, "type") ?: "?"
                "type=$type es desconocido — @GhostFallback devolvió Unknown. $out"
            },
        ),
        FeatureLab(
            id = "rawjson",
            icon = PlaygroundIconKind.Package,
            titleEn = "RawJson",
            titleEs = "RawJson",
            introEn = "Capture opaque JSON as bytes — no intermediate tree, perfect for passthrough fields.",
            introEs = "Captura JSON opaco como bytes — sin árbol intermedio, ideal para passthrough.",
            dtoSource = """
                @GhostSerialization
                data class EnvelopePayload(
                    val event: String,
                    val meta: RawJson,
                )
            """.trimIndent(),
            fieldNames = listOf("event", "meta"),
            variants = listOf(
                LabVariant("ping", "Ping event", "Evento ping", """{"event":"ping","meta":{"trace":"abc","n":1}}"""),
            ),
            run = { json ->
                val env = Ghost.deserialize<EnvelopePayload>(json)
                "event=${env.event}, meta=${env.meta.decodeToString()}"
            },
            explainEn = { _, out ->
                "The meta object was captured verbatim as RawJson bytes without building a Map/List tree. $out"
            },
            explainEs = { _, out ->
                "El objeto meta se capturó verbatim como bytes RawJson sin armar Map/List. $out"
            },
        ),
        FeatureLab(
            id = "protojson",
            icon = PlaygroundIconKind.Bytes,
            titleEn = "Proto-JSON",
            titleEs = "Proto-JSON",
            introEn = "@GhostProtoSerialization follows proto3 JSON rules: int64 fields round-trip as quoted strings, and fields left at their default are dropped from the output.",
            introEs = "@GhostProtoSerialization sigue las reglas de proto3 JSON: los campos int64 van y vuelven como strings entre comillas, y los campos en su valor default se omiten del output.",
            dtoSource = """
                @GhostProtoSerialization
                data class ProtoOrderEvent(
                    val orderId: Long,
                    val label: String,
                    val retries: Int = 0,
                )
            """.trimIndent(),
            fieldNames = listOf("orderId", "label", "retries"),
            variants = listOf(
                LabVariant("restock", "Restock order", "Orden de reposición", """{"orderId":"5001","label":"restock","retries":0}"""),
            ),
            run = { json ->
                val event = GhostProto.deserialize<ProtoOrderEvent>(json)
                GhostProto.encodeToString(event)
            },
            explainEn = { _, out ->
                "orderId stayed a quoted string (proto3 int64 convention) and retries=0 (the default) was dropped from the output: $out"
            },
            explainEs = { _, out ->
                "orderId se mantuvo como string entre comillas (convención int64 de proto3) y retries=0 (el default) se omitió del output: $out"
            },
        ),
    )

    /** Tiny best-effort extractor for this catalog's own hand-written sample JSON — not a general parser. */
    private fun extractJsonStringField(json: String, key: String): String? {
        val marker = "\"$key\""
        val keyIndex = json.indexOf(marker)
        if (keyIndex < 0) return null
        val colonIndex = json.indexOf(':', keyIndex + marker.length)
        if (colonIndex < 0) return null
        val quoteStart = json.indexOf('"', colonIndex + 1)
        if (quoteStart < 0) return null
        val quoteEnd = json.indexOf('"', quoteStart + 1)
        if (quoteEnd < 0) return null
        return json.substring(quoteStart + 1, quoteEnd)
    }
}
