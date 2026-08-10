package com.ghost.benchmark

import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonAdapter
import com.squareup.moshi.JsonReader
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.Moshi
import com.squareup.moshi.ToJson

/** Moshi adapter for [IntArray] fields (codegen does not cover primitive arrays). */
internal object IntArrayJsonAdapter : JsonAdapter<IntArray>() {

    @FromJson
    override fun fromJson(reader: JsonReader): IntArray {
        val values = mutableListOf<Int>()
        reader.beginArray()
        while (reader.hasNext()) {
            values.add(reader.nextInt())
        }
        reader.endArray()
        return values.toIntArray()
    }

    @ToJson
    override fun toJson(writer: JsonWriter, value: IntArray?) {
        if (value == null) {
            writer.nullValue()
            return
        }
        writer.beginArray()
        for (element in value) {
            writer.value(element.toLong())
        }
        writer.endArray()
    }
}

/** Builds a Moshi instance with adapters required by integration-test benchmark models. */
internal fun createBenchmarkMoshi(): Moshi {
    return Moshi.Builder()
        .add(IntArray::class.java, IntArrayJsonAdapter)
        .build()
}
