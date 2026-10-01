package com.ghost.serialization.compiler.codegen.emit

import com.ghost.serialization.compiler.internal.GhostAnalyzerConstants as AC
import com.ghost.serialization.compiler.internal.GhostEmitterConstants as C

/**
 * Writer statement template per Kotlin scalar type, with an optional proto3 variant (int64 as a
 * quoted string, `ByteArray` as Base64). Supporting another scalar type is one more entry here
 * instead of another branch in [BaseSerializeEmitter]'s type dispatch.
 */
internal object ScalarWriteTemplates {

    private class Template(val plain: String, val proto: String = plain)

    private val templates = mapOf(
        AC.K_INT to Template(plain = C.TEMPLATE_WRITER_VALUE),
        AC.K_LONG to Template(plain = C.TEMPLATE_WRITER_VALUE, proto = C.STR_WRITER_VAL_LONG_AS_STRING),
        AC.K_ULONG to Template(plain = C.TEMPLATE_WRITER_VALUE, proto = C.STR_WRITER_VAL_LONG_AS_STRING),
        AC.K_STRING to Template(plain = C.TEMPLATE_WRITER_VALUE),
        AC.K_BOOLEAN to Template(plain = C.TEMPLATE_WRITER_VALUE),
        AC.K_DOUBLE to Template(plain = C.TEMPLATE_WRITER_VALUE),
        AC.K_FLOAT to Template(plain = C.TEMPLATE_WRITER_VALUE),
        AC.K_BYTE to Template(plain = C.STR_WRITER_VAL_TO_INT),
        AC.K_SHORT to Template(plain = C.STR_WRITER_VAL_TO_INT),
        AC.K_CHAR to Template(plain = C.TEMPLATE_WRITER_VALUE),
        AC.K_BYTE_ARRAY to Template(plain = C.STR_WRITER_RAW_VALUE_L, proto = C.STR_WRITER_VAL_BYTES_AS_BASE64),
    )

    /** The statement template for [qualifiedName], or `null` when it isn't a table-driven scalar. */
    fun templateFor(qualifiedName: String?, isProto: Boolean): String? {
        val template = templates[qualifiedName] ?: return null
        return if (isProto) template.proto else template.plain
    }
}
