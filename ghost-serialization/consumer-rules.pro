# Ghost Serialization Consumer ProGuard Rules
# Absolute Performance, Absolute Integrity

# Keep the GhostSerialization annotation itself
-keep @interface com.ghostserializer.annotations.GhostSerialization

# Keep classes annotated with @GhostSerialization and their properties
# This is important for discovery and if any reflection-based debugging is used
-keep @com.ghostserializer.annotations.GhostSerialization class * {
    <fields>;
    <init>(...);
}

# Keep the generated Serializers
-keep class * implements com.ghostserializer.core.contract.GhostSerializer {
    public static ** INSTANCE;
    public <init>(...);
    *;
}

# Keep the generated Registries for discovery
-keep class * implements com.ghostserializer.core.contract.GhostRegistry {
    public <init>(...);
    *;
}

# Preserve ServiceLoader metadata for discovery
-keepnames class * implements com.ghostserializer.core.contract.GhostRegistry
-keepclassmembers class * implements com.ghostserializer.core.contract.GhostRegistry {
    public <init>();
}
