package com.ghost.serialization.compiler.codegen.emit

import com.ghost.serialization.compiler.model.GhostPropertyModel
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C


/**
 * Serializer for standard-sized DTOs (typically < 40 properties); handles opening/closing
 * nested JSON brackets for flattened or wrapped properties.
 */
internal class StandardSerializeEmitter(
    properties: List<GhostPropertyModel>,
    originalClassName: ClassName,
    writerClass: ClassName
) : BaseSerializeEmitter(properties = properties, originalClassName = originalClassName, writerClass = writerClass) {

    fun emit(
        code: CodeBlock.Builder,
        discriminator: String?,
        sealedDiscriminatorKey: String
    ) {
        code.addStatement(C.STR_WRITER_BEGIN_OBJ)

        val hasDiscriminatorProperty = properties.any { it.jsonName == sealedDiscriminatorKey }
        if (discriminator != null && !hasDiscriminatorProperty) {
            code.addStatement(
                C.STR_WRITER_NAME_TYPE_VAL,
                sealedDiscriminatorKey,
                discriminator
            )
        }

        val currentPath = mutableListOf<String>()
        properties.forEach { prop ->
            emitPathTransition(code = code, currentPath = currentPath, prop = prop)
            emitProperty(code = code, prop = prop)
        }
        emitClosePath(code = code, currentPath = currentPath)
        code.addStatement(C.STR_WRITER_END_OBJ)
    }
}
