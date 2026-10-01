package com.ghost.serialization.compiler.internal

/** KSP processor concerns: module options, registry/ProGuard/ServiceLoader output, YAML eligibility diagnostics,
 * log messages. */
internal object GhostProcessorConstants {

    const val STR_REGISTRY_PREFIX = "GhostModuleRegistry"
    const val STR_LOG_PREFIX = ">>> [GhostSerialization]"
    const val OPTION_MODULE_NAME = "ghost.moduleName"
    /** Explicit test compilation flag (`"true"` / `"false"`); see
     * [com.ghost.serialization.compiler.ksp.TestSourceSetDetection]. */
    const val OPTION_IS_TEST = "ghost.isTest"

    const val STR_DEFAULT_NAME = "Default"
    const val STR_DASH = "-"
    const val STR_LOG_OPTIMIZED = " Successfully optimized: "
    const val STR_LOG_CRITICAL = " Critical error processing "
    const val STR_COLON_SPACE = ": "
    const val STR_KCLASS = "KClass"
    const val STR_TYPE_T = "T"
    const val STR_MAP = "Map"
    const val STR_FORMAT_S = "%S"
    const val STR_MAP_OF = "mapOf(\n"
    const val STR_MAP_ENTRY = "    %T::class to %T"
    const val STR_COMMA_NEWLINE = ",\n"
    const val STR_PAREN_CLOSE = ")"
    const val STR_PROP_SERIALIZERS_MAP = "serializersByClass"
    const val STR_FUN_GET_SERIALIZER = "getSerializer"
    const val STR_PARAM_CLAZZ = "clazz"
    const val STR_UNCHECKED_CAST = "UNCHECKED_CAST"
    const val STR_GHOST_REGISTRY = "GhostRegistry"
    const val STR_FUN_PREWARM = "prewarm"
    const val STR_SERIALIZERS_SIZE = "serializersByClass.size"
    const val STR_FUN_REG_COUNT = "registeredCount"
    const val STR_FUN_GET_ALL_SERIALIZERS = "getAllSerializers"
    const val STR_RETURN_SERIALIZERS = "return serializersByClass"
    const val STR_KDOC_REGISTRY =
        "Generated Registry for GhostSerialization.\nUses a when-based getSerializer for cold-start-friendly class loading and a lazy map for getAllSerializers."

    const val STR_INSTANCE = "INSTANCE"
    const val STR_INIT_INSTANCE = "%T()"
    const val STR_META_INF_PROGUARD = "META-INF.proguard"
    const val STR_GHOST_SERIALIZATION_FILE = "ghost-serialization"
    const val STR_EXT_PRO = "pro"
    const val STR_LOG_PROGUARD_WARN = " Could not generate ProGuard rules: "
    const val STR_SERVICE_REGISTRY = "com.ghost.serialization.contract.GhostRegistry"
    const val STR_META_INF_SERVICES = "META-INF.services"
    const val STR_LOG_SERVICE_WARN = " Could not generate ServiceLoader file: "
    const val STR_LAZY_START = "lazy {\n"
    const val STR_WHEN_CLAZZ_START = "return when (clazz) {\n"
    const val STR_WHEN_ENTRY = "    %T::class -> %T\n"
    const val STR_WHEN_ELSE_NULL = "    else -> null\n"
    const val STR_WHEN_CLOSE_CAST = "} as %T\n"
    const val STR_RETURN_L = "return %L"
    const val REGISTRY_CHUNK_SIZE = 500
    const val TEMPLATE_GET_SHARD_MAP_CALL = "getShardMap%L()"
    const val STR_PLUS_SPACED = " + "
    const val STR_NEWLINE_CLOSE_CURLY = "\n}"
    const val TEMPLATE_GET_SHARD_CALL = "getShard%L(clazz)?.let { return it as %T }"
    const val STR_RETURN_NULL = "return null"
    const val TEMPLATE_SHARD_MAP_NAME = "getShardMap%d"
    const val TEMPLATE_SHARD_NAME = "getShard%d"
    const val TEMPLATE_PROGUARD_KEEP = $$"""
        # Ghost KSP module rules (registry + serializers for this compilation unit)
        -keep class %1$s.** { *; }
        -keep class %1$s.%2$s {
            public static ** INSTANCE;
            public *** getSerializer(...);
        }
        -keep class * implements com.ghost.serialization.contract.GhostSerializer {
            *;
        }
        -keep class * implements com.ghost.serialization.yaml.contract.GhostYamlSerializer {
            *;
        }
    """

    const val STR_ERR_YAML_ORPHAN =
        "GhostYamlSerialization: @GhostYamlSerialization requires @GhostSerialization or @GhostProtoSerialization on the same class."

    const val STR_ERR_YAML_RESILIENT =
        "GhostYamlSerialization: @GhostResilient is JSON-only and cannot be combined with @GhostYamlSerialization."

    const val STR_ERR_YAML_SEALED =
        "GhostYamlSerialization: sealed classes are JSON-only and cannot use @GhostYamlSerialization."

    const val STR_ERR_YAML_ENVELOPE =
        "GhostYamlSerialization: @GhostJsonEnvelope is JSON-only and cannot use @GhostYamlSerialization."

    const val STR_ERR_YAML_INFERRED =
        "GhostYamlSerialization: inferred polymorphism is JSON-only and cannot use @GhostYamlSerialization."

    const val STR_ERR_YAML_CUSTOM_CODEC =
        "GhostYamlSerialization: @GhostDecoder/@GhostEncoder are JSON-only and cannot be used on YAML-enabled models."

    const val STR_ERR_YAML_STRUCTURAL =
        "GhostYamlSerialization: @GhostFlatten/@GhostWrap/@GhostWrappedKeys are JSON-only and cannot be used on YAML-enabled models."

    const val STR_ERR_YAML_NESTED_GHOST =
        "GhostYamlSerialization: nested @GhostSerialization types are not supported on YAML paths."

    const val STR_ERR_YAML_RAW_JSON =
        "GhostYamlSerialization: RawJson is JSON-only and cannot be used on YAML-enabled models."

    const val STR_ERR_YAML_BYTE_ARRAY =
        "GhostYamlSerialization: non-proto ByteArray is JSON-only; use @GhostProtoSerialization for Base64 YAML fields."

    const val STR_YAML_SERIALIZER_FQN = "com.ghost.serialization.yaml.contract.GhostYamlSerializer"

    const val OPTION_TEXT_CHANNEL = "ghost.textChannel"
    const val ARG_INFERRED = "inferred"
    const val STR_SRC_TEST = "/src/test/"
    const val STR_SRC_ANDROID_TEST = "/src/androidTest/"
    const val STR_SRC_TEST_KSP = "/src/testKsp/"
    const val STR_TEST_SUFFIX = "_Test"
}
