package com.ghost.serialization.compiler

import com.ghost.serialization.compiler.GhostEmitterConstants as C
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.squareup.kotlinpoet.FunSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.TypeSpec

/**
 * Emitter for fragmented serialization logic.
 *
 * Used for large classes to avoid JVM method size limits by splitting
 * the writing logic into multiple private "chunk" functions.
 */
internal class FragmentedSerializeEmitter(
    properties: List<GhostPropertyModel>,
    originalClassName: ClassName,
    writerClass: ClassName
) : BaseSerializeEmitter(properties, originalClassName, writerClass) {

    /**
     * Emits the fragmented chunk-based serialization logic.
     *
     * @param code The target [CodeBlock.Builder].
     * @param typeSpecBuilder The KotlinPoet companion [TypeSpec.Builder].
     * @param discriminator Optional polymorphic sealed class discriminator value.
     * @param sealedDiscriminatorKey Discriminator key name.
     */
    fun emit(
        code: CodeBlock.Builder,
        typeSpecBuilder: TypeSpec.Builder,
        discriminator: String?,
        sealedDiscriminatorKey: String
    ) {
        code.addStatement(C.STR_WRITER_BEGIN_OBJ)

        if (discriminator != null) {
            code.addStatement(
                C.STR_WRITER_NAME_TYPE_VAL,
                sealedDiscriminatorKey,
                discriminator
            )
        }

        val chunks = properties.chunked(C.DEFAULT_CHUNK_SIZE)
        val writerSuffix = writerClass.simpleName

        chunks.forEachIndexed { index, chunkProps ->
            val chunkFunName = C.TEMPLATE_CHUNK_FUN_NAME
                .format(C.STR_SERIALIZE_CHUNK_PREFIX, index, writerSuffix)

            emitChunkFunction(index, chunkProps, typeSpecBuilder, writerSuffix)
            code.addStatement(C.TEMPLATE_CHUNK_CALL_WRITER, chunkFunName)
        }

        code.addStatement(C.STR_WRITER_END_OBJ)
    }

    /**
     * Generates a private chunk serialization method.
     */
    private fun emitChunkFunction(
        index: Int,
        chunkProps: List<GhostPropertyModel>,
        typeSpecBuilder: TypeSpec.Builder,
        writerSuffix: String
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
            emitProperty(chunkCode, prop)
        }

        chunkFun.addCode(chunkCode.build())
        typeSpecBuilder.addFunction(chunkFun.build())
    }
}
