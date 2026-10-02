package com.ghost.serialization

import com.ghost.serialization.contract.GhostSerializer
import com.ghost.serialization.contract.GhostSerializerLink
import kotlin.reflect.ExperimentalAssociatedObjects
import kotlin.reflect.KClass
import kotlin.reflect.findAssociatedObject

@OptIn(ExperimentalAssociatedObjects::class)
internal actual fun findLinkedSerializer(
    clazz: KClass<*>
): GhostSerializer<*>? = clazz.findAssociatedObject<GhostSerializerLink>() as? GhostSerializer<*>
