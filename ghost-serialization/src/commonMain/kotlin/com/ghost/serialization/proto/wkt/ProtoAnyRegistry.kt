@file:OptIn(InternalGhostApi::class)

package com.ghost.serialization.proto.wkt

import com.ghost.serialization.Ghost
import com.ghost.serialization.InternalGhostApi
import com.ghost.serialization.contract.GhostRegistry
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

    /** The [KClass] registered for [typeUrl], or `null` if none was registered. */
    fun classFor(typeUrl: String): KClass<*>? = classByTypeUrl[typeUrl]

    /**
     * Serializes [message] and wraps it in a [ProtoAny] using the `typeUrl` registered for
     * [kClass] via [register].
     *
     * @throws IllegalArgumentException if no `typeUrl` was registered for [kClass], or if
     *   [kClass] has no [GhostSerializer] registered with [registry] (e.g. missing
     *   `@GhostProtoSerialization`/`@GhostSerialization`).
     */
    fun <T : Any> pack(
        message: T,
        kClass: KClass<T>,
        registry: GhostRegistry = Ghost
    ): ProtoAny {
        val typeUrl = typeUrlByClass[kClass] ?: Ghost.throwError(
            message = "No typeUrl registered for ${kClass.simpleName}. " +
                    "Call ProtoAnyRegistry.register<${kClass.simpleName}>(typeUrl) first."
        )
        val serializer = registry.getSerializer(clazz = kClass)
            ?: Ghost.throwError(message = "${Ghost.NOT_FOUND} ${kClass.simpleName}. ${Ghost.MISSING_ANN}")
        val bytes = Ghost.encodeToBytes(serializer = serializer, value = message)
        return ProtoAny(typeUrl = typeUrl, value = bytes)
    }

    inline fun <reified T : Any> pack(
        message: T,
        registry: GhostRegistry = Ghost
    ): ProtoAny = pack(message = message, kClass = T::class, registry = registry)

    /** Registers the `typeUrl` a [ProtoAny] should carry for messages of type [kClass]. */
    fun register(typeUrl: String, kClass: KClass<*>) {
        typeUrlByClass[kClass] = typeUrl
        classByTypeUrl[typeUrl] = kClass
    }

    inline fun <reified T : Any> register(typeUrl: String) {
        register(typeUrl = typeUrl, kClass = T::class)
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

    /** The `typeUrl` registered for [kClass], or `null` if none was registered. */
    fun typeUrlFor(kClass: KClass<*>): String? = typeUrlByClass[kClass]

    /**
     * Decodes the payload captured in [any] as [kClass], using the [GhostSerializer] registered
     * with [registry] for that type.
     *
     * When [kClass] or [any]'s `typeUrl` is registered via [register], verifies that the wire
     * `typeUrl` matches [kClass]; a mismatch throws. Use [unpackDynamic] when the target type is
     * only known from the wire.
     *
     * @throws IllegalArgumentException if [kClass] has no [GhostSerializer] registered with
     *   [registry], or if a registered `typeUrl` does not match [kClass].
     */
    fun <T : Any> unpack(
        any: ProtoAny,
        kClass: KClass<T>,
        registry: GhostRegistry = Ghost
    ): T {
        val expectedTypeUrl = typeUrlFor(kClass = kClass)
        if (expectedTypeUrl != null && any.typeUrl != expectedTypeUrl) {
            Ghost.throwError(
                message = "ProtoAny typeUrl mismatch: expected '$expectedTypeUrl' for ${kClass.simpleName}, " +
                        "got '${any.typeUrl}'"
            )
        }
        val registeredClass = classFor(typeUrl = any.typeUrl)
        if (registeredClass != null && registeredClass != kClass) {
            Ghost.throwError(
                message = "ProtoAny typeUrl '${any.typeUrl}' is registered for ${registeredClass.simpleName}, " +
                        "not ${kClass.simpleName}"
            )
        }
        val serializer = registry.getSerializer(clazz = kClass)
            ?: Ghost.throwError(message = "${Ghost.NOT_FOUND} ${kClass.simpleName}. ${Ghost.MISSING_ANN}")
        return ghostProtoInternalUseFlatReader(bytes = any.value) { reader ->
            serializer.deserialize(reader = reader)
        }
    }

    inline fun <reified T : Any> unpack(
        any: ProtoAny,
        registry: GhostRegistry = Ghost
    ): T = unpack(any = any, kClass = T::class, registry = registry)

    /**
     * Resolves [any]'s Kotlin type purely from its `typeUrl` (via [register]) and decodes the
     * payload, without the caller needing to know the target type at compile time.
     *
     * @return The decoded message, or `null` if no [KClass] was registered for `any.typeUrl`.
     */
    fun unpackDynamic(
        any: ProtoAny,
        registry: GhostRegistry = Ghost
    ): Any? {
        val kClass = classByTypeUrl[any.typeUrl] ?: return null
        val serializer = registry.getSerializer(clazz = kClass) ?: return null
        return ghostProtoInternalUseFlatReader(bytes = any.value) { reader ->
            serializer.deserialize(reader = reader)
        }
    }
}
