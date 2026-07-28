package com.ghost.serialization.yaml.serializer

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.serializers.ListSerializer
import com.ghost.serialization.serializers.MapSerializer
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer

object GhostYamlIntArraySerializer : GhostYamlSerializer<IntArray> {
    override fun serialize(writer: GhostYamlWriter, value: IntArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: IntArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun deserialize(reader: GhostYamlFlatReader): IntArray {
        reader.beginArray()
        val list = ArrayList<Int>()
        while (reader.hasNextArrayElement()) {
            list.add(reader.nextInt())
        }
        reader.endArray()
        return list.toIntArray()
    }
}

object GhostYamlLongArraySerializer : GhostYamlSerializer<LongArray> {
    override fun serialize(writer: GhostYamlWriter, value: LongArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: LongArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun deserialize(reader: GhostYamlFlatReader): LongArray {
        reader.beginArray()
        val list = ArrayList<Long>()
        while (reader.hasNextArrayElement()) {
            list.add(reader.nextLong())
        }
        reader.endArray()
        return list.toLongArray()
    }
}

object GhostYamlFloatArraySerializer : GhostYamlSerializer<FloatArray> {
    override fun serialize(writer: GhostYamlWriter, value: FloatArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: FloatArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun deserialize(reader: GhostYamlFlatReader): FloatArray {
        reader.beginArray()
        val list = ArrayList<Float>()
        while (reader.hasNextArrayElement()) {
            list.add(reader.nextFloat())
        }
        reader.endArray()
        return list.toFloatArray()
    }
}

object GhostYamlDoubleArraySerializer : GhostYamlSerializer<DoubleArray> {
    override fun serialize(writer: GhostYamlWriter, value: DoubleArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: DoubleArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun deserialize(reader: GhostYamlFlatReader): DoubleArray {
        reader.beginArray()
        val list = ArrayList<Double>()
        while (reader.hasNextArrayElement()) {
            list.add(reader.nextDouble())
        }
        reader.endArray()
        return list.toDoubleArray()
    }
}

object GhostYamlBooleanArraySerializer : GhostYamlSerializer<BooleanArray> {
    override fun serialize(writer: GhostYamlWriter, value: BooleanArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: BooleanArray) {
        writer.beginArray()
        val size = value.size
        var idx = 0
        while (idx < size) {
            writer.value(value[idx])
            idx++
        }
        writer.endArray()
    }

    override fun deserialize(reader: GhostYamlFlatReader): BooleanArray {
        reader.beginArray()
        val list = ArrayList<Boolean>()
        while (reader.hasNextArrayElement()) {
            list.add(reader.nextBoolean())
        }
        reader.endArray()
        return list.toBooleanArray()
    }
}

/**
 * YAML list body serializer — used by Retrofit/Ktor YAML adapters for `List<T>` endpoints when
 * [itemSerializer] also implements [GhostYamlSerializer].
 */
class GhostYamlListSerializer<T>(
    private val itemSerializer: GhostSerializer<T>,
) : GhostSerializer<List<T>>, GhostYamlSerializer<List<T>> {

    init {
        require(itemSerializer is GhostYamlSerializer<*>) {
            "GhostYamlListSerializer requires a GhostYamlSerializer item serializer, got ${itemSerializer.typeName}"
        }
    }

    private val yamlItem: GhostYamlSerializer<T>
        get() = itemSerializer as GhostYamlSerializer<T>

    private val jsonList = ListSerializer(itemSerializer)

    override val typeName: String
        get() = "List<${itemSerializer.typeName}>"

    override fun serialize(writer: com.ghost.serialization.writer.bytes.GhostJsonWriter, value: List<T>) =
        jsonList.serialize(writer, value)

    override fun serialize(writer: com.ghost.serialization.writer.bytes.GhostJsonFlatWriter, value: List<T>) =
        jsonList.serialize(writer, value)

    override fun serialize(writer: com.ghost.serialization.writer.strings.GhostJsonStringWriter, value: List<T>) =
        jsonList.serialize(writer, value)

    override fun serialize(writer: GhostYamlWriter, value: List<T>) {
        writer.beginArray()
        for (item in value) {
            yamlItem.serialize(writer, item)
        }
        writer.endArray()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: List<T>) {
        writer.beginArray()
        for (item in value) {
            yamlItem.serialize(writer, item)
        }
        writer.endArray()
    }

    override fun deserialize(reader: com.ghost.serialization.parser.streaming.GhostJsonReader): List<T> =
        jsonList.deserialize(reader)

    override fun deserialize(reader: com.ghost.serialization.parser.bytes.GhostJsonFlatReader): List<T> =
        jsonList.deserialize(reader)

    override fun deserialize(reader: com.ghost.serialization.parser.strings.GhostJsonStringReader): List<T> =
        jsonList.deserialize(reader)

    override fun deserialize(reader: GhostYamlFlatReader): List<T> =
        reader.readList { yamlItem.deserialize(reader) }
}

/**
 * YAML map body serializer for `Map<String, V>` endpoints when the value serializer implements
 * [GhostYamlSerializer].
 */
class GhostYamlMapSerializer<V>(
    private val valueSerializer: GhostSerializer<V>,
) : GhostSerializer<Map<String, V>>, GhostYamlSerializer<Map<String, V>> {

    init {
        require(valueSerializer is GhostYamlSerializer<*>) {
            "GhostYamlMapSerializer requires a GhostYamlSerializer value serializer, got ${valueSerializer.typeName}"
        }
    }

    private val yamlValue: GhostYamlSerializer<V>
        get() = valueSerializer as GhostYamlSerializer<V>

    private val jsonMap = MapSerializer(valueSerializer)

    override val typeName: String
        get() = "Map<String, ${valueSerializer.typeName}>"

    override fun serialize(writer: com.ghost.serialization.writer.bytes.GhostJsonWriter, value: Map<String, V>) =
        jsonMap.serialize(writer, value)

    override fun serialize(writer: com.ghost.serialization.writer.bytes.GhostJsonFlatWriter, value: Map<String, V>) =
        jsonMap.serialize(writer, value)

    override fun serialize(writer: com.ghost.serialization.writer.strings.GhostJsonStringWriter, value: Map<String, V>) =
        jsonMap.serialize(writer, value)

    override fun serialize(writer: GhostYamlWriter, value: Map<String, V>) {
        writer.beginObject()
        for ((key, entryValue) in value) {
            writer.name(key)
            yamlValue.serialize(writer, entryValue)
        }
        writer.endObject()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: Map<String, V>) {
        writer.beginObject()
        for ((key, entryValue) in value) {
            writer.name(key)
            yamlValue.serialize(writer, entryValue)
        }
        writer.endObject()
    }

    override fun deserialize(reader: com.ghost.serialization.parser.streaming.GhostJsonReader): Map<String, V> =
        jsonMap.deserialize(reader)

    override fun deserialize(reader: com.ghost.serialization.parser.bytes.GhostJsonFlatReader): Map<String, V> =
        jsonMap.deserialize(reader)

    override fun deserialize(reader: com.ghost.serialization.parser.strings.GhostJsonStringReader): Map<String, V> =
        jsonMap.deserialize(reader)

    override fun deserialize(reader: GhostYamlFlatReader): Map<String, V> =
        reader.readMap({ reader.nextKey()!! }) { yamlValue.deserialize(reader) }
}
