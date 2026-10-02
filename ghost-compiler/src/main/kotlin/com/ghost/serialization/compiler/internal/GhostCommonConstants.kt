package com.ghost.serialization.compiler.internal

/** Vocabulary shared by three or more compiler stages (analysis, KSP processor, codegen, emitters): common
 * literals, core package/class/annotation names. */
internal object GhostCommonConstants {

    const val STR_NULL = "null"
    const val VAL_ONE = 1
    const val STR_FALSE = "false"
    const val STR_UNDERSCORE = "_"
    const val STR_SERIALIZERS_PKG = "com.ghost.serialization.serializers"
    const val STR_EMPTY = ""
    const val STR_GHOST_SERIALIZER = "GhostSerializer"
    const val STR_SERIALIZER_SUFFIX = "Serializer"
    const val STR_ANNOTATION_SERIALIZATION =
        "com.ghost.serialization.annotations.GhostSerialization"

    const val STR_ANNOTATION_PROTO_SERIALIZATION =
        "com.ghost.serialization.annotations.GhostProtoSerialization"

    const val STR_ANNOTATION_YAML_SERIALIZATION =
        "com.ghost.serialization.annotations.GhostYamlSerialization"

    const val STR_GENERATED_PKG = "com.ghost.serialization.generated"
    const val STR_REFLECT_PKG = "kotlin.reflect"
    const val STR_COLLECTIONS_PKG = "kotlin.collections"
    const val PKG_PARSER_COMMON = "com.ghost.serialization.parser.common"
    const val PKG_PARSER_COMMON_CONSTANTS = "com.ghost.serialization.parser.common.constants"
    const val PKG_PARSER_COMMON_JSON = "com.ghost.serialization.parser.common.json"
    const val PKG_PARSER_STRINGS = "com.ghost.serialization.parser.strings"
    const val PKG_PARSER_BYTES = "com.ghost.serialization.parser.bytes"
    const val PKG_PARSER_BYTES_EXTENSIONS = "com.ghost.serialization.parser.bytes.extensions"
    const val PKG_PARSER_STREAMING = "com.ghost.serialization.parser.streaming"
    const val STR_DOT = "."
    const val PKG_TYPES = "com.ghost.serialization.types"
    const val PKG_WRITER_BYTES = "com.ghost.serialization.writer.bytes"

    const val PKG_WRITER_STRINGS = "com.ghost.serialization.writer.strings"
    const val PKG_CONTRACT = "com.ghost.serialization.contract"
    const val PKG_EXCEPTION = "com.ghost.serialization.exception"
    const val PKG_GHOST = "com.ghost.serialization"
    const val PKG_YAML_CONTRACT = "com.ghost.serialization.yaml.contract"
    const val PKG_YAML_WRITER = "com.ghost.serialization.writer.yaml"
    const val PKG_YAML_PARSER = "com.ghost.serialization.parser.yaml"
    const val PKG_YAML_SERIALIZER = "com.ghost.serialization.yaml.serializer"
    const val ANNOTATION_GHOST_SERIALIZATION = "GhostSerialization"
    const val ANNOTATION_GHOST_PROTO_SERIALIZATION = "GhostProtoSerialization"
    const val ANNOTATION_GHOST_YAML_SERIALIZATION = "GhostYamlSerialization"
    const val PKG_KOTLIN = "kotlin"
    const val STR_TRUE = "true"
}
