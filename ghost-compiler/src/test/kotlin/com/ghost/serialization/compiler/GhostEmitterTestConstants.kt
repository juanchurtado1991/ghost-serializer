package com.ghost.serialization.compiler

/**
 * Literals used only by compiler unit / KSP fixture tests and generated-code hygiene.
 * Kept out of production [com.ghost.serialization.compiler.internal.GhostEmitterConstants].
 */
internal object GhostEmitterTestConstants {
    const val STR_OVERRIDE_DESERIALIZE_STRING_READER =
        "override fun deserialize(reader: GhostJsonStringReader)"
    const val STR_OVERRIDE_SERIALIZE_FN = "override fun serialize"
    const val STR_KT_SERIALIZER_FILE_SUFFIX = "Serializer.kt"

    const val STR_TEST_CUSTOM_DECODER_FN = "decodeHex"
    const val STR_TEST_DECODER_UTILS = "DecoderUtils"
    const val STR_TEST_CUSTOM_FIELD_MODEL = "CustomFieldModel"
    const val STR_TEST_BYTES_RESULT = "bytes"
    const val STR_TEST_NATIVE_RESULT = "native"
}
