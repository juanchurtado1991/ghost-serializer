package com.ghost.serialization.compiler.codegen.emit

import com.google.devtools.ksp.symbol.KSType
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.ParameterizedTypeName.Companion.parameterizedBy
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import com.squareup.kotlinpoet.ksp.toTypeName
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C

/**
 * Tracks contextual serializers for a generated class and emits the backing private fields.
 * Shared by [BaseSerializeEmitter] and [BaseDeserializeEmitter], each holding its own instance.
 */
internal class ContextualSerializerRegistry {

    private val entries = mutableMapOf<KSType, String>()

    fun nameFor(type: KSType): String {
        return entries.getOrPut(type) {
            val simpleName = type.declaration.simpleName.asString()
            val nullableSuffix = if (type.isMarkedNullable) C.STR_NULLABLE_SUFFIX else ""
            C.STR_CONTEXTUAL_PREFIX +
                    simpleName.replaceFirstChar { it.lowercase() } +
                    nullableSuffix +
                    C.STR_SERIALIZER_SUFFIX
        }
    }

    fun injectInto(typeSpecBuilder: TypeSpec.Builder) {
        val ghostClass = ClassName(C.PKG_GHOST, C.STR_GHOST)

        entries.forEach { (type, name) ->
            val nonNullableType = type.makeNotNullable()
            typeSpecBuilder.addProperty(
                PropertySpec.builder(
                    name,
                    ClassName(C.PKG_CONTRACT, C.STR_GHOST_SERIALIZER)
                        .parameterizedBy(nonNullableType.toTypeName()),
                    KModifier.PRIVATE
                )
                    .initializer(
                        C.TEMPLATE_RESOLVE_SERIALIZER,
                        ghostClass,
                        nonNullableType.toTypeName()
                    )
                    .build()
            )
        }
    }
}
