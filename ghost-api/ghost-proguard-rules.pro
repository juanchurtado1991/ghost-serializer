# Ghost consumer ProGuard/R8 rules (merged automatically from the ghost-api AAR).
# Ghost uses compile-time generated code (no reflection), but preserving annotated
# DTOs prevents R8 from stripping models only referenced via deserialization.

-keep @com.ghost.serialization.annotations.GhostSerialization class * { *; }
-keep @com.ghost.serialization.annotations.GhostProtoSerialization class * { *; }
-keep @com.ghost.serialization.annotations.GhostYamlSerialization class * { *; }

-keep class com.ghost.serialization.annotations.GhostSerialization { *; }
-keep class com.ghost.serialization.annotations.GhostProtoSerialization { *; }
-keep class com.ghost.serialization.annotations.GhostYamlSerialization { *; }

-keep class com.ghost.serialization.generated.** { *; }

-keep class * implements com.ghost.serialization.contract.GhostSerializer { *; }
-keep class * implements com.ghost.serialization.yaml.contract.GhostYamlSerializer { *; }
