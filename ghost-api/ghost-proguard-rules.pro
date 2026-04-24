# Preserve classes annotated with @GhostSerialization.
# Ghost uses compile-time generated code (no reflection), but preserving these
# prevents R8/ProGuard from aggressively stripping domain models that are only
# instantiated dynamically via JSON deserialization or accessed dynamically.

-keep @com.ghost.serialization.annotations.GhostSerialization class * { *; }

# Preserve the core annotation itself just in case it is queried at runtime (rare)
-keep class com.ghost.serialization.annotations.GhostSerialization { *; }

# Ghost's generated serializers must be preserved if they are accessed dynamically
-keep class * implements com.ghost.serialization.core.contract.GhostSerializer { *; }
