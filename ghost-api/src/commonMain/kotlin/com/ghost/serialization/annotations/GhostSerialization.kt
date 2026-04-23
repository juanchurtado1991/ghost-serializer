package com.ghost.serialization.annotations

/**
 * Triggers the automatic generation of high-performance serializers for the annotated class.
 * 
 * When applied, the Ghost KSP compiler plugin generates a specialized Serializer 
 * implementation that avoids reflection and is optimized for peak throughput in 
 * Kotlin Multiplatform environments.
 * 
 * @param name Optional custom name for the model to avoid collisions.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class GhostSerialization(val name: String = "")