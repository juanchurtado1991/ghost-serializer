# ====================================================================
# Ghost Serialization - ProGuard/R8 Safety Rules
# ====================================================================

# 1. Keep KSP generated registries (deterministic names based on package)
-keep class com.ghost.serialization.generated.GhostModuleRegistry** {
    *;
}

# 2. Keep core contracts and generated serializers
-keep class * implements com.ghost.serialization.core.contract.GhostSerializer {
    *;
}

# 3. Keep library annotations to prevent stripping @GhostSerialization usage
-keep @interface com.ghost.serialization.core.annotations.** {
    *;
}

# 4. Keep the Registry interface as it is accessed via reflection in sub-registries
-keep interface com.ghost.serialization.core.contract.GhostRegistry {
    *;
}
