package com.ghost.serialization.compiler

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.ghost.serialization.compiler.GhostEmitterConstants as C

/**
 * Standard serializer code generator for standard-sized DTOs (typically < 40 properties).
 * Handles structured opening and closing of nested JSON brackets for flattened or wrapped properties.
 */
internal class StandardSerializeEmitter(
    properties: List<GhostPropertyModel>,
    originalClassName: ClassName,
    writerClass: ClassName
) : BaseSerializeEmitter(properties, originalClassName, writerClass) {

    /**
     * Emits the standard object serialization instructions.
     *
     * @param code The target [CodeBlock.Builder].
     * @param discriminator Optional polymorphic sealed class discriminator value.
     * @param sealedDiscriminatorKey Discriminator key name.
     */
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
            val targetPath = prop.flattenPath?.dropLast(1)
                ?: prop.wrapPath
                ?: emptyList()

            // Close objects that are not in the new target path
            while (currentPath.isNotEmpty() && !isPrefix(currentPath, targetPath)) {
                code.addStatement(C.STR_WRITER_END_OBJ)
                currentPath.removeAt(currentPath.size - 1)
            }

            val isStringWriter = writerClass.simpleName == "GhostJsonStringWriter"
            val prefix = if (isStringWriter) "HS_" else C.STR_H_VAL_PREFIX
            // Open new objects in the target path
            targetPath.drop(currentPath.size).forEach { segment ->
                code.addStatement(
                    C.STR_WRITER_WRITE_NAME_VAL,
                    prefix + segment.uppercase()
                )
                code.addStatement(C.STR_WRITER_BEGIN_OBJ)
                currentPath.add(segment)
            }

            emitProperty(code, prop)
        }

        // Close remaining open objects
        while (currentPath.isNotEmpty()) {
            code.addStatement(C.STR_WRITER_END_OBJ)
            currentPath.removeAt(currentPath.size - 1)
        }
        code.addStatement(C.STR_WRITER_END_OBJ)
    }

    /**
     * Checks if a path prefix is a subset of the full path.
     */
    private fun isPrefix(prefix: List<String>, full: List<String>): Boolean {
        if (prefix.size > full.size) {
            return false
        }
        for (i in prefix.indices) {
            if (prefix[i] != full[i]) {
                return false
            }
        }
        return true
    }
}
