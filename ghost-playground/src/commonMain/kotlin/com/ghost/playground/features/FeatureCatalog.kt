package com.ghost.playground.features

import com.ghost.playground.ui.icons.PlaygroundIconKind
import com.ghost.serialization.Ghost
import com.ghost.serialization.decodeFromYaml
import com.ghost.serialization.encodeToYaml
import com.ghost.serialization.proto.GhostProto

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
                import com.ghost.serialization.annotations.GhostSerialization

                @GhostSerialization
                data class PlaygroundUser(
                    val id: Long,
                    val name: String,
                    val email: String? = null,
                )
            """.trimIndent(),
            fieldNames = listOf("id", "name", "email"),
            variants = listOf(
                LabVariant(
                    id = "full",
                    labelEn = "Full profile",
                    labelEs = "Perfil completo",
                    json = """{"id":7,"name":"Neo","email":"neo@matrix.io"}"""
                ),
                LabVariant(
                    id = "nullEmail",
                    labelEn = "Null email",
                    labelEs = "Email nulo",
                    json = """{"id":8,"name":"Trinity","email":null}"""
                ),
                LabVariant(
                    id = "missingOptional",
                    labelEn = "Missing optional field",
                    labelEs = "Campo opcional ausente",
                    json = """{"id":9,"name":"Morpheus"}"""
                ),
                LabVariant(
                    id = "unicode",
                    labelEn = "Unicode name",
                    labelEs = "Nombre con unicode",
                    json = """{"id":10,"name":"Niobe 🚀","email":"niobe@zion.io"}"""
                ),
                LabVariant(
                    id = "largeId",
                    labelEn = "Max-size id",
                    labelEs = "Id al máximo",
                    json = """{"id":9223372036854775807,"name":"Architect","email":"architect@matrix.io"}"""
                ),
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
                import com.ghost.serialization.annotations.GhostResilient
                import com.ghost.serialization.annotations.GhostSerialization

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
                LabVariant(
                    id = "bothWrong",
                    labelEn = "Both fields wrong type",
                    labelEs = "Ambos campos con tipo incorrecto",
                    json = """{"theme":123,"retryCount":"nope"}"""
                ),
                LabVariant(
                    id = "themeWrong",
                    labelEn = "Wrong theme type",
                    labelEs = "Tipo incorrecto en theme",
                    json = """{"theme":true,"retryCount":5}"""
                ),
                LabVariant(
                    id = "retryWrong",
                    labelEn = "Wrong retryCount type",
                    labelEs = "Tipo incorrecto en retryCount",
                    json = """{"theme":"dark","retryCount":"lots"}"""
                ),
                LabVariant(
                    id = "retryNull",
                    labelEn = "Explicit null for a non-nullable field",
                    labelEs = "Null explícito en un campo no-nullable",
                    json = """{"theme":"ok","retryCount":null}"""
                ),
                LabVariant(
                    id = "bothValid",
                    labelEn = "Both fields valid",
                    labelEs = "Ambos campos válidos",
                    json = """{"theme":"dark","retryCount":10}"""
                ),
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
                import com.ghost.serialization.annotations.GhostFlatten
                import com.ghost.serialization.annotations.GhostSerialization

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
                LabVariant(
                    id = "london",
                    labelEn = "London office",
                    labelEs = "Oficina en Londres",
                    json = """{"name":"Ada","address":{"city":"London","zip":"EC2"}}"""
                ),
                LabVariant(
                    id = "us",
                    labelEn = "US address",
                    labelEs = "Dirección en EE.UU.",
                    json = """{"name":"Grace","address":{"city":"Arlington","zip":"22203"}}"""
                ),
                LabVariant(
                    id = "cambridge",
                    labelEn = "Cambridge",
                    labelEs = "Cambridge",
                    json = """{"name":"Alan","address":{"city":"Cambridge","zip":"CB2"}}"""
                ),
                LabVariant(
                    id = "unicode",
                    labelEn = "Unicode city name",
                    labelEs = "Ciudad con unicode",
                    json = """{"name":"José","address":{"city":"São Paulo","zip":"01310-100"}}"""
                ),
                LabVariant(
                    id = "numericZip",
                    labelEn = "Numeric-looking zip",
                    labelEs = "Zip con apariencia numérica",
                    json = """{"name":"Katherine","address":{"city":"Hampton","zip":"23666"}}"""
                ),
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
                import com.ghost.serialization.annotations.GhostFallback
                import com.ghost.serialization.annotations.GhostSerialization

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
                LabVariant(
                    id = "future",
                    labelEn = "Unknown type",
                    labelEs = "Tipo desconocido",
                    json = """{"type":"FutureEvent","payload":true}"""
                ),
                LabVariant(
                    id = "legacy",
                    labelEn = "Legacy ping",
                    labelEs = "Ping legado",
                    json = """{"type":"LegacyPing","payload":"hello"}"""
                ),
                LabVariant(
                    id = "empty",
                    labelEn = "Empty type",
                    labelEs = "Tipo vacío",
                    json = """{"type":"","payload":null}"""
                ),
                LabVariant(
                    id = "nested",
                    labelEn = "Nested payload",
                    labelEs = "Payload anidado",
                    json = """{"type":"SensorAlert","payload":{"level":"critical","code":42}}"""
                ),
                LabVariant(
                    id = "versioned",
                    labelEn = "Versioned type",
                    labelEs = "Tipo versionado",
                    json = """{"type":"v2.event","payload":123}"""
                ),
            ),
            run = { json ->
                Ghost.deserialize<DeviceEvent>(json).toString()
            },
            explainEn = { input, out ->
                val type = extractJsonStringField(json = input, key = "type") ?: "?"
                "type=$type is unknown — @GhostFallback returned Unknown instead of failing. $out"
            },
            explainEs = { input, out ->
                val type = extractJsonStringField(json = input, key = "type") ?: "?"
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
                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.types.RawJson

                @GhostSerialization
                data class EnvelopePayload(
                    val event: String,
                    val meta: RawJson,
                )
            """.trimIndent(),
            fieldNames = listOf("event", "meta"),
            variants = listOf(
                LabVariant(
                    id = "ping",
                    labelEn = "Ping event",
                    labelEs = "Evento ping",
                    json = """{"event":"ping","meta":{"trace":"abc","n":1}}"""
                ),
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
            wireFormat = LabWireFormat.PROTO_JSON,
            introEn = "@GhostProtoSerialization applies proto3 JSON rules (quoted int64, Base64 bytes, default omission). Works with @GhostName and @GhostIgnore; add @GhostYamlSerialization for YAML. Not compatible with @GhostFlatten, @GhostJsonEnvelope, RawJson, or sealed discriminators — use @GhostWrappedKeys for oneof.",
            introEs = "@GhostProtoSerialization aplica reglas proto3 JSON (int64 entre comillas, bytes Base64, omisión de defaults). Compatible con @GhostName y @GhostIgnore; añade @GhostYamlSerialization para YAML. No combina con @GhostFlatten, @GhostJsonEnvelope, RawJson ni sealed con discriminador — usa @GhostWrappedKeys para oneof.",
            dtoSource = """
                import com.ghost.serialization.annotations.GhostProtoSerialization

                @GhostProtoSerialization
                data class ProtoOrderEvent(
                    val orderId: Long,
                    val label: String,
                    val retries: Int = 0,
                )
            """.trimIndent(),
            fieldNames = listOf("orderId", "label", "retries"),
            variants = listOf(
                LabVariant(
                    id = "restock",
                    labelEn = "Restock order",
                    labelEs = "Orden de reposición",
                    json = """{"orderId":"5001","label":"restock"}"""
                ),
                LabVariant(
                    id = "withRetries",
                    labelEn = "Non-default retries",
                    labelEs = "Retries distinto del default",
                    json = """{"orderId":"5002","label":"priority","retries":3}"""
                ),
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
        FeatureLab(
            id = "yamlRoundtrip",
            icon = PlaygroundIconKind.RoundTrip,
            titleEn = "YAML",
            titleEs = "YAML",
            wireFormat = LabWireFormat.YAML,
            introEn = "Add @GhostYamlSerialization beside @GhostSerialization to opt in to YAML codegen — the same DTO can round-trip YAML documents without a second schema.",
            introEs = "Añade @GhostYamlSerialization junto a @GhostSerialization para activar codegen YAML — el mismo DTO puede hacer round-trip de documentos YAML sin un segundo schema.",
            dtoSource = """
                import com.ghost.serialization.annotations.GhostSerialization
                import com.ghost.serialization.annotations.GhostYamlSerialization

                @GhostSerialization
                @GhostYamlSerialization
                data class PlaygroundUser(
                    val id: Long,
                    val name: String,
                    val email: String? = null,
                )
            """.trimIndent(),
            fieldNames = listOf("id", "name", "email"),
            variants = listOf(
                LabVariant(
                    id = "full",
                    labelEn = "Full profile",
                    labelEs = "Perfil completo",
                    json = """
                    id: 7
                    name: Neo
                    email: neo@matrix.io
                    """.trimIndent(),
                ),
                LabVariant(
                    id = "missingOptional",
                    labelEn = "Missing optional field",
                    labelEs = "Campo opcional ausente",
                    json = """
                    id: 9
                    name: Morpheus
                    """.trimIndent(),
                ),
                LabVariant(
                    id = "nullEmail",
                    labelEn = "Null email",
                    labelEs = "Email nulo",
                    json = """
                    id: 8
                    name: Trinity
                    email: null
                    """.trimIndent(),
                ),
            ),
            run = { yaml ->
                val user = Ghost.decodeFromYaml<PlaygroundUser>(yaml)
                Ghost.encodeToYaml(value = user)
            },
            explainEn = { _, out ->
                "Ghost parsed YAML with the generated GhostYamlFlatReader and wrote YAML again with GhostYamlWriter. Output: $out"
            },
            explainEs = { _, out ->
                "Ghost parseó YAML con GhostYamlFlatReader generado y reescribió YAML con GhostYamlWriter. Salida: $out"
            },
        ),
    )

    /** Best-effort JSON string-field extractor for this catalog's sample payloads; not a general parser. */
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
