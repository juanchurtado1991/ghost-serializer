package com.ghost.serialization

import com.ghost.serialization.contract.GhostSerializer
import kotlin.reflect.KClass

internal actual fun findLinkedSerializer(
    clazz: KClass<*>
): GhostSerializer<*>? = null

internal actual val serializerRegistrationHint: String = ""
