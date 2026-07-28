@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.retrofit

import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.parser.streaming.GhostJsonReader
import com.ghost.serialization.parser.yaml.GhostYamlFlatReader
import com.ghost.serialization.writer.bytes.GhostJsonFlatWriter
import com.ghost.serialization.writer.bytes.GhostJsonWriter
import com.ghost.serialization.writer.yaml.GhostYamlFlatWriter
import com.ghost.serialization.writer.yaml.GhostYamlWriter
import com.ghost.serialization.yaml.contract.GhostYamlSerializer
import kotlin.reflect.KClass

data class YamlDeviceProfile(val deviceId: Int, val label: String)

object YamlDeviceProfileSerializer :
    GhostSerializer<YamlDeviceProfile>,
    GhostYamlSerializer<YamlDeviceProfile> {
    override val typeName: String = "com.ghost.serialization.retrofit.YamlDeviceProfile"

    override fun serialize(writer: GhostJsonWriter, value: YamlDeviceProfile) = Unit
    override fun serialize(writer: GhostJsonFlatWriter, value: YamlDeviceProfile) = Unit
    override fun deserialize(reader: GhostJsonReader): YamlDeviceProfile = YamlDeviceProfile(0, "")

    override fun serialize(writer: GhostYamlWriter, value: YamlDeviceProfile) {
        writer.beginObject()
        writer.name("deviceId")
        writer.value(value.deviceId)
        writer.name("label")
        writer.value(value.label)
        writer.endObject()
    }

    override fun serialize(writer: GhostYamlFlatWriter, value: YamlDeviceProfile) {
        writer.beginObject()
        writer.name("deviceId")
        writer.value(value.deviceId)
        writer.name("label")
        writer.value(value.label)
        writer.endObject()
    }

    override fun deserialize(reader: GhostYamlFlatReader): YamlDeviceProfile {
        var deviceId = 0
        var label = ""
        reader.beginObject()
        while (reader.hasNext()) {
            when (reader.nextKey()) {
                "deviceId" -> deviceId = reader.nextInt()
                "label" -> label = reader.nextString()
                else -> reader.skipValue()
            }
        }
        reader.endObject()
        return YamlDeviceProfile(deviceId, label)
    }
}

object YamlRetrofitTestRegistry : GhostRegistry {
    override fun prewarm() {}
    override fun getAllSerializers(): Map<KClass<*>, GhostSerializer<*>> =
        mapOf(YamlDeviceProfile::class to YamlDeviceProfileSerializer)

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any> getSerializer(clazz: KClass<T>): GhostSerializer<T>? =
        if (clazz == YamlDeviceProfile::class) YamlDeviceProfileSerializer as GhostSerializer<T> else null
}
