package com.ghost.serialization.compiler.codegen
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.CodeBlock


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
            return CodeBlock.of("%L()", name)
        }
        if (args.size == 1 && name.length + args[0].length <= C.COMPACT_CALL_MAX_INLINE_LENGTH) {
            return CodeBlock.of("%L(%L)", name, args[0])
        }
        return CodeBlock.builder()
            .add("%L(\n", name)
            .indent()
            .apply {
                args.forEach { arg ->
                    add("%L,\n", arg)
                }
            }
            .unindent()
            .add(")")
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
            .add("%T.of(\n", optionsClass)
            .indent()
            .add("%L,\n", shift)
            .add("%L,\n", multiplier)
            .add("%L,\n", tableSize)
            .add("%L,\n", textChannel)
            .apply {
                if (extendedKeyHash) {
                    add("%L,\n", true)
                }
                names.forEach { name ->
                    add("%S,\n", name)
                }
            }
            .unindent()
            .add(")")
            .build()
    }
}
