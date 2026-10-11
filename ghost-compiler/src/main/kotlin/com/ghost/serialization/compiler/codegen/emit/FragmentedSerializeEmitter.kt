package com.ghost.serialization.compiler.codegen.emit

import com.ghost.serialization.compiler.model.GhostPropertyModel
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C


/**
 * Serializes large classes as multiple private "chunk" functions to avoid JVM method size limits.
 * Nested objects of `@GhostFlatten`/`@GhostWrap` paths may open in one chunk and close in a later one:
 * chunks run in order, so the open-object path is tracked across them at generation time.
 */
internal class FragmentedSerializeEmitter(
    properties: List<GhostPropertyModel>,
    originalClassName: ClassName,
    writerClass: ClassName
) : BaseSerializeEmitter(properties = properties, originalClassName = originalClassName, writerClass = writerClass) {

    fun emit(
        code: CodeBlock.Builder,
        typeSpecBuilder: TypeSpec.Builder,
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

        val chunks = properties.chunked(C.DEFAULT_CHUNK_SIZE)
        val writerSuffix = writerClass.simpleName

        val currentPath = mutableListOf<String>()

        chunks.forEachIndexed { index, chunkProps ->
            val chunkFunName = C.TEMPLATE_CHUNK_FUN_NAME
                .format(C.STR_SERIALIZE_CHUNK_PREFIX, index, writerSuffix)

            emitChunkFunction(index, chunkProps, typeSpecBuilder, writerSuffix, currentPath)
            code.addStatement(C.TEMPLATE_CHUNK_CALL_WRITER, chunkFunName)
        }

        emitClosePath(code = code, currentPath = currentPath)
        code.addStatement(C.STR_WRITER_END_OBJ)
    }

    private fun emitChunkFunction(
        index: Int,
        chunkProps: List<GhostPropertyModel>,
        typeSpecBuilder: TypeSpec.Builder,
        writerSuffix: String,
        currentPath: MutableList<String>
    ) {
        val chunkFun = FunSpec
            .builder(
                C.TEMPLATE_CHUNK_FUN_NAME.format(
                    C.STR_SERIALIZE_CHUNK_PREFIX,
                    index,
                    writerSuffix
                )
            )
            .addModifiers(KModifier.PRIVATE)
            .addParameter(C.STR_PARAM_WRITER, writerClass)
            .addParameter(C.STR_PARAM_VALUE, originalClassName)

        val chunkCode = CodeBlock.builder()
        chunkProps.forEach { prop ->
            emitPathTransition(code = chunkCode, currentPath = currentPath, prop = prop)
            emitProperty(code = chunkCode, prop = prop)
        }

        chunkFun.addCode(chunkCode.build())
        typeSpecBuilder.addFunction(chunkFun.build())
    }
}
