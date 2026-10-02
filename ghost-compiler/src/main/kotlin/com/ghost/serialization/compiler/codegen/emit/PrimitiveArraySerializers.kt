package com.ghost.serialization.compiler.codegen.emit

import com.squareup.kotlinpoet.ClassName
import com.ghost.serialization.compiler.internal.GhostCommonConstants as CC
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C

/**
 * The serializer class a generated serializer delegates a primitive-array property (`IntArray`, ...)
 * to: the YAML array serializer when [channelClass] is a YAML reader/writer, the JSON one otherwise.
 */
internal fun primitiveArraySerializerClass(channelClass: ClassName, primitiveArrayType: String?): ClassName {
    val isYamlChannel = channelClass.simpleName.startsWith(C.STR_GHOST_YAML_PREFIX)
    return if (isYamlChannel) {
        ClassName(CC.PKG_YAML_SERIALIZER, C.TEMPLATE_YAML_ARRAY_SERIALIZER.format(primitiveArrayType))
    } else {
        ClassName(CC.STR_SERIALIZERS_PKG, "$primitiveArrayType${CC.STR_SERIALIZER_SUFFIX}")
    }
}
