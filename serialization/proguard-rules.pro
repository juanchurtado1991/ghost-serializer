# Ghost Serialization R8/ProGuard Rules

# 1. Preserve the ServiceLoader entries for Registry Discovery
-keepnames class com.ghost.serialization.core.contract.GhostRegistry
-keepclassmembers class * implements com.ghost.serialization.core.contract.GhostRegistry {
    public <init>();
}
-keep class com.ghost.serialization.generated.** { *; }

# 2. Preserve hardcoded fallback classes if ServiceLoader fails
-keep class com.ghost.serialization.generated.GhostModuleRegistry_** { *; }

# 3. Preserve Ghost Serializer implementations (avoid purging via KSP)
-keep class * implements com.ghost.serialization.core.contract.GhostSerializer {
    public <init>();
    public static ** INSTANCE;
}

# 4. Handle Enum discovery
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
