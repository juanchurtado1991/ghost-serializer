@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.proto.ghostProtoInternalUseFlatReader
import com.ghost.serialization.proto.wkt.ProtoAnyRegistry.pack
import com.ghost.serialization.proto.wkt.ProtoAnyRegistry.register
import com.ghost.serialization.proto.wkt.ProtoAnyRegistry.unpack
import com.ghost.serialization.proto.wkt.ProtoAnyRegistry.unpackDynamic
import kotlin.reflect.KClass


/**
 * Maps `typeUrl` strings to the Kotlin types packed inside a [ProtoAny], so `pack`/`unpack`
 * work without the caller manually juggling bytes.
 *
 * Independent of [Ghost]'s serializer registry — the type still needs a `GhostSerializer`
 * registered there too (e.g. via `@GhostProtoSerialization` + KSP). This registry only
 * remembers which `typeUrl` corresponds to which [KClass].
 *
 * ```kotlin
 * ProtoAnyRegistry.register<DeviceRebooted>("type.googleapis.com/myapp.DeviceRebooted")
 *
 * val any: ProtoAny = ProtoAnyRegistry.pack(DeviceRebooted(deviceId = 1))
 * val event: DeviceRebooted = ProtoAnyRegistry.unpack(any)
 * val dynamic: Any? = ProtoAnyRegistry.unpackDynamic(any) // resolved purely from any.typeUrl
 * ```
 */
object ProtoAnyRegistry {

    private val typeUrlByClass = mutableMapOf<KClass<*>, String>()
    private val classByTypeUrl = mutableMapOf<String, KClass<*>>()

    /** Registers the `typeUrl` a [ProtoAny] should carry for messages of type [kClass]. */
    fun register(typeUrl: String, kClass: KClass<*>) {
        typeUrlByClass[kClass] = typeUrl
        classByTypeUrl[typeUrl] = kClass
    }

    /** Reified convenience for [register]. */
    inline fun <reified T : Any> register(typeUrl: String) {
        register(typeUrl, T::class)
    }

    /** The `typeUrl` registered for [kClass], or `null` if none was registered. */
    fun typeUrlFor(kClass: KClass<*>): String? = typeUrlByClass[kClass]

    /** The [KClass] registered for [typeUrl], or `null` if none was registered. */
    fun classFor(typeUrl: String): KClass<*>? = classByTypeUrl[typeUrl]

    /**
     * Serializes [message] and wraps it in a [ProtoAny] using the `typeUrl` registered for
     * [kClass] via [register].
     *
     * @throws IllegalArgumentException if no `typeUrl` was registered for [kClass], or if
     *   [kClass] has no [GhostSerializer] registered with [Ghost] (e.g. missing
     *   `@GhostProtoSerialization`/`@GhostSerialization`).
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> pack(message: T, kClass: KClass<T>): ProtoAny {
        val typeUrl = typeUrlByClass[kClass]
            ?: Ghost.throwError(
                "No typeUrl registered for ${kClass.simpleName}. " +
                        "Call ProtoAnyRegistry.register<${kClass.simpleName}>(typeUrl) first."
            )
        val serializer = Ghost.getSerializer(kClass) as? GhostSerializer<T>
            ?: Ghost.throwError("${Ghost.NOT_FOUND} ${kClass.simpleName}. ${Ghost.MISSING_ANN}")
        val bytes = Ghost.encodeToBytes(serializer, message)
        return ProtoAny(typeUrl, bytes)
    }

    /** Reified convenience for [pack]. */
    inline fun <reified T : Any> pack(message: T): ProtoAny = pack(message, T::class)

    /**
     * Decodes the payload captured in [any] as [kClass], using the [GhostSerializer] registered
     * with [Ghost] for that type.
     *
     * When [kClass] or [any]'s `typeUrl` is registered via [register], verifies that the wire
     * `typeUrl` matches [kClass]; a mismatch throws. Use [unpackDynamic] when the target type is
     * only known from the wire.
     *
     * @throws IllegalArgumentException if [kClass] has no [GhostSerializer] registered with [Ghost],
     *   or if a registered `typeUrl` does not match [kClass].
     */
    @Suppress("UNCHECKED_CAST")
    fun <T : Any> unpack(any: ProtoAny, kClass: KClass<T>): T {
        val expectedTypeUrl = typeUrlFor(kClass)
        if (expectedTypeUrl != null && any.typeUrl != expectedTypeUrl) {
            Ghost.throwError(
                "ProtoAny typeUrl mismatch: expected '$expectedTypeUrl' for ${kClass.simpleName}, " +
                        "got '${any.typeUrl}'"
            )
        }
        val registeredClass = classFor(any.typeUrl)
        if (registeredClass != null && registeredClass != kClass) {
            Ghost.throwError(
                "ProtoAny typeUrl '${any.typeUrl}' is registered for ${registeredClass.simpleName}, " +
                        "not ${kClass.simpleName}"
            )
        }
        val serializer = Ghost.getSerializer(kClass) as? GhostSerializer<T>
            ?: Ghost.throwError("${Ghost.NOT_FOUND} ${kClass.simpleName}. ${Ghost.MISSING_ANN}")
        return ghostProtoInternalUseFlatReader(any.value) { reader ->
            serializer.deserialize(reader)
        }
    }

    /** Reified convenience for [unpack]. */
    inline fun <reified T : Any> unpack(any: ProtoAny): T = unpack(any, T::class)

    /**
     * Resolves [any]'s Kotlin type purely from its `typeUrl` (via [register]) and decodes the
     * payload, without the caller needing to know the target type at compile time.
     *
     * @return The decoded message, or `null` if no [KClass] was registered for `any.typeUrl`.
     */
    fun unpackDynamic(any: ProtoAny): Any? {
        val kClass = classByTypeUrl[any.typeUrl] ?: return null
        val serializer = Ghost.getSerializer(kClass) ?: return null
        return ghostProtoInternalUseFlatReader(any.value) { reader ->
            serializer.deserialize(reader)
        }
    }

    /**
     * Test hook: clears all registered typeUrl/KClass mappings to prevent cross-test pollution.
     * Not for production use.
     */
    @InternalGhostApi
    fun resetForTest() {
        typeUrlByClass.clear()
        classByTypeUrl.clear()
    }
}
