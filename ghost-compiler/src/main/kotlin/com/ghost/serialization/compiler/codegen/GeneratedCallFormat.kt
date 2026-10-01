package com.ghost.serialization.compiler.codegen

import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock
import com.ghost.serialization.compiler.internal.GhostCodegenConstants as CG

/**
 * Shared multiline formatting for generated call sites that would otherwise become
 * single ultra-long lines (OPTIONS tables, createInstance, etc.).
 */
internal object GeneratedCallFormat {

    /**
     * `name(arg0, arg1, …)` with one argument per line and a trailing comma.
     * Short single-arg calls stay on one line when clearly compact.
     */
    fun invoke(name: String, args: List<String>): CodeBlock {
        if (args.isEmpty()) {
            return CodeBlock.of(CG.TEMPLATE_INVOKE_EMPTY, name)
        }
        if (args.size == 1 && name.length + args[0].length <= CG.COMPACT_CALL_MAX_INLINE_LENGTH) {
            return CodeBlock.of(CG.TEMPLATE_INVOKE_ONE, name, args[0])
        }
        return CodeBlock.builder()
            .add(CG.TEMPLATE_INVOKE_OPEN, name)
            .indent()
            .apply {
                args.forEach { arg ->
                    add(CG.TEMPLATE_ARG_LINE, arg)
                }
            }
            .unindent()
            .add(CG.STR_CLOSE_PAREN_FLOW)
            .build()
    }

    fun jsonReaderOptionsOf(
        optionsClass: ClassName,
        shift: Int,
        multiplier: Int,
        tableSize: Int,
        textChannel: Boolean,
        extendedKeyHash: Boolean,
        names: List<String>,
    ): CodeBlock {
        return CodeBlock.builder()
            .add(CG.TEMPLATE_TYPED_OF_OPEN, optionsClass)
            .indent()
            .add(CG.TEMPLATE_ARG_LINE, shift)
            .add(CG.TEMPLATE_ARG_LINE, multiplier)
            .add(CG.TEMPLATE_ARG_LINE, tableSize)
            .add(CG.TEMPLATE_ARG_LINE, textChannel)
            .apply {
                if (extendedKeyHash) {
                    add(CG.TEMPLATE_ARG_LINE, true)
                }
                names.forEach { name ->
                    add(CG.TEMPLATE_STRING_ARG_LINE, name)
                }
            }
            .unindent()
            .add(CG.STR_CLOSE_PAREN_FLOW)
            .build()
    }
}
