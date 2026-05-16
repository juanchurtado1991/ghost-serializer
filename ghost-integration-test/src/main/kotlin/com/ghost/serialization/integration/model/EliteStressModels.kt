@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.integration.model

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.annotations.*
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.GhostJsonReader
import com.ghost.serialization.parser.nextString
import com.ghost.serialization.parser.nextInt
import com.ghost.serialization.parser.consumeNull
import com.ghost.serialization.parser.isNextNullValue
import com.ghost.serialization.writer.GhostJsonFlatWriter
import com.ghost.serialization.writer.GhostJsonWriter

@GhostSerialization
data class CollisionChild(
    val name: String,
    val value: Int
)

@GhostSerialization
data class StructuralCollisionModel(
    val name: String, // Collides with child.name
    @GhostFlatten("meta")
    val child: CollisionChild
)

@GhostSerialization
data class WrapSharedPathModel(
    @GhostWrap("metadata.info")
    val name: String,
    @GhostWrap("metadata.auth")
    val token: String,
    @GhostWrap("system.flags.active")
    val active: Boolean
)

@GhostSerialization
data class CoercionStressModel(
    val b1: Boolean,
    val b2: Boolean,
    val b3: Boolean,
    val b4: Boolean,
    val b5: Boolean,
    val b6: Boolean
)

@GhostSerialization
data class DeepResilientModel(
    val id: String,
    val list: List<ResilientItem>
)

@GhostResilient
@GhostSerialization
data class ResilientItem(
    val id: String,
    @GhostResilient
    val value: Int? = null
)

@GhostSerialization
data class CustomCoderStressModel(
    val id: String,
    @GhostDecoder(EliteUtils::class, "decodeHex")
    @GhostEncoder(EliteUtils::class, "encodeHex")
    val secret: String,
    @GhostDecoder(EliteUtils::class, "decodeNullableInt")
    val score: Int?
)

object EliteUtils {
    fun decodeHex(reader: GhostJsonReader): String {
        val hex = reader.nextString()
        return "HEX:$hex"
    }
    fun encodeHex(writer: GhostJsonWriter, value: String) {
        writer.value(value.removePrefix("HEX:"))
    }
    fun encodeHex(writer: GhostJsonFlatWriter, value: String) {
        writer.value(value.removePrefix("HEX:"))
    }
    fun decodeNullableInt(reader: GhostJsonReader): Int? {
        if (reader.isNextNullValue()) {
            reader.consumeNull()
            return -1 // Return a magic number instead of null to test decoder logic
        }
        return reader.nextInt()
    }
}

data class ExternalColor(val r: Int, val g: Int, val b: Int)

@InternalGhostApi
object ExternalColorSerializer : GhostSerializer<ExternalColor> {
    override val typeName: String = "ExternalColor"

    override fun serialize(writer: GhostJsonWriter, value: ExternalColor) {
        serializeInternal(writer, value)
    }

    override fun serialize(writer: GhostJsonFlatWriter, value: ExternalColor) {
        serializeInternal(writer, value)
    }

    private fun serializeInternal(writer: Any, value: ExternalColor) {
        val hex = "#%02x%02x%02x".format(value.r, value.g, value.b)
        if (writer is GhostJsonWriter) writer.value(hex)
        else if (writer is GhostJsonFlatWriter) writer.value(hex)
    }

    override fun deserialize(reader: GhostJsonReader): ExternalColor {
        val hex = reader.nextString().removePrefix("#")
        val r = hex.substring(0, 2).toInt(16)
        val g = hex.substring(2, 4).toInt(16)
        val b = hex.substring(4, 6).toInt(16)
        return ExternalColor(r, g, b)
    }
}

@GhostSerialization
data class ContextualModel(
    val id: String,
    val color: ExternalColor // This will require a contextual registry
)
