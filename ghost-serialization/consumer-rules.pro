# Ghost Serialization consumer ProGuard/R8 rules (merged from the ghost-serialization AAR).

-keep @interface com.ghost.serialization.annotations.GhostSerialization
-keep @interface com.ghost.serialization.annotations.GhostProtoSerialization
-keep @interface com.ghost.serialization.annotations.GhostYamlSerialization

-keep @com.ghost.serialization.annotations.GhostSerialization class * {
    <fields>;
    <init>(...);
}
-keep @com.ghost.serialization.annotations.GhostProtoSerialization class * {
    <fields>;
    <init>(...);
}
-keep @com.ghost.serialization.annotations.GhostYamlSerialization class * {
    <fields>;
    <init>(...);
}

-keep class com.ghost.serialization.generated.** { *; }

-keep class * implements com.ghost.serialization.contract.GhostSerializer {
    public static ** INSTANCE;
    public <init>(...);
    *;
}
-keep class * implements com.ghost.serialization.yaml.contract.GhostYamlSerializer {
    public static ** INSTANCE;
    public <init>(...);
    *;
}

-keep class * implements com.ghost.serialization.contract.GhostRegistry {
    public <init>(...);
    *;
}

-keepnames class * implements com.ghost.serialization.contract.GhostRegistry
-keepclassmembers class * implements com.ghost.serialization.contract.GhostRegistry {
    public <init>();
}
