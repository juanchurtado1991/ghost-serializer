# ====================================================================
# Ghost Serialization - ProGuard/R8 Rules
# ====================================================================

# 1. Keep KSP generated registry.
-keep class com.ghost.serialization.generated.GhostModuleRegistry {
    *;
}

# 2. keep genereated serializers
-keep class * implements com.ghost.serialization.core.GhostSerializer {
    *;
}

# 3. Keep library annotation
-keep @interface com.ghost.serialization.annotations.** {
    *;
}
