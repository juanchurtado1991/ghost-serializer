@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.AbstractGhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.streaming.beginObject
import com.ghost.serialization.parser.streaming.consumeKeySeparator
import com.ghost.serialization.parser.streaming.endObject
import com.ghost.serialization.parser.streaming.nextKey
import com.ghost.serialization.parser.streaming.nextString
import com.ghost.serialization.parser.streaming.skipValue
import com.ghost.serialization.writer.bytes.GhostJsonWriter

@InternalGhostApi
object RetrofitAnimalSerializer : AbstractGhostSerializer<RetrofitAnimal>() {
    override val typeName: String = "com.ghost.serialization.retrofit.RetrofitAnimal"

    override fun serialize(writer: GhostJsonWriter, value: RetrofitAnimal) {
        writer.beginObject()
        writer.name(key = "name")
        writer.value(value.name)
        writer.endObject()
    }

    override fun deserialize(reader: GhostJsonReader): RetrofitAnimal {
        var name = ""
        reader.beginObject()
        while (true) {
            val key = reader.nextKey() ?: break
            reader.consumeKeySeparator()
            if (key == "name") name = reader.nextString() else reader.skipValue()
        }
        reader.endObject()
        return RetrofitAnimal(name = name)
    }
}
